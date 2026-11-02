package com.heapvortex.dto;

public record ThreadMetricsDto(
        int threadCount,
        int peakThreadCount,
        int daemonThreadCount,
        long totalStartedThreadCount) {}