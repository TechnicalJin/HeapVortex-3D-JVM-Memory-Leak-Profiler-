package com.heapvortex.heap;

import com.heapvortex.dto.*;
import com.heapvortex.exception.ObjectNotFoundException;
import com.heapvortex.service.HeapGraphService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class HprofHeapGraphService implements HeapGraphService {

    private final HeapLoader loader;

    public HprofHeapGraphService(HeapLoader loader) {
        this.loader = loader;
    }

    @Override
    public GraphStatsDto stats(String dumpId) {
        ObjectGraph graph = loader.load(dumpId).graph();

        long[] counts = new long[ObjectGraph.Kind.values().length];
        for (int node = 0; node < graph.size(); node++) {
            counts[graph.kindAt(node).ordinal()]++;
        }
        Map<String, Long> byKind = new LinkedHashMap<>();
        for (ObjectGraph.Kind kind : ObjectGraph.Kind.values()) {
            byKind.put(kind.name(), counts[kind.ordinal()]);
        }
        return new GraphStatsDto(graph.size(), graph.edgeCount(), graph.roots().size(), byKind);
    }

    @Override
    public List<ObjectSummaryDto> instancesOf(String dumpId, String className, int limit) {
        LoadedHeap heap = loader.load(dumpId);
        ObjectGraph graph = heap.graph();

        List<ObjectSummaryDto> result = new ArrayList<>();
        for (int node = 0; node < graph.size() && result.size() < limit; node++) {
            if (graph.kindAt(node) == ObjectGraph.Kind.CLASS) {
                continue;
            }
            if (heap.typeName(node).equals(className)) {
                result.add(new ObjectSummaryDto(
                        ObjectIds.format(graph.idAt(node)),
                        className,
                        graph.shallowBytesAt(node),
                        graph.edgeEnd(node) - graph.edgeStart(node)));
            }
        }
        return result;
    }

    @Override
    public ObjectDetailsDto object(String dumpId, String objectId, int maxReferences) {
        LoadedHeap heap = loader.load(dumpId);
        ObjectGraph graph = heap.graph();
        int node = nodeOf(heap, objectId);

        List<ReferenceDto> references = new ArrayList<>();
        for (int edge = graph.edgeStart(node);
             edge < graph.edgeEnd(node) && references.size() < maxReferences;
             edge++) {
            int target = graph.edgeTarget(edge);
            references.add(new ReferenceDto(
                    fieldText(graph.labelOf(graph.edgeLabelId(edge))),
                    ObjectIds.format(graph.idAt(target)),
                    heap.displayName(target),
                    graph.shallowBytesAt(target)));
        }

        return new ObjectDetailsDto(
                ObjectIds.format(graph.idAt(node)),
                graph.kindAt(node).name(),
                heap.displayName(node),
                graph.shallowBytesAt(node),
                graph.edgeEnd(node) - graph.edgeStart(node),
                references);
    }

    @Override
    public GcRootPathDto pathToGcRoot(
            String dumpId,
            String objectId,
            int maxDepth) {

        LoadedHeap heap = loader.load(dumpId);
        ObjectGraph graph = heap.graph();
        int target = nodeOf(heap, objectId);

        int[] parent = new int[graph.size()];
        int[] parentLabel = new int[graph.size()];
        int[] depth = new int[graph.size()];
        Arrays.fill(parent, -2);
        Arrays.fill(parentLabel, ObjectGraphBuilder.NO_LABEL);

        Deque<Integer> queue = new ArrayDeque<>();
        parent[target] = -1;
        queue.add(target);

        int foundRoot = -1;

        while (!queue.isEmpty()) {
            int current = queue.removeFirst();

            if (graph.isRoot(current)) {
                foundRoot = current;
                break;
            }
            if (depth[current] >= maxDepth) {
                continue;
            }

            for (int edge = graph.incomingStart(current);
                 edge < graph.incomingEnd(current);
                 edge++) {
                int source = graph.incomingSource(edge);
                if (parent[source] != -2) {
                    continue;
                }
                parent[source] = current;
                parentLabel[source] = graph.incomingLabelId(edge);
                depth[source] = depth[current] + 1;
                queue.addLast(source);
            }
        }

        if (foundRoot < 0) {
            return new GcRootPathDto(
                    ObjectIds.format(graph.idAt(target)),
                    false,
                    null,
                    Collections.emptyList());
        }

        List<GcRootPathDto.PathNode> path = new ArrayList<>();
        int current = foundRoot;

        path.add(new GcRootPathDto.PathNode(
                ObjectIds.format(graph.idAt(current)),
                heap.displayName(current),
                null));

        while (current != target) {
            int next = parent[current];
            String reference = graph.labelOf(parentLabel[current]);

            path.add(new GcRootPathDto.PathNode(
                    ObjectIds.format(graph.idAt(next)),
                    heap.displayName(next),
                    reference == null ? "[element]" : reference));

            current = next;
        }

        return new GcRootPathDto(
                ObjectIds.format(graph.idAt(target)),
                true,
                graph.rootKindOf(foundRoot).name(),
                path);
    }

    static int nodeOf(LoadedHeap heap, String objectId) {
        int node = heap.graph().indexOf(ObjectIds.parse(objectId));
        if (node < 0) {
            throw new ObjectNotFoundException(objectId);
        }
        return node;
    }

    static String fieldText(String label) {
        return label == null ? "[element]" : label;
    }
}