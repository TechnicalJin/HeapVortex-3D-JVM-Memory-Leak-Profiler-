package com.heapvortex.dto;

import java.time.Instant;
import java.util.List;

public record JvmMetricsDto(
        String pid,
        Instant timestamp,
        MemoryMetricsDto memory,
        ThreadMetricsDto threads,
        List<GcMetricsDto> gc) {}