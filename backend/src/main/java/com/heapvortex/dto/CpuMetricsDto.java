package com.heapvortex.dto;

public record CpuMetricsDto(
        String pid,
        double processCpuPercent,   // -1 when the JVM cannot report it
        double systemCpuPercent,    // -1 when the JVM cannot report it
        int availableProcessors) {}