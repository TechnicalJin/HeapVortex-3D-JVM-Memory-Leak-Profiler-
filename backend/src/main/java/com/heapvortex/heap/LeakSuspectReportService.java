
package com.heapvortex.heap;

import com.heapvortex.dto.GcRootPathDto;
import com.heapvortex.dto.HeapDumpComparisonDto;
import com.heapvortex.dto.LeakSuspectReportDto;
import com.heapvortex.dto.ObjectSummaryDto;
import com.heapvortex.service.HeapGraphService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LeakSuspectReportService {

    private static final int MAX_CLASS_LIMIT = 500;
    private static final int MAX_OBJECTS_PER_CLASS = 10;
    private static final int MAX_PATH_DEPTH = 1000;

    private final HeapDumpComparisonService comparisonService;
    private final HeapGraphService graphService;

    public LeakSuspectReportService(
            HeapDumpComparisonService comparisonService,
            HeapGraphService graphService) {
        this.comparisonService = comparisonService;
        this.graphService = graphService;
    }

    public LeakSuspectReportDto generate(
            String baselineDumpId,
            String comparisonDumpId,
            int limit,
            int objectsPerClass,
            int maxDepth) {

        if (limit < 1 || limit > MAX_CLASS_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and " + MAX_CLASS_LIMIT);
        }

        if (objectsPerClass < 1
                || objectsPerClass > MAX_OBJECTS_PER_CLASS) {
            throw new IllegalArgumentException(
                    "objectsPerClass must be between 1 and "
                            + MAX_OBJECTS_PER_CLASS);
        }

        if (maxDepth < 1 || maxDepth > MAX_PATH_DEPTH) {
            throw new IllegalArgumentException(
                    "maxDepth must be between 1 and " + MAX_PATH_DEPTH);
        }

        if (baselineDumpId.equals(comparisonDumpId)) {
            throw new IllegalArgumentException(
                    "baseline and comparison must be different dump IDs");
        }

        // Get the broadest comparison supported by the comparison service.
        HeapDumpComparisonDto comparison = comparisonService.compare(
                baselineDumpId,
                comparisonDumpId,
                MAX_CLASS_LIMIT);

        List<LeakSuspectReportDto.ClassSuspectDto> suspects =
                new ArrayList<>();

        for (HeapDumpComparisonDto.ClassGrowthDto growth
                : comparison.classGrowth()) {

            // Report positive growth only; decreases are not leak suspects.
            if (growth.instanceCountGrowth() <= 0
                    && growth.dataBytesGrowth() <= 0) {
                continue;
            }

            List<ObjectSummaryDto> objects = graphService.instancesOf(
                    comparisonDumpId,
                    growth.className(),
                    objectsPerClass);

            List<LeakSuspectReportDto.SampleObjectDto> samples =
                    new ArrayList<>();

            for (ObjectSummaryDto object : objects) {
                GcRootPathDto path = graphService.pathToGcRoot(
                        comparisonDumpId,
                        object.objectId(),
                        maxDepth);

                samples.add(
                        new LeakSuspectReportDto.SampleObjectDto(
                                object.objectId(),
                                object.shallowBytes(),
                                object.referenceCount(),
                                path));
            }

            String evidence = evidenceFor(growth);

            suspects.add(
                    new LeakSuspectReportDto.ClassSuspectDto(
                            growth.className(),
                            growth.baselineInstanceCount(),
                            growth.comparisonInstanceCount(),
                            growth.instanceCountGrowth(),
                            growth.baselineDataBytes(),
                            growth.comparisonDataBytes(),
                            growth.dataBytesGrowth(),
                            evidence,
                            List.copyOf(samples)));

            if (suspects.size() >= limit) {
                break;
            }
        }

        return new LeakSuspectReportDto(
                baselineDumpId,
                comparisonDumpId,
                List.copyOf(suspects));
    }

    private String evidenceFor(
            HeapDumpComparisonDto.ClassGrowthDto growth) {

        if (growth.instanceCountGrowth() > 0
                && growth.dataBytesGrowth() > 0) {
            return "Instance count and reported data bytes both increased";
        }

        if (growth.instanceCountGrowth() > 0) {
            return "Instance count increased; reported data bytes did not increase";
        }

        return "Reported data bytes increased despite no positive instance-count growth";
    }
}
