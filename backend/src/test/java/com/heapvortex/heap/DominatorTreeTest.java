package com.heapvortex.heap;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DominatorTreeTest {

    /** Builds a small ObjectGraph from nodes, edges and roots. */
    private static final class Fixture {
        private static final int FIELD = 0;
        private static final int REFERENT = 1;

        private final List<Integer> sizes = new ArrayList<>();
        private final List<int[]> edges = new ArrayList<>();
        private final Map<Integer, RootKind> roots = new LinkedHashMap<>();

        int node(int shallowBytes) {
            sizes.add(shallowBytes);
            return sizes.size() - 1;
        }

        void edge(int from, int to) {
            edges.add(new int[]{from, to, FIELD});
        }

        /** A reference through the field "referent", which the dominator tree ignores. */
        void weakEdge(int from, int to) {
            edges.add(new int[]{from, to, REFERENT});
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
            int[] labelIds = new int[m];
            for (int[] e : edges) {
                int pos = cursor[e[0]]++;
                targets[pos] = e[1];
                labelIds[pos] = e[2];
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
            int[] inLabels = new int[m];
            for (int source = 0; source < n; source++) {
                for (int edge = offsets[source]; edge < offsets[source + 1]; edge++) {
                    int pos = inCursor[targets[edge]]++;
                    inSources[pos] = source;
                    inLabels[pos] = labelIds[edge];
                }
            }

            long[] ids = new long[n];
            int[] shallow = new int[n];
            Map<Long, Integer> indexById = new HashMap<>();
            for (int i = 0; i < n; i++) {
                ids[i] = 0x1000L + i;
                shallow[i] = sizes.get(i);
                indexById.put(ids[i], i);
            }

            return new ObjectGraph(
                    ids,
                    new byte[n],            // all nodes are INSTANCE
                    new long[n],
                    shallow,
                    offsets,
                    targets,
                    labelIds,
                    new String[]{"field", "referent"},
                    inOffsets,
                    inSources,
                    inLabels,
                    indexById,
                    new LinkedHashMap<>(roots));
        }
    }

    @Test
    void chainRetainsEverythingBelow() {
        Fixture f = new Fixture();
        int r = f.node(10);
        int a = f.node(20);
        int b = f.node(30);
        f.root(r);
        f.edge(r, a);
        f.edge(a, b);

        DominatorTree dom = DominatorTree.compute(f.build());

        assertEquals(DominatorTree.VIRTUAL_ROOT, dom.dominatorOf(r));
        assertEquals(r, dom.dominatorOf(a));
        assertEquals(a, dom.dominatorOf(b));
        assertEquals(60, dom.retainedBytes(r));
        assertEquals(50, dom.retainedBytes(a));
        assertEquals(30, dom.retainedBytes(b));
        assertEquals(60, dom.reachableBytes());
    }

    @Test
    void diamondIsDominatedByTheTop() {
        // R -> A, R -> B, A -> C, B -> C : C is reachable two ways, so only R dominates it
        Fixture f = new Fixture();
        int r = f.node(1);
        int a = f.node(2);
        int b = f.node(4);
        int c = f.node(8);
        f.root(r);
        f.edge(r, a);
        f.edge(r, b);
        f.edge(a, c);
        f.edge(b, c);

        DominatorTree dom = DominatorTree.compute(f.build());

        assertEquals(r, dom.dominatorOf(c));
        assertEquals(2, dom.retainedBytes(a));
        assertEquals(4, dom.retainedBytes(b));
        assertEquals(15, dom.retainedBytes(r));
    }

    @Test
    void objectSharedByTwoRootsHasTheVirtualRootAsDominator() {
        Fixture f = new Fixture();
        int r1 = f.node(5);
        int r2 = f.node(7);
        int shared = f.node(100);
        f.root(r1);
        f.root(r2);
        f.edge(r1, shared);
        f.edge(r2, shared);

        DominatorTree dom = DominatorTree.compute(f.build());

        assertEquals(DominatorTree.VIRTUAL_ROOT, dom.dominatorOf(shared));
        assertEquals(100, dom.retainedBytes(shared));
        assertEquals(5, dom.retainedBytes(r1));
        assertEquals(7, dom.retainedBytes(r2));
        assertEquals(112, dom.reachableBytes());
    }

    @Test
    void aRootReferencedByAnotherRootStaysTopLevel() {
        Fixture f = new Fixture();
        int r1 = f.node(10);
        int r2 = f.node(20);
        f.root(r1);
        f.root(r2);
        f.edge(r1, r2);

        DominatorTree dom = DominatorTree.compute(f.build());

        assertEquals(DominatorTree.VIRTUAL_ROOT, dom.dominatorOf(r2));
        assertEquals(10, dom.retainedBytes(r1));
        assertEquals(20, dom.retainedBytes(r2));
    }

    @Test
    void cycleIsRetainedByItsEntryPoint() {
        // R -> A -> B -> A
        Fixture f = new Fixture();
        int r = f.node(1);
        int a = f.node(10);
        int b = f.node(100);
        f.root(r);
        f.edge(r, a);
        f.edge(a, b);
        f.edge(b, a);

        DominatorTree dom = DominatorTree.compute(f.build());

        assertEquals(r, dom.dominatorOf(a));
        assertEquals(a, dom.dominatorOf(b));
        assertEquals(110, dom.retainedBytes(a));
        assertEquals(111, dom.retainedBytes(r));
    }

    @Test
    void weakReferencesDoNotKeepObjectsAlive() {
        Fixture f = new Fixture();
        int r = f.node(10);
        int weakTarget = f.node(50);
        f.root(r);
        f.weakEdge(r, weakTarget);

        DominatorTree dom = DominatorTree.compute(f.build());

        assertFalse(dom.isReachable(weakTarget));
        assertEquals(DominatorTree.UNREACHABLE, dom.dominatorOf(weakTarget));
        assertEquals(50, dom.retainedBytes(weakTarget)); // an unreachable object retains only itself
        assertEquals(10, dom.reachableBytes());
    }

    @Test
    void anObjectWithBothStrongAndWeakPathStaysReachable() {
        Fixture f = new Fixture();
        int r = f.node(10);
        int a = f.node(20);
        int target = f.node(30);
        f.root(r);
        f.edge(r, a);
        f.edge(a, target);
        f.weakEdge(r, target); // the weak path must not change the dominator

        DominatorTree dom = DominatorTree.compute(f.build());

        assertEquals(a, dom.dominatorOf(target));
        assertEquals(50, dom.retainedBytes(a));
    }

    @Test
    void objectsWithoutAnyPathFromARootAreUnreachable() {
        Fixture f = new Fixture();
        int r = f.node(10);
        int orphan = f.node(99);
        int orphanChild = f.node(1);
        f.root(r);
        f.edge(orphan, orphanChild);

        DominatorTree dom = DominatorTree.compute(f.build());

        assertTrue(dom.isReachable(r));
        assertFalse(dom.isReachable(orphan));
        assertFalse(dom.isReachable(orphanChild));
        assertEquals(10, dom.reachableBytes());
    }

    @Test
    void childListAndLargestChildFollowTheDominatorTree() {
        Fixture f = new Fixture();
        int r = f.node(1);
        int small = f.node(5);
        int big = f.node(500);
        int bigChild = f.node(50);
        f.root(r);
        f.edge(r, small);
        f.edge(r, big);
        f.edge(big, bigChild);

        DominatorTree dom = DominatorTree.compute(f.build());

        assertEquals(2, dom.childrenEnd(r) - dom.childrenStart(r));
        assertEquals(big, dom.largestChild(r));
        assertEquals(bigChild, dom.largestChild(big));
        assertEquals(-1, dom.largestChild(bigChild));
    }

    @Test
    void retainedSizesOfTopLevelDominatorsAddUpToTheReachableBytes() {
        Fixture f = new Fixture();
        int r1 = f.node(3);
        int r2 = f.node(4);
        int a = f.node(10);
        int b = f.node(20);
        int shared = f.node(40);
        f.root(r1);
        f.root(r2);
        f.edge(r1, a);
        f.edge(r2, b);
        f.edge(a, shared);
        f.edge(b, shared);

        ObjectGraph graph = f.build();
        DominatorTree dom = DominatorTree.compute(graph);

        long sum = 0;
        for (int node = 0; node < graph.size(); node++) {
            if (dom.dominatorOf(node) == DominatorTree.VIRTUAL_ROOT) {
                sum += dom.retainedBytes(node);
            }
        }
        assertEquals(dom.reachableBytes(), sum);
        assertEquals(77, sum);
    }
}