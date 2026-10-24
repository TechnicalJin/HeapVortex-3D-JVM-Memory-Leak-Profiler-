package com.heapvortex.controller;

import com.heapvortex.dto.GraphStatsDto;
import com.heapvortex.dto.ObjectDetailsDto;
import com.heapvortex.dto.ObjectSummaryDto;
import com.heapvortex.dto.ReferenceDto;
import com.heapvortex.exception.GlobalExceptionHandler;
import com.heapvortex.exception.HeapTooLargeException;
import com.heapvortex.exception.InvalidRequestException;
import com.heapvortex.exception.ObjectNotFoundException;
import com.heapvortex.service.HeapGraphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HeapGraphControllerTest {

    private HeapGraphService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(HeapGraphService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HeapGraphController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void statsReturnsTheCounts() throws Exception {
        when(service.stats("d1")).thenReturn(new GraphStatsDto(8, 5, 2, Map.of("INSTANCE", 3L)));

        mockMvc.perform(get("/api/heapdumps/d1/graph/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectCount").value(8))
                .andExpect(jsonPath("$.objectsByKind.INSTANCE").value(3));
    }

    @Test
    void instancesUseTheDefaultLimit() throws Exception {
        when(service.instancesOf("d1", "java.util.HashMap$Node", 20))
                .thenReturn(List.of(new ObjectSummaryDto("0x3e8", "java.util.HashMap$Node", 32, 3)));

        mockMvc.perform(get("/api/heapdumps/d1/instances").param("className", "java.util.HashMap$Node"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].objectId").value("0x3e8"))
                .andExpect(jsonPath("$[0].referenceCount").value(3));
    }

    @Test
    void instancesNeedAClassName() throws Exception {
        mockMvc.perform(get("/api/heapdumps/d1/instances"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void instancesRejectALimitOutOfRange() throws Exception {
        mockMvc.perform(get("/api/heapdumps/d1/instances").param("className", "X").param("limit", "501"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void objectDetailsAreReturned() throws Exception {
        when(service.object("d1", "0x3e8", 50)).thenReturn(new ObjectDetailsDto(
                "0x3e8", "INSTANCE", "Child", 20, 1,
                List.of(new ReferenceDto("next", "0x3e9", "Child", 20))));

        mockMvc.perform(get("/api/heapdumps/d1/objects/0x3e8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.className").value("Child"))
                .andExpect(jsonPath("$.references[0].field").value("next"))
                .andExpect(jsonPath("$.references[0].objectId").value("0x3e9"));
    }

    @Test
    void anUnknownObjectIsA404() throws Exception {
        when(service.object("d1", "0x1", 50)).thenThrow(new ObjectNotFoundException("0x1"));

        mockMvc.perform(get("/api/heapdumps/d1/objects/0x1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Object not found: 0x1"));
    }

    @Test
    void anInvalidObjectIdIsA400() throws Exception {
        when(service.object("d1", "zzz", 50)).thenThrow(new InvalidRequestException("Invalid object id: zzz"));

        mockMvc.perform(get("/api/heapdumps/d1/objects/zzz"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTooLargeDumpIsA413() throws Exception {
        when(service.stats("huge")).thenThrow(new HeapTooLargeException(2_000_000));

        mockMvc.perform(get("/api/heapdumps/huge/graph/stats"))
                .andExpect(status().is(413));
    }

    @Test
    void maxReferencesIsPassedOn() throws Exception {
        mockMvc.perform(get("/api/heapdumps/d1/objects/0x3e8").param("maxReferences", "7"));

        verify(service).object("d1", "0x3e8", 7);
    }
}