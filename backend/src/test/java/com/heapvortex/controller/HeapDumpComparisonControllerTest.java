package com.heapvortex.controller;

import com.heapvortex.dto.HeapDumpComparisonDto;
import com.heapvortex.exception.GlobalExceptionHandler;
import com.heapvortex.heap.HeapDumpComparisonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HeapDumpComparisonControllerTest {

    private HeapDumpComparisonService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(HeapDumpComparisonService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HeapDumpComparisonController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsTheGrowthPerClass() throws Exception {
        when(service.compare("a", "b", 20)).thenReturn(new HeapDumpComparisonDto("a", "b",
                List.of(new HeapDumpComparisonDto.ClassGrowthDto("byte[]", 10, 30, 20, 100, 900, 800))));

        mockMvc.perform(get("/api/heapdumps/compare").param("baseline", "a").param("comparison", "b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classGrowth[0].className").value("byte[]"))
                .andExpect(jsonPath("$.classGrowth[0].dataBytesGrowth").value(800));
    }

    @Test
    void passesTheLimitOn() throws Exception {
        mockMvc.perform(get("/api/heapdumps/compare")
                .param("baseline", "a").param("comparison", "b").param("limit", "5"));

        verify(service).compare("a", "b", 5);
    }

    @Test
    void rejectsTheSameDumpTwice() throws Exception {
        mockMvc.perform(get("/api/heapdumps/compare").param("baseline", "a").param("comparison", "a"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsALimitOutOfRange() throws Exception {
        mockMvc.perform(get("/api/heapdumps/compare")
                        .param("baseline", "a").param("comparison", "b").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/heapdumps/compare")
                        .param("baseline", "a").param("comparison", "b").param("limit", "501"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bothDumpsAreRequired() throws Exception {
        mockMvc.perform(get("/api/heapdumps/compare").param("baseline", "a"))
                .andExpect(status().isBadRequest());
    }
}