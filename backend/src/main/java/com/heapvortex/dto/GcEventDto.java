package com.heapvortex.dto;

import java.time.Instant;

public record GcEventDto(
        String pid,
        String gcName,
        String gcAction,
        String gcCause,
        long durationMs,
        long heapBeforeBytes,
        long heapAfterBytes,
        long reclaimedBytes,
        Instant timestamp) {}