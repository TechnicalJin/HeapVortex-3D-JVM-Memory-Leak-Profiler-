package com.heapvortex.controller;

import com.heapvortex.dto.ClassHistogramEntryDto;
import com.heapvortex.dto.HeapSummaryDto;
import com.heapvortex.exception.GlobalExceptionHandler;
import com.heapvortex.exception.HeapDumpNotFoundException;
import com.heapvortex.exception.HprofFormatException;
import com.heapvortex.service.HeapAnalysisService;
import com.heapvortex.service.HistogramSort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HeapAnalysisControllerTest {

    private HeapAnalysisService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(HeapAnalysisService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HeapAnalysisController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void summaryReturnsTheSummary() throws Exception {
        when(service.summary("dump1")).thenReturn(new HeapSummaryDto(
                "JAVA PROFILE 1.0.2", 8, Instant.parse("2026-10-22T18:00:00Z"),
                10, 100, 5, 7, Map.of("STICKY_CLASS", 3L)));

        mockMvc.perform(get("/api/heapdumps/dump1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceCount").value(100))
                .andExpect(jsonPath("$.gcRootsByKind.STICKY_CLASS").value(3));
    }

    @Test
    void histogramUsesDefaultLimitAndSort() throws Exception {
        when(service.histogram("dump1", 20, HistogramSort.BYTES))
                .thenReturn(List.of(new ClassHistogramEntryDto("byte[]", 10, 1000)));

        mockMvc.perform(get("/api/heapdumps/dump1/histogram"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].className").value("byte[]"))
                .andExpect(jsonPath("$[0].instanceCount").value(10));
    }

    @Test
    void histogramPassesLimitAndSort() throws Exception {
        mockMvc.perform(get("/api/heapdumps/dump1/histogram").param("limit", "5").param("sort", "count"))
                .andExpect(status().isOk());

        verify(service).histogram("dump1", 5, HistogramSort.COUNT);
    }

    @Test
    void limitOutsideTheAllowedRangeIsRejected() throws Exception {
        mockMvc.perform(get("/api/heapdumps/dump1/histogram").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/heapdumps/dump1/histogram").param("limit", "501"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownSortIsRejected() throws Exception {
        mockMvc.perform(get("/api/heapdumps/dump1/histogram").param("sort", "size"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void anUnknownDumpIsA404() throws Exception {
        when(service.summary("nope")).thenThrow(new HeapDumpNotFoundException("nope"));

        mockMvc.perform(get("/api/heapdumps/nope/summary"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aBrokenDumpFileIsA422() throws Exception {
        when(service.summary("bad")).thenThrow(new HprofFormatException("Not an HPROF file"));

        mockMvc.perform(get("/api/heapdumps/bad/summary"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Not an HPROF file"));
    }
}