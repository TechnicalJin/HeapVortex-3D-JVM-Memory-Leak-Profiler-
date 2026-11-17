package com.heapvortex.jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JmxCpuMetricsServiceTest {

    @Test
    void convertsTheLoadToPercent() {
        assertEquals(25.0, JmxCpuMetricsService.toPercent(0.25), 0.0001);
        assertEquals(0.0, JmxCpuMetricsService.toPercent(0.0), 0.0001);
    }

    @Test
    void capsTheLoadAtOneHundredPercent() {
        assertEquals(100.0, JmxCpuMetricsService.toPercent(1.7), 0.0001);
    }

    @Test
    void reportsMinusOneWhenTheLoadIsNotAvailable() {
        assertEquals(-1.0, JmxCpuMetricsService.toPercent(-1.0), 0.0001);
        assertEquals(-1.0, JmxCpuMetricsService.toPercent(Double.NaN), 0.0001);
    }
}