package com.heapvortex.heap;

import com.heapvortex.dto.GraphStatsDto;
import com.heapvortex.dto.ObjectDetailsDto;
import com.heapvortex.dto.ObjectSummaryDto;
import com.heapvortex.dto.ReferenceDto;
import com.heapvortex.exception.ObjectNotFoundException;
import com.heapvortex.service.HeapGraphService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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