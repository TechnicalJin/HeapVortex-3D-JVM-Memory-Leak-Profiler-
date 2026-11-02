package com.heapvortex.dto;

import java.util.List;

public record HeapDumpComparisonDto(
        String baselineDumpId,
        String comparisonDumpId,
        List<ClassGrowthDto> classGrowth
) {
    public record ClassGrowthDto(
            String className,
            long baselineInstanceCount,
            long comparisonInstanceCount,
            long instanceCountGrowth,
            long baselineDataBytes,
            long comparisonDataBytes,
            long dataBytesGrowth
    ) {
    }
}
