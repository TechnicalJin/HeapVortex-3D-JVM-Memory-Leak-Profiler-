package com.heapvortex.controller;

import com.heapvortex.dto.JvmInfoDto;
import com.heapvortex.exception.GlobalExceptionHandler;
import com.heapvortex.exception.JvmConnectionException;
import com.heapvortex.service.JvmDiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JvmControllerTest {

    private JvmDiscoveryService discoveryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        discoveryService = mock(JvmDiscoveryService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new JvmController(discoveryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
}