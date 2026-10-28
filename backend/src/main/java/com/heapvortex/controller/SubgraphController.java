package com.heapvortex.controller;

import com.heapvortex.dto.SubgraphDto;
import com.heapvortex.exception.InvalidRequestException;
import com.heapvortex.heap.SubgraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/heapdumps/{id}/subgraph")
public class SubgraphController {

    public static final int MAX_NODES = 2000;
    public static final int MAX_DEPTH = 6;

    private final SubgraphService subgraphService;

    public SubgraphController(SubgraphService subgraphService) {
        this.subgraphService = subgraphService;
    }

    @GetMapping("/overview")
    public SubgraphDto overview(@PathVariable String id,
                                @RequestParam(defaultValue = "10") int topN,
                                @RequestParam(defaultValue = "5") int childrenPerNode) {
        check("topN", topN, 50);
        check("childrenPerNode", childrenPerNode, 50);
        return subgraphService.overview(id, topN, childrenPerNode);
    }

    @GetMapping("/{objectId}")
    public SubgraphDto expand(@PathVariable String id,
                              @PathVariable String objectId,
                              @RequestParam(defaultValue = "2") int depth,
                              @RequestParam(defaultValue = "500") int maxNodes) {
        check("depth", depth, MAX_DEPTH);
        check("maxNodes", maxNodes, MAX_NODES);
        return subgraphService.expand(id, objectId, depth, maxNodes);
    }

    private static void check(String name, int value, int max) {
        if (value < 1 || value > max) {
            throw new InvalidRequestException(name + " must be between 1 and " + max);
        }
    }
}