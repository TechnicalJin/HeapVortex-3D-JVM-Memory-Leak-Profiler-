package com.heapvortex.heap;

import com.heapvortex.support.GraphTestSupport;
import com.sun.management.HotSpotDiagnosticMXBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealDumpGraphTest {

    static final class ChainNode {
        ChainNode next;
    }

    /** A static field: the path from a GC root to the chain goes through it. */
    static ChainNode HEAD;

    @TempDir
    Path tempDir;

    @AfterEach
    void release() {
        HEAD = null;
    }

    @Test
    void theRealGraphContainsTheChainAndTheStaticReference() throws Exception {
        ChainNode a = new ChainNode();
        ChainNode b = new ChainNode();
        ChainNode c = new ChainNode();
        a.next = b;
        b.next = c;
        HEAD = a;

        Path dump = tempDir.resolve("own.hprof");
        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).dumpHeap(dump.toString(), true);

        LoadedHeap heap = GraphTestSupport.load(dump);
        ObjectGraph graph = heap.graph();

        List<Integer> chain = new ArrayList<>();
        for (int node = 0; node < graph.size(); node++) {
            if (graph.kindAt(node) == ObjectGraph.Kind.INSTANCE
                    && heap.typeName(node).endsWith("RealDumpGraphTest$ChainNode")) {
                chain.add(node);
            }
        }
        assertEquals(3, chain.size());

        // two of the three nodes refer to another node through the field next
        long withNext = chain.stream()
                .filter(n -> graph.edgeEnd(n) - graph.edgeStart(n) == 1)
                .filter(n -> "next".equals(graph.labelOf(graph.edgeLabelId(graph.edgeStart(n)))))
                .count();
        assertEquals(2, withNext);

        // the test class has a static field HEAD that points to one of them
        boolean staticFound = false;
        for (int node = 0; node < graph.size(); node++) {
            if (graph.kindAt(node) != ObjectGraph.Kind.CLASS) {
                continue;
            }
            for (int edge = graph.edgeStart(node); edge < graph.edgeEnd(node); edge++) {
                if ("static HEAD".equals(graph.labelOf(graph.edgeLabelId(edge)))
                        && chain.contains(graph.edgeTarget(edge))) {
                    staticFound = true;
                }
            }
        }
        assertTrue(staticFound, "the static field HEAD was not found as an edge");
        assertTrue(graph.roots().size() > 0);
    }
}