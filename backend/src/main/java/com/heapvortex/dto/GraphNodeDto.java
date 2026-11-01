package com.heapvortex.dto;

public record GraphNodeDto(
        String id,
        String label,
        String kind,
        long shallowBytes,
        long retainedBytes,
        double retainedPercent,
        int depth) {}