package com.heapvortex.dto;

public record WatchStatusDto(
        String pid,
        String state,          // LEARNING, STABLE, SUSPECTED or ERROR
        int floorSamples,
        double growthPercent,
        long heapUsed,
        String dumpId,         // set when an automatic heap dump was taken
        String message) {}