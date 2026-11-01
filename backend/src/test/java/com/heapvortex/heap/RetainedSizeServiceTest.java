package com.heapvortex.heap;

import com.heapvortex.dto.DominatorSuspectDto;
import com.heapvortex.dto.GcRootPathDto;
import com.heapvortex.service.HeapGraphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetainedSizeServiceTest {

    private static final long HOLDER = 100;
    private static final long LIST = 101;
    private static final long ARRAY = 102;
    private static final long LEAKED = 103;
    private static final long STRING = 104;
    private static final long NODE_A = 105;
    private static final long NODE_B = 106;

    @TempDir
    Path tempDir;

    private HeapGraphService graphService;

    @BeforeEach
    void setUp() {
        graphService = mock(HeapGraphService.class);
        when(graphService.pathToGcRoot(anyString(), anyString(), anyInt()))
                .thenReturn(new GcRootPathDto("0x0", true, "JAVA_FRAME", Collections.emptyList()));
    }

    /** Builds a graph node by node. All nodes are plain instances. */
    private static final class Builder {
        private final List<Integer> sizes = new ArrayList<>();
        private final List<Long> classes = new ArrayList<>();
        private final List<int[]> edges = new ArrayList<>();
        private final Map<Integer, RootKind> roots = new LinkedHashMap<>();

        int node(int bytes, long classId) {
            sizes.add(bytes);
            classes.add(classId);
            return sizes.size() - 1;
        }

        void edge(int from, int to) {
            edges.add(new int[]{from, to});
        }

        void root(int node) {
            roots.put(node, RootKind.JAVA_FRAME);
        }

        ObjectGraph build() {
            int n = sizes.size();
            int m = edges.size();

            int[] offsets = new int[n + 1];
            for (int[] e : edges) {
                offsets[e[0] + 1]++;
            }
            for (int i = 0; i < n; i++) {
                offsets[i + 1] += offsets[i];
            }
            int[] cursor = Arrays.copyOf(offsets, n);
            int[] targets = new int[m];
            for (int[] e : edges) {
                targets[cursor[e[0]]++] = e[1];
            }

            int[] inOffsets = new int[n + 1];
            for (int target : targets) {
                inOffsets[target + 1]++;
            }
            for (int i = 0; i < n; i++) {
                inOffsets[i + 1] += inOffsets[i];
            }
            int[] inCursor = Arrays.copyOf(inOffsets, n);
            int[] inSources = new int[m];
            for (int source = 0; source < n; source++) {
                for (int edge = offsets[source]; edge < offsets[source + 1]; edge++) {
                    inSources[inCursor[targets[edge]]++] = source;
                }
            }

            long[] ids = new long[n];
            int[] shallow = new int[n];
            long[] classIds = new long[n];
            Map<Long, Integer> indexById = new HashMap<>();
            for (int i = 0; i < n; i++) {
                ids[i] = 0x1000L + i;
                shallow[i] = sizes.get(i);
                classIds[i] = classes.get(i);
                indexById.put(ids[i], i);
            }

            return new ObjectGraph(ids, new byte[n], classIds, shallow,
                    offsets, targets, new int[m],
                    new String[]{"field", "referent"},
                    inOffsets, inSources, new int[m],
                    indexById, new LinkedHashMap<>(roots));
        }
    }

    private static ClassIndex classIndex() {
        ClassIndex index = new ClassIndex();
        String[] names = {"com/app/Holder", "java/util/ArrayList", "[Ljava/lang/Object;",
                "com/app/Leaked", "java/lang/String", "com/app/A", "com/app/B"};
        long[] classIds = {HOLDER, LIST, ARRAY, LEAKED, STRING, NODE_A, NODE_B};
        for (int i = 0; i < names.length; i++) {
            index.onString(i + 1, names[i]);
            index.onLoadClass(classIds[i], i + 1);
        }
        return index;
    }

    private RetainedSizeService serviceFor(ObjectGraph graph) {
        LoadedHeap heap = new LoadedHeap(classIndex(), graph);
        HeapDumpStorage storage = new HeapDumpStorage(tempDir.toString());
        HeapLoader loader = new HeapLoader(storage, 1000, 1) {
            @Override
            public synchronized LoadedHeap load(String dumpId) {
                return heap;
            }
        };
        return new RetainedSizeService(loader, graphService);
    }

    @Test
    void walksDownToTheArrayAndNamesTheLeakedClass() {
        // Holder -> ArrayList -> Object[] -> 10 x Leaked (100 bytes each)
        // retained: Leaked 100, array 40+1000=1040, list 24+1040=1064, holder 16+1064=1080
        Builder b = new Builder();
        int holder = b.node(16, HOLDER);
        int list = b.node(24, LIST);
        int array = b.node(40, ARRAY);
        b.root(holder);
        b.edge(holder, list);
        b.edge(list, array);
        for (int i = 0; i < 10; i++) {
            int leaked = b.node(100, LEAKED);
            b.edge(array, leaked);
        }

        List<DominatorSuspectDto> result = serviceFor(b.build()).suspects("dump", 1, 100);

        assertEquals(1, result.size());
        DominatorSuspectDto s = result.get(0);
        assertEquals("com.app.Holder", s.topClassName());
        assertEquals(1080, s.topRetainedBytes());
        assertEquals(100.0, s.heapPercent(), 0.001);
        // 1040 >= 0.8 * 1064 keeps descending; 100 < 0.8 * 1040 stops at the array
        assertEquals(List.of("com.app.Holder", "java.util.ArrayList", "java.lang.Object[]"),
                s.dominatorChain());
        assertEquals("0x" + Long.toHexString(0x1000L + array), s.accumulationObjectId());
        assertEquals(1040, s.accumulationRetainedBytes());
        assertEquals("com.app.Leaked", s.likelyLeakClass());
        assertTrue(s.dominatedClasses().stream()
                .anyMatch(c -> c.className().equals("com.app.Leaked") && c.instanceCount() == 10));
        assertTrue(s.pathToGcRoot().found());
    }

    @Test
    void jdkClassesAreSkippedWhenNamingTheLeakClass() {
        // The array holds 5 Strings and 2 Leaked objects. Strings are more numerous but belong to the JDK.
        Builder b = new Builder();
        int holder = b.node(16, HOLDER);
        int array = b.node(40, ARRAY);
        b.root(holder);
        b.edge(holder, array);
        for (int i = 0; i < 5; i++) {
            b.edge(array, b.node(30, STRING));
        }
        for (int i = 0; i < 2; i++) {
            b.edge(array, b.node(200, LEAKED));
        }

        DominatorSuspectDto s = serviceFor(b.build()).suspects("dump", 1, 100).get(0);

        assertEquals("com.app.Leaked", s.likelyLeakClass());
    }

    @Test
    void stopsAtTheTopWhenTheMemoryIsSplitBetweenChildren() {
        // Holder 10 with two children of 100 each: 100 < 0.8 * 210, so there is no single accumulation path
        Builder b = new Builder();
        int holder = b.node(10, HOLDER);
        int a = b.node(100, NODE_A);
        int c = b.node(100, NODE_B);
        b.root(holder);
        b.edge(holder, a);
        b.edge(holder, c);

        DominatorSuspectDto s = serviceFor(b.build()).suspects("dump", 1, 100).get(0);

        assertEquals(List.of("com.app.Holder"), s.dominatorChain());
        assertEquals(s.topObjectId(), s.accumulationObjectId());
        assertEquals(210, s.accumulationRetainedBytes());
    }
}