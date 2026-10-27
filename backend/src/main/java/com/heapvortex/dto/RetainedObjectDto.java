package com.heapvortex.dto;

public record RetainedObjectDto(
        String objectId,
        String kind,
        String className,
        long shallowBytes,
        long retainedBytes,
        double retainedPercent) {}