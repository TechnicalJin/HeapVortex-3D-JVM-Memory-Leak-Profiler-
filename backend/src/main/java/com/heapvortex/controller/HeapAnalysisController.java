package com.heapvortex.controller;

import com.heapvortex.dto.ClassHistogramEntryDto;
import com.heapvortex.dto.HeapSummaryDto;
import com.heapvortex.exception.InvalidRequestException;
import com.heapvortex.service.HeapAnalysisService;
import com.heapvortex.service.HistogramSort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/heapdumps/{id}")
public class HeapAnalysisController {

    private static final int MAX_LIMIT = 500;

    private final HeapAnalysisService analysisService;

    public HeapAnalysisController(HeapAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/summary")
    public HeapSummaryDto summary(@PathVariable String id) {
        return analysisService.summary(id);
    }

    @GetMapping("/histogram")
    public List<ClassHistogramEntryDto> histogram(@PathVariable String id,
                                                  @RequestParam(defaultValue = "20") int limit,
                                                  @RequestParam(defaultValue = "bytes") String sort) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        return analysisService.histogram(id, limit, HistogramSort.from(sort));
    }
}