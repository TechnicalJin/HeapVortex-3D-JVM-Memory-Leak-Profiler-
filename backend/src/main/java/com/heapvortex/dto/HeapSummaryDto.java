package com.heapvortex.dto;

import java.time.Instant;
import java.util.Map;

public record HeapSummaryDto(
        String hprofVersion,
        int identifierSize,
        Instant dumpTimestamp,
        long classCount,
        long instanceCount,
        long objectArrayCount,
        long primitiveArrayCount,
        Map<String, Long> gcRootsByKind) {}