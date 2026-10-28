package com.heapvortex.dto;

import java.util.List;

public record SubgraphDto(
        String rootId,
        List<GraphNodeDto> nodes,
        List<GraphEdgeDto> edges,
        boolean truncated) {}