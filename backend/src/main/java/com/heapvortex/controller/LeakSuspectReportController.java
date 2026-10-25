
package com.heapvortex.controller;

import com.heapvortex.dto.LeakSuspectReportDto;
import com.heapvortex.exception.InvalidRequestException;
import com.heapvortex.heap.LeakSuspectReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/heapdumps/compare")
public class LeakSuspectReportController {

    private static final int MAX_CLASS_LIMIT = 500;
    private static final int MAX_OBJECTS_PER_CLASS = 10;
    private static final int MAX_PATH_DEPTH = 1000;

    private final LeakSuspectReportService reportService;

    public LeakSuspectReportController(
            LeakSuspectReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/leak-suspects")
    public LeakSuspectReportDto leakSuspects(
            @RequestParam String baseline,
            @RequestParam String comparison,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "3") int objectsPerClass,
            @RequestParam(defaultValue = "100") int maxDepth) {

        if (limit < 1 || limit > MAX_CLASS_LIMIT) {
            throw new InvalidRequestException(
                    "limit must be between 1 and " + MAX_CLASS_LIMIT);
        }

        if (objectsPerClass < 1
                || objectsPerClass > MAX_OBJECTS_PER_CLASS) {
            throw new InvalidRequestException(
                    "objectsPerClass must be between 1 and "
                            + MAX_OBJECTS_PER_CLASS);
        }

        if (maxDepth < 1 || maxDepth > MAX_PATH_DEPTH) {
            throw new InvalidRequestException(
                    "maxDepth must be between 1 and " + MAX_PATH_DEPTH);
        }

        if (baseline.equals(comparison)) {
            throw new InvalidRequestException(
                    "baseline and comparison must be different dump IDs");
        }

        return reportService.generate(
                baseline, comparison, limit, objectsPerClass, maxDepth);
    }
}
