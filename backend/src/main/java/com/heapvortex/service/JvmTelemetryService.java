package com.heapvortex.service;

import com.heapvortex.dto.JvmMetricsDto;

public interface JvmTelemetryService {

    /** Connects to the JVM (if needed) and returns a snapshot of its metrics. */
    JvmMetricsDto collect(String pid);
}