package com.heapvortex.controller;

import com.heapvortex.dto.GcMetricsDto;
import com.heapvortex.dto.JvmInfoDto;
import com.heapvortex.dto.JvmMetricsDto;
import com.heapvortex.dto.MemoryMetricsDto;
import com.heapvortex.dto.ThreadMetricsDto;
import com.heapvortex.exception.GlobalExceptionHandler;
import com.heapvortex.exception.JvmConnectionException;
import com.heapvortex.service.JvmDiscoveryService;
import com.heapvortex.service.JvmTelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JvmControllerTest {

    private JvmDiscoveryService discoveryService;
    private JvmTelemetryService telemetryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        discoveryService = mock(JvmDiscoveryService.class);
        telemetryService = mock(JvmTelemetryService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new JvmController(discoveryService, telemetryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private JvmMetricsDto sampleMetrics(String pid) {
        return new JvmMetricsDto(
                pid,
                Instant.parse("2026-10-13T10:15:30Z"),
                new MemoryMetricsDto(100L, 200L, 400L, 50L, 60L),
                new ThreadMetricsDto(24, 31, 18, 120L),
                List.of(new GcMetricsDto("G1 Young Generation", 15L, 82L)));
    }

    @Test
    void listReturnsDiscoveredJvms() throws Exception {
        when(discoveryService.listJvms()).thenReturn(List.of(new JvmInfoDto("1234", "LeakyApp")));

        mockMvc.perform(get("/api/jvms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pid").value("1234"))
                .andExpect(jsonPath("$[0].displayName").value("LeakyApp"));
    }

    @Test
    void listReturnsEmptyArrayWhenNoJvms() throws Exception {
        when(discoveryService.listJvms()).thenReturn(List.of());

        mockMvc.perform(get("/api/jvms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void connectionFailureIsReturnedAs502() throws Exception {
        when(discoveryService.listJvms())
                .thenThrow(new JvmConnectionException("Attach failed", new RuntimeException()));

        mockMvc.perform(get("/api/jvms"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Attach failed"));
    }

    @Test
    void unexpectedErrorIsReturnedAs500WithGenericMessage() throws Exception {
        when(discoveryService.listJvms()).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/jvms"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
    }

    @Test
    void metricsReturnsSnapshotForKnownPid() throws Exception {
        when(discoveryService.exists("1234")).thenReturn(true);
        when(telemetryService.collect("1234")).thenReturn(sampleMetrics("1234"));

        mockMvc.perform(get("/api/jvms/1234/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pid").value("1234"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.memory.heapUsed").value(100))
                .andExpect(jsonPath("$.memory.heapCommitted").value(200))
                .andExpect(jsonPath("$.memory.heapMax").value(400))
                .andExpect(jsonPath("$.memory.nonHeapUsed").value(50))
                .andExpect(jsonPath("$.memory.nonHeapCommitted").value(60))
                .andExpect(jsonPath("$.threads.threadCount").value(24))
                .andExpect(jsonPath("$.threads.peakThreadCount").value(31))
                .andExpect(jsonPath("$.threads.daemonThreadCount").value(18))
                .andExpect(jsonPath("$.threads.totalStartedThreadCount").value(120))
                .andExpect(jsonPath("$.gc[0].name").value("G1 Young Generation"))
                .andExpect(jsonPath("$.gc[0].collectionCount").value(15))
                .andExpect(jsonPath("$.gc[0].collectionTimeMs").value(82));
    }

    @Test
    void metricsReturns404ForUnknownPid() throws Exception {
        when(discoveryService.exists("999")).thenReturn(false);

        mockMvc.perform(get("/api/jvms/999/metrics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("JVM not found: 999"));
    }

    @Test
    void metricsDoesNotContactTheJvmWhenThePidIsUnknown() throws Exception {
        when(discoveryService.exists("999")).thenReturn(false);

        mockMvc.perform(get("/api/jvms/999/metrics"));

        verify(telemetryService, never()).collect("999");
    }

    @Test
    void metricsReturns502WhenJmxConnectionFails() throws Exception {
        when(discoveryService.exists("1234")).thenReturn(true);
        when(telemetryService.collect("1234"))
                .thenThrow(new JvmConnectionException("Failed to connect", new RuntimeException()));

        mockMvc.perform(get("/api/jvms/1234/metrics"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Failed to connect"));
    }

    @Test
    void metricsReturns500ForUnexpectedErrors() throws Exception {
        when(discoveryService.exists("1234")).thenReturn(true);
        when(telemetryService.collect("1234")).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/jvms/1234/metrics"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
    }
}