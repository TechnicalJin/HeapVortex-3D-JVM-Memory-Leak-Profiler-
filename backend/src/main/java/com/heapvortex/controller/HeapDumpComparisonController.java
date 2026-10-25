
package com.heapvortex.controller;

import com.heapvortex.dto.HeapDumpComparisonDto;
import com.heapvortex.exception.InvalidRequestException;
import com.heapvortex.heap.HeapDumpComparisonService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/heapdumps")
public class HeapDumpComparisonController {

    private static final int MAX_LIMIT = 500;

    private final HeapDumpComparisonService comparisonService;

    public HeapDumpComparisonController(
            HeapDumpComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @GetMapping("/compare")
    public HeapDumpComparisonDto compare(
            @RequestParam String baseline,
            @RequestParam String comparison,
            @RequestParam(defaultValue = "20") int limit) {

        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException(
                    "limit must be between 1 and " + MAX_LIMIT);
        }

        if (baseline.equals(comparison)) {
            throw new InvalidRequestException(
                    "baseline and comparison must be different dump IDs");
        }

        return comparisonService.compare(baseline, comparison, limit);
    }
}
