package com.heapvortex.heap;

import com.heapvortex.dto.ClassRetentionDto;
import com.heapvortex.dto.DominatorSuspectDto;
import com.heapvortex.dto.GcRootPathDto;
import com.heapvortex.dto.RetainedObjectDto;
import com.heapvortex.service.HeapGraphService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Retained sizes, the dominator tree and leak suspects of ONE heap dump.
 * A suspect is a top-level dominator (an object kept alive only by a GC root)
 * that retains a big part of the heap. From there we walk down the dominator tree
 * while one child keeps most of the bytes. The walk stops at the "accumulation point",
 * where the memory is spread over many objects. That is where the leak is collected.
 */
@Service
public class RetainedSizeService {

    private static final double ACCUMULATION_RATIO = 0.8;
    private static final int MAX_CHAIN = 50;
    private static final int MAX_CLASSES_REPORTED = 10;
    private static final String[] JDK_PREFIXES = {"java.", "javax.", "jdk.", "sun.", "com.sun."};

    private final HeapLoader loader;
    private final HeapGraphService graphService;

    public RetainedSizeService(HeapLoader loader, HeapGraphService graphService) {
        this.loader = loader;
        this.graphService = graphService;
    }

    /** The objects with the biggest retained size, biggest first. */
    public List<RetainedObjectDto> topDominators(String dumpId, int limit, boolean topLevelOnly) {
        LoadedHeap heap = loader.load(dumpId);
        DominatorTree dom = heap.dominators();
        List<RetainedObjectDto> result = new ArrayList<>();
        for (int node : topNodes(heap.graph(), dom, limit, topLevelOnly)) {
            result.add(toDto(heap, dom, node));
        }
        return result;
    }

    /** The objects that this object dominates directly, biggest retained size first. */
    public List<RetainedObjectDto> dominated(String dumpId, String objectId, int limit) {
        LoadedHeap heap = loader.load(dumpId);
        int node = HprofHeapGraphService.nodeOf(heap, objectId);
        DominatorTree dom = heap.dominators();

        List<Integer> children = new ArrayList<>();
        for (int i = dom.childrenStart(node); i < dom.childrenEnd(node); i++) {
            children.add(dom.child(i));
        }
        children.sort(Comparator.<Integer>comparingLong(dom::retainedBytes).reversed());

        List<RetainedObjectDto> result = new ArrayList<>();
        for (int child : children) {
            if (result.size() >= limit) {
                break;
            }
            result.add(toDto(heap, dom, child));
        }
        return result;
    }

    public List<DominatorSuspectDto> suspects(String dumpId, int limit, int maxDepth) {
        LoadedHeap heap = loader.load(dumpId);
        ObjectGraph graph = heap.graph();
        DominatorTree dom = heap.dominators();

        List<DominatorSuspectDto> result = new ArrayList<>();
        for (int top : topNodes(graph, dom, limit, true)) {
            List<String> chain = new ArrayList<>();
            int current = top;
            chain.add(heap.displayName(current));
            while (chain.size() < MAX_CHAIN) {
                int child = dom.largestChild(current);
                if (child < 0 || dom.retainedBytes(child) < ACCUMULATION_RATIO * dom.retainedBytes(current)) {
                    break;
                }
                current = child;
                chain.add(heap.displayName(current));
            }

            Map<String, long[]> tally = tallySubtree(heap, dom, current);
            GcRootPathDto path = graphService.pathToGcRoot(
                    dumpId, ObjectIds.format(graph.idAt(current)), maxDepth);

            result.add(new DominatorSuspectDto(
                    ObjectIds.format(graph.idAt(top)),
                    heap.displayName(top),
                    dom.retainedBytes(top),
                    percent(dom, dom.retainedBytes(top)),
                    List.copyOf(chain),
                    ObjectIds.format(graph.idAt(current)),
                    heap.displayName(current),
                    dom.retainedBytes(current),
                    likelyLeakClass(tally),
                    classRetention(tally),
                    path));
        }
        return result;
    }

    private static int[] topNodes(ObjectGraph graph, DominatorTree dom, int limit, boolean topLevelOnly) {
        PriorityQueue<Integer> best = new PriorityQueue<>(
                Comparator.<Integer>comparingLong(dom::retainedBytes));
        for (int node = 0; node < graph.size(); node++) {
            if (!dom.isReachable(node)) {
                continue;
            }
            if (topLevelOnly && dom.dominatorOf(node) != DominatorTree.VIRTUAL_ROOT) {
                continue;
            }
            if (best.size() < limit) {
                best.add(node);
            } else if (dom.retainedBytes(node) > dom.retainedBytes(best.peek())) {
                best.poll();
                best.add(node);
            }
        }
        int[] result = new int[best.size()];
        for (int i = result.length - 1; i >= 0; i--) {
            result[i] = best.poll();
        }
        return result;
    }

    /** Count and shallow bytes per type for everything below a node in the dominator tree. */
    private static Map<String, long[]> tallySubtree(LoadedHeap heap, DominatorTree dom, int start) {
        Map<String, long[]> tally = new HashMap<>();
        int[] stack = new int[dom.size()];
        int top = 0;
        stack[top++] = start;
        while (top > 0) {
            int node = stack[--top];
            long[] stats = tally.computeIfAbsent(heap.typeName(node), k -> new long[2]);
            stats[0]++;
            stats[1] += heap.graph().shallowBytesAt(node);
            for (int i = dom.childrenStart(node); i < dom.childrenEnd(node); i++) {
                stack[top++] = dom.child(i);
            }
        }
        return tally;
    }

    /**
     * Heuristic: the application class with the most instances below the accumulation point.
     * JDK classes and arrays are skipped. If nothing is left, the type with the most bytes wins.
     */
    private static String likelyLeakClass(Map<String, long[]> tally) {
        String best = null;
        long[] bestStats = null;
        for (Map.Entry<String, long[]> entry : tally.entrySet()) {
            String name = entry.getKey();
            if (name.endsWith("[]") || isJdkClass(name)) {
                continue;
            }
            long[] stats = entry.getValue();
            if (bestStats == null || stats[0] > bestStats[0]
                    || (stats[0] == bestStats[0] && stats[1] > bestStats[1])) {
                best = name;
                bestStats = stats;
            }
        }
        if (best != null) {
            return best;
        }
        return tally.entrySet().stream()
                .max(Comparator.comparingLong(e -> e.getValue()[1]))
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    private static boolean isJdkClass(String name) {
        for (String prefix : JDK_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static List<ClassRetentionDto> classRetention(Map<String, long[]> tally) {
        return tally.entrySet().stream()
                .map(e -> new ClassRetentionDto(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(ClassRetentionDto::shallowBytes).reversed())
                .limit(MAX_CLASSES_REPORTED)
                .toList();
    }

    private RetainedObjectDto toDto(LoadedHeap heap, DominatorTree dom, int node) {
        ObjectGraph graph = heap.graph();
        return new RetainedObjectDto(
                ObjectIds.format(graph.idAt(node)),
                graph.kindAt(node).name(),
                heap.displayName(node),
                graph.shallowBytesAt(node),
                dom.retainedBytes(node),
                percent(dom, dom.retainedBytes(node)));
    }

    private static double percent(DominatorTree dom, long bytes) {
        long total = dom.reachableBytes();
        return total == 0 ? 0 : 100.0 * bytes / total;
    }
}