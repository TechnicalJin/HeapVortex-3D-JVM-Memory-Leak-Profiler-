package com.heapvortex.dto;

import java.util.List;

public record DominatorSuspectDto(
        String topObjectId,
        String topClassName,
        long topRetainedBytes,
        double heapPercent,
        List<String> dominatorChain,
        String accumulationObjectId,
        String accumulationClassName,
        long accumulationRetainedBytes,
        String likelyLeakClass,
        List<ClassRetentionDto> dominatedClasses,
        GcRootPathDto pathToGcRoot) {}