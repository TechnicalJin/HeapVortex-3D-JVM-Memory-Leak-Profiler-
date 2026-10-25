package com.heapvortex.controller;

import com.heapvortex.dto.GcRootPathDto;
import com.heapvortex.dto.LeakSuspectReportDto;
import com.heapvortex.exception.GlobalExceptionHandler;
import com.heapvortex.heap.LeakSuspectReportService;
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

class LeakSuspectReportControllerTest {

    private static final String URL = "/api/heapdumps/compare/leak-suspects";

    private LeakSuspectReportService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LeakSuspectReportService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LeakSuspectReportController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsTheSuspectsWithTheirPaths() throws Exception {
        GcRootPathDto path = new GcRootPathDto("0x1", true, "STICKY_CLASS",
                List.of(new GcRootPathDto.PathNode("0x2", "class LeakyApp", null)));
        when(service.generate("a", "b", 10, 3, 100)).thenReturn(new LeakSuspectReportDto("a", "b",
                List.of(new LeakSuspectReportDto.ClassSuspectDto("byte[]", 1, 5, 4, 10, 90, 80, "both increased",
                        List.of(new LeakSuspectReportDto.SampleObjectDto("0x1", 51200, 0, path))))));

        mockMvc.perform(get(URL).param("baseline", "a").param("comparison", "b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspects[0].className").value("byte[]"))
                .andExpect(jsonPath("$.suspects[0].sampleObjects[0].shallowBytes").value(51200))
                .andExpect(jsonPath("$.suspects[0].sampleObjects[0].gcRootPath.pathFound").value(true));
    }

    @Test
    void usesTheDefaultsAndPassesParametersOn() throws Exception {
        mockMvc.perform(get(URL).param("baseline", "a").param("comparison", "b")
                .param("limit", "4").param("objectsPerClass", "2").param("maxDepth", "50"));

        verify(service).generate("a", "b", 4, 2, 50);
    }

    @Test
    void rejectsParametersOutOfRange() throws Exception {
        String ok = "a";
        mockMvc.perform(get(URL).param("baseline", ok).param("comparison", "b").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("baseline", ok).param("comparison", "b").param("objectsPerClass", "11"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("baseline", ok).param("comparison", "b").param("maxDepth", "1001"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTheSameDumpTwice() throws Exception {
        mockMvc.perform(get(URL).param("baseline", "a").param("comparison", "a"))
                .andExpect(status().isBadRequest());
    }
}