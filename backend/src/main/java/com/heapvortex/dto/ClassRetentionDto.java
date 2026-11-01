package com.heapvortex.dto;

public record ClassRetentionDto(
        String className,
        long instanceCount,
        long shallowBytes) {}