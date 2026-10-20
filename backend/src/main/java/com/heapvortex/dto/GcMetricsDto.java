package com.heapvortex.dto;

public record GcMetricsDto(String name, long collectionCount, long collectionTimeMs) {}