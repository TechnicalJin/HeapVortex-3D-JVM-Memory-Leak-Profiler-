package com.heapvortex.controller;

import com.heapvortex.dto.HeapDumpInfoDto;
import com.heapvortex.exception.GlobalExceptionHandler;
import com.heapvortex.exception.HeapDumpException;
import com.heapvortex.exception.HeapDumpNotFoundException;
import com.heapvortex.exception.InvalidRequestException;
import com.heapvortex.heap.HeapDumpStorage;
import com.heapvortex.service.HeapDumpService;
import com.heapvortex.service.JvmDiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HeapDumpControllerTest {

    private static final HeapDumpInfoDto INFO = new HeapDumpInfoDto(
            "heap-1234-20261022-235347",
            "1234",
            "heap-1234-20261022-235347.hprof",
            1024L,
            Instant.parse("2026-10-22T18:23:47Z"));

    private JvmDiscoveryService discoveryService;
    private HeapDumpService heapDumpService;
    private HeapDumpStorage storage;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        discoveryService = mock(JvmDiscoveryService.class);
        heapDumpService = mock(HeapDumpService.class);
        storage = mock(HeapDumpStorage.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HeapDumpController(discoveryService, heapDumpService, storage))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(discoveryService.exists("1234")).thenReturn(true);
        when(heapDumpService.create("1234", true)).thenReturn(INFO);

        mockMvc.perform(post("/api/jvms/1234/heapdump"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/heapdumps/heap-1234-20261022-235347"))
                .andExpect(jsonPath("$.id").value("heap-1234-20261022-235347"))
                .andExpect(jsonPath("$.sizeBytes").value(1024));
    }

    @Test
    void createPassesTheLiveParameter() throws Exception {
        when(discoveryService.exists("1234")).thenReturn(true);
        when(heapDumpService.create("1234", false)).thenReturn(INFO);

        mockMvc.perform(post("/api/jvms/1234/heapdump").param("live", "false"))
                .andExpect(status().isCreated());

        verify(heapDumpService).create("1234", false);
    }

    @Test
    void createReturns404ForAnUnknownPidWithoutDumping() throws Exception {
        when(discoveryService.exists("999")).thenReturn(false);

        mockMvc.perform(post("/api/jvms/999/heapdump"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("JVM not found: 999"));

        verify(heapDumpService, never()).create("999", true);
    }

    @Test
    void createReturns502WhenTheDumpFails() throws Exception {
        when(discoveryService.exists("1234")).thenReturn(true);
        when(heapDumpService.create("1234", true))
                .thenThrow(new HeapDumpException("Heap dump failed for JVM 1234", new IOException("disk full")));

        mockMvc.perform(post("/api/jvms/1234/heapdump"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Heap dump failed for JVM 1234"));
    }

    @Test
    void listReturnsTheStoredDumps() throws Exception {
        when(storage.list()).thenReturn(List.of(INFO));

        mockMvc.perform(get("/api/heapdumps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pid").value("1234"))
                .andExpect(jsonPath("$[0].fileName").value("heap-1234-20261022-235347.hprof"));
    }

    @Test
    void getReturnsOneDump() throws Exception {
        when(storage.get("heap-1234-20261022-235347")).thenReturn(INFO);

        mockMvc.perform(get("/api/heapdumps/heap-1234-20261022-235347"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizeBytes").value(1024));
    }

    @Test
    void getReturns404ForAnUnknownDump() throws Exception {
        when(storage.get("nope")).thenThrow(new HeapDumpNotFoundException("nope"));

        mockMvc.perform(get("/api/heapdumps/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Heap dump not found: nope"));
    }

    @Test
    void getReturns400ForAnInvalidId() throws Exception {
        when(storage.get("bad")).thenThrow(new InvalidRequestException("Invalid heap dump id"));

        mockMvc.perform(get("/api/heapdumps/bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid heap dump id"));
    }

    @Test
    void deleteReturns204AndDeletesTheDump() throws Exception {
        mockMvc.perform(delete("/api/heapdumps/heap-1234-20261022-235347"))
                .andExpect(status().isNoContent());

        verify(storage).delete("heap-1234-20261022-235347");
    }
}