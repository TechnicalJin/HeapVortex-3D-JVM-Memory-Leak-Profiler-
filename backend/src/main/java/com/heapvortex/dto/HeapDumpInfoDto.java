package com.heapvortex.dto;

import java.time.Instant;

public record HeapDumpInfoDto(
        String id,
        String pid,
        String fileName,
        long sizeBytes,
        Instant createdAt) {}