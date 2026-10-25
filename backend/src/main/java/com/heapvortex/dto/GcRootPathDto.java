package com.heapvortex.dto;

import java.util.List;

public record GcRootPathDto(
        String targetObjectId,
        boolean pathFound,
        String rootKind,
        List<PathNode> path
) {
    public record PathNode(
            String objectId,
            String typeName,
            String referenceFromPrevious
    ) {
    }
}