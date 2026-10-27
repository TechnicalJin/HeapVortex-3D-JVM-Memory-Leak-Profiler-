package com.heapvortex.controller;

import com.heapvortex.dto.DominatorSuspectDto;
import com.heapvortex.dto.RetainedObjectDto;
import com.heapvortex.exception.InvalidRequestException;
import com.heapvortex.heap.RetainedSizeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/heapdumps/{id}")
public class DominatorController {

    private static final int MAX_LIMIT = 500;

    private final RetainedSizeService retainedService;

    public DominatorController(RetainedSizeService retainedService) {
        this.retainedService = retainedService;
    }

    @GetMapping("/dominators/top")
    public List<RetainedObjectDto> top(@PathVariable String id,
                                       @RequestParam(defaultValue = "20") int limit,
                                       @RequestParam(defaultValue = "true") boolean topLevel) {
        check("limit", limit, MAX_LIMIT);
        return retainedService.topDominators(id, limit, topLevel);
    }

    @GetMapping("/objects/{objectId}/dominated")
    public List<RetainedObjectDto> dominated(@PathVariable String id,
                                             @PathVariable String objectId,
                                             @RequestParam(defaultValue = "20") int limit) {
        check("limit", limit, MAX_LIMIT);
        return retainedService.dominated(id, objectId, limit);
    }

    @GetMapping("/dominators/suspects")
    public List<DominatorSuspectDto> suspects(@PathVariable String id,
                                              @RequestParam(defaultValue = "3") int limit,
                                              @RequestParam(defaultValue = "100") int maxDepth) {
        check("limit", limit, 20);
        check("maxDepth", maxDepth, 1000);
        return retainedService.suspects(id, limit, maxDepth);
    }

    private static void check(String name, int value, int max) {
        if (value < 1 || value > max) {
            throw new InvalidRequestException(name + " must be between 1 and " + max);
        }
    }
}