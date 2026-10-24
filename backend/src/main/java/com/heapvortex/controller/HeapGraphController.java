package com.heapvortex.controller;

import com.heapvortex.dto.GraphStatsDto;
import com.heapvortex.dto.ObjectDetailsDto;
import com.heapvortex.dto.ObjectSummaryDto;
import com.heapvortex.exception.InvalidRequestException;
import com.heapvortex.service.HeapGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/heapdumps/{id}")
public class HeapGraphController {

    private static final int MAX_LIMIT = 500;

    private final HeapGraphService graphService;

    public HeapGraphController(HeapGraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/graph/stats")
    public GraphStatsDto stats(@PathVariable String id) {
        return graphService.stats(id);
    }

    @GetMapping("/instances")
    public List<ObjectSummaryDto> instances(@PathVariable String id,
                                            @RequestParam String className,
                                            @RequestParam(defaultValue = "20") int limit) {
        checkRange("limit", limit);
        return graphService.instancesOf(id, className, limit);
    }

    @GetMapping("/objects/{objectId}")
    public ObjectDetailsDto object(@PathVariable String id,
                                   @PathVariable String objectId,
                                   @RequestParam(defaultValue = "50") int maxReferences) {
        checkRange("maxReferences", maxReferences);
        return graphService.object(id, objectId, maxReferences);
    }

    private static void checkRange(String name, int value) {
        if (value < 1 || value > MAX_LIMIT) {
            throw new InvalidRequestException(name + " must be between 1 and " + MAX_LIMIT);
        }
    }
}