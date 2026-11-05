package com.heapvortex.service;

import com.heapvortex.dto.CpuMetricsDto;

public interface CpuMetricsService {

    CpuMetricsDto collect(String pid);
}