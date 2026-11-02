
package com.heapvortex.dto;

import java.util.List;

public record LeakSuspectReportDto(
        String baselineDumpId,
        String comparisonDumpId,
        List<ClassSuspectDto> suspects
) {
    public record ClassSuspectDto(
            String className,
            long baselineInstanceCount,
            long comparisonInstanceCount,
            long instanceCountGrowth,
            long baselineDataBytes,
            long comparisonDataBytes,
            long dataBytesGrowth,
            String evidence,
            List<SampleObjectDto> sampleObjects
    ) {}

    public record SampleObjectDto(
            String objectId,
            long shallowBytes,
            int referenceCount,
            GcRootPathDto gcRootPath
    ) {}
}
