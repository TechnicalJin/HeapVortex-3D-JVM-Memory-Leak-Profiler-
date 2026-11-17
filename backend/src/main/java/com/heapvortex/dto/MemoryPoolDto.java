package com.heapvortex.dto;
public record MemoryPoolDto(
        String name,
        String type,            // HEAP or NON_HEAP
        long used,
        long committed,
        long max,               // -1 when the pool has no limit
        long peakUsed,
        long usedAfterLastGc) {} // -1 when the pool is not collected