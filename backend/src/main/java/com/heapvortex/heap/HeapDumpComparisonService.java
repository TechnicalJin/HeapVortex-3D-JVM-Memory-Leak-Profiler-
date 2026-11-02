
package com.heapvortex.heap;

import com.heapvortex.dto.ClassHistogramEntryDto;
import com.heapvortex.dto.HeapDumpComparisonDto;
import com.heapvortex.service.HeapAnalysisService;
import com.heapvortex.service.HistogramSort;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HeapDumpComparisonService {

    private final HeapAnalysisService analysisService;

    public HeapDumpComparisonService(HeapAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    public HeapDumpComparisonDto compare(
            String baselineDumpId,
            String comparisonDumpId,
            int limit) {

        List<ClassHistogramEntryDto> baseline =
                analysisService.histogram(
                        baselineDumpId, Integer.MAX_VALUE, HistogramSort.BYTES);

        List<ClassHistogramEntryDto> comparison =
                analysisService.histogram(
                        comparisonDumpId, Integer.MAX_VALUE, HistogramSort.BYTES);

        Map<String, ClassHistogramEntryDto> baselineByClass =
                new HashMap<>();

        for (ClassHistogramEntryDto entry : baseline) {
            baselineByClass.put(entry.className(), entry);
        }

        Map<String, ClassHistogramEntryDto> comparisonByClass =
                new HashMap<>();

        for (ClassHistogramEntryDto entry : comparison) {
            comparisonByClass.put(entry.className(), entry);
        }

        java.util.Set<String> allClasses = new java.util.HashSet<>();
        allClasses.addAll(baselineByClass.keySet());
        allClasses.addAll(comparisonByClass.keySet());

        List<HeapDumpComparisonDto.ClassGrowthDto> growth =
                allClasses.stream()
                        .map(className -> {
                            ClassHistogramEntryDto before =
                                    baselineByClass.get(className);
                            ClassHistogramEntryDto after =
                                    comparisonByClass.get(className);

                            long beforeCount = before == null
                                    ? 0 : before.instanceCount();
                            long afterCount = after == null
                                    ? 0 : after.instanceCount();

                            long beforeBytes = before == null
                                    ? 0 : before.dataBytes();
                            long afterBytes = after == null
                                    ? 0 : after.dataBytes();

                            return new HeapDumpComparisonDto.ClassGrowthDto(
                                    className,
                                    beforeCount,
                                    afterCount,
                                    afterCount - beforeCount,
                                    beforeBytes,
                                    afterBytes,
                                    afterBytes - beforeBytes);
                        })
                        .filter(row ->
                                row.instanceCountGrowth() != 0
                                || row.dataBytesGrowth() != 0)
                        .sorted((a, b) -> {
                            int byBytes = Long.compare(
                                    b.dataBytesGrowth(), a.dataBytesGrowth());
                            if (byBytes != 0) {
                                return byBytes;
                            }
                            return Long.compare(
                                    b.instanceCountGrowth(),
                                    a.instanceCountGrowth());
                        })
                        .limit(limit)
                        .toList();

        return new HeapDumpComparisonDto(
                baselineDumpId, comparisonDumpId, growth);
    }
}
