package com.heapvortex.heap;

import com.heapvortex.dto.GraphEdgeDto;
import com.heapvortex.dto.GraphNodeDto;
import com.heapvortex.dto.RetainedObjectDto;
import com.heapvortex.dto.SubgraphDto;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small pieces of the heap for the 3D view.
 * overview: the biggest top-level dominators and the objects they dominate (edges mean "dominates").
 * expand: breadth-first walk over real references from one object, capped by depth and node count.
 * Note: for a huge collection the walk fills the cap with its first elements. Raise maxNodes
 * or expand a single element to look deeper.
 */
@Service
public class SubgraphService {

    private final HeapLoader loader;
    private final RetainedSizeService retainedService;

    public SubgraphService(HeapLoader loader, RetainedSizeService retainedService) {
        this.loader = loader;
        this.retainedService = retainedService;
    }

    public SubgraphDto overview(String dumpId, int topN, int childrenPerNode) {
        List<GraphNodeDto> nodes = new ArrayList<>();
        List<GraphEdgeDto> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (RetainedObjectDto top : retainedService.topDominators(dumpId, topN, true)) {
            if (seen.add(top.objectId())) {
                nodes.add(fromRetained(top, 0));
            }
            for (RetainedObjectDto child : retainedService.dominated(dumpId, top.objectId(), childrenPerNode)) {
                if (seen.add(child.objectId())) {
                    nodes.add(fromRetained(child, 1));
                }
                edges.add(new GraphEdgeDto(top.objectId(), child.objectId(), "dominates"));
            }
        }
        return new SubgraphDto(null, nodes, edges, false);
    }

    public SubgraphDto expand(String dumpId, String objectId, int maxDepth, int maxNodes) {
        LoadedHeap heap = loader.load(dumpId);
        ObjectGraph graph = heap.graph();
        DominatorTree dom = heap.dominators();
        int start = HprofHeapGraphService.nodeOf(heap, objectId);

        Map<Integer, Integer> depthOf = new LinkedHashMap<>();
        Deque<Integer> queue = new ArrayDeque<>();
        depthOf.put(start, 0);
        queue.add(start);
        boolean truncated = false;

        bfs:
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            int depth = depthOf.get(current);
            if (depth >= maxDepth) {
                continue;
            }
            for (int edge = graph.edgeStart(current); edge < graph.edgeEnd(current); edge++) {
                int target = graph.edgeTarget(edge);
                if (depthOf.containsKey(target)) {
                    continue;
                }
                if (depthOf.size() >= maxNodes) {
                    truncated = true;
                    break bfs;
                }
                depthOf.put(target, depth + 1);
                queue.addLast(target);
            }
        }

        List<GraphNodeDto> nodes = new ArrayList<>(depthOf.size());
        for (Map.Entry<Integer, Integer> entry : depthOf.entrySet()) {
            nodes.add(toNode(heap, dom, entry.getKey(), entry.getValue()));
        }

        // edges between the chosen nodes only
        int maxEdges = maxNodes * 5;
        List<GraphEdgeDto> edges = new ArrayList<>();
        edgeLoop:
        for (int node : depthOf.keySet()) {
            for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
                int target = graph.edgeTarget(edge);
                if (!depthOf.containsKey(target)) {
                    continue;
                }
                if (edges.size() >= maxEdges) {
                    truncated = true;
                    break edgeLoop;
                }
                edges.add(new GraphEdgeDto(
                        ObjectIds.format(graph.idAt(node)),
                        ObjectIds.format(graph.idAt(target)),
                        HprofHeapGraphService.fieldText(graph.labelOf(graph.edgeLabelId(edge)))));
            }
        }

        return new SubgraphDto(ObjectIds.format(graph.idAt(start)), nodes, edges, truncated);
    }

    private static GraphNodeDto fromRetained(RetainedObjectDto dto, int depth) {
        return new GraphNodeDto(dto.objectId(), dto.className(), dto.kind(),
                dto.shallowBytes(), dto.retainedBytes(), dto.retainedPercent(), depth);
    }

    private static GraphNodeDto toNode(LoadedHeap heap, DominatorTree dom, int node, int depth) {
        ObjectGraph graph = heap.graph();
        long total = dom.reachableBytes();
        long retained = dom.retainedBytes(node);
        return new GraphNodeDto(
                ObjectIds.format(graph.idAt(node)),
                heap.displayName(node),
                graph.kindAt(node).name(),
                graph.shallowBytesAt(node),
                retained,
                total == 0 ? 0 : 100.0 * retained / total,
                depth);
    }
}