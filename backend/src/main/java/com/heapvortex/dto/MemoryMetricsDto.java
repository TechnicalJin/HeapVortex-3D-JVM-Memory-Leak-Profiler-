package com.heapvortex.dto;

public record MemoryMetricsDto(
        long heapUsed,
        long heapCommitted,
        long heapMax,
        long nonHeapUsed,
        long nonHeapCommitted) {}