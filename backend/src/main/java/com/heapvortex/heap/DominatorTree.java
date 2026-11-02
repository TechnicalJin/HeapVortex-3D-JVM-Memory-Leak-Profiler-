package com.heapvortex.heap;

import java.util.Arrays;

/**
 * Dominators and retained sizes of a heap graph.
 * Algorithm: Cooper, Harvey and Kennedy, "A Simple, Fast Dominance Algorithm".
 * A virtual root points to every GC root. Weak references (the field referent) are ignored.
 */
public final class DominatorTree {

    /** The dominator of this node is the virtual root: a GC root, or reachable through several roots. */
    public static final int VIRTUAL_ROOT = -1;

    /** The node cannot be reached from any GC root through strong references. */
    public static final int UNREACHABLE = -2;

    private static final String WEAK_REFERENT_FIELD = "referent";

    private final int[] dominator;
    private final long[] retained;
    private final int[] childOffsets;
    private final int[] childNodes;
    private final long reachableBytes;

    private DominatorTree(int[] dominator, long[] retained, int[] childOffsets,
                          int[] childNodes, long reachableBytes) {
        this.dominator = dominator;
        this.retained = retained;
        this.childOffsets = childOffsets;
        this.childNodes = childNodes;
        this.reachableBytes = reachableBytes;
    }

    public static DominatorTree compute(ObjectGraph graph) {
        int n = graph.size();
        int superRoot = n; // the virtual root gets the number n
        int skipLabel = graph.labelIdOf(WEAK_REFERENT_FIELD);
        int[] rootNodes = graph.roots().keySet().stream().mapToInt(Integer::intValue).toArray();

        // Step 1: depth-first search without recursion. It gives every node a post-order number.
        int[] order = new int[n + 1];       // order[i] is the node with post-order number i
        int[] postorder = new int[n + 1];   // the post-order number of a node
        boolean[] visited = new boolean[n + 1];
        int[] cursor = new int[n + 1];      // the next edge to look at, for each node on the stack
        int[] stack = new int[n + 1];
        int count = 0;
        int top = 0;

        visited[superRoot] = true;
        stack[top++] = superRoot;
        while (top > 0) {
            int node = stack[top - 1];
            int next = -1;
            if (node == superRoot) {
                while (cursor[node] < rootNodes.length) {
                    int candidate = rootNodes[cursor[node]++];
                    if (!visited[candidate]) {
                        next = candidate;
                        break;
                    }
                }
            } else {
                int end = graph.edgeEnd(node);
                while (cursor[node] < end) {
                    int edge = cursor[node]++;
                    if (graph.edgeLabelId(edge) == skipLabel) {
                        continue;
                    }
                    int candidate = graph.edgeTarget(edge);
                    if (!visited[candidate]) {
                        next = candidate;
                        break;
                    }
                }
            }
            if (next >= 0) {
                visited[next] = true;
                cursor[next] = graph.edgeStart(next);
                stack[top++] = next;
            } else {
                top--;
                postorder[node] = count;
                order[count++] = node;
            }
        }

        // Step 2: improve the dominators until nothing changes.
        int[] idom = new int[n + 1];
        Arrays.fill(idom, -1);
        idom[superRoot] = superRoot;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = count - 1; i >= 0; i--) { // reverse post-order
                int node = order[i];
                if (node == superRoot) {
                    continue;
                }
                int newIdom = graph.isRoot(node) ? superRoot : -1;
                for (int edge = graph.incomingStart(node); edge < graph.incomingEnd(node); edge++) {
                    if (graph.incomingLabelId(edge) == skipLabel) {
                        continue;
                    }
                    int predecessor = graph.incomingSource(edge);
                    if (idom[predecessor] == -1) {
                        continue; // not reachable, or not processed yet
                    }
                    newIdom = newIdom == -1 ? predecessor : intersect(predecessor, newIdom, idom, postorder);
                }
                if (newIdom != -1 && idom[node] != newIdom) {
                    idom[node] = newIdom;
                    changed = true;
                }
            }
        }

        // Step 3: retained size. Post-order means every node comes before its dominator.
        long[] retained = new long[n];
        for (int node = 0; node < n; node++) {
            retained[node] = graph.shallowBytesAt(node);
        }
        for (int i = 0; i < count; i++) {
            int node = order[i];
            if (node != superRoot && idom[node] != superRoot) {
                retained[idom[node]] += retained[node];
            }
        }

        // Step 4: the public dominator array and the child lists of the dominator tree.
        int[] dominator = new int[n];
        int[] childOffsets = new int[n + 1];
        long reachableBytes = 0;
        for (int node = 0; node < n; node++) {
            if (!visited[node]) {
                dominator[node] = UNREACHABLE;
                continue;
            }
            reachableBytes += graph.shallowBytesAt(node);
            if (idom[node] == superRoot) {
                dominator[node] = VIRTUAL_ROOT;
            } else {
                dominator[node] = idom[node];
                childOffsets[idom[node] + 1]++;
            }
        }
        for (int i = 0; i < n; i++) {
            childOffsets[i + 1] += childOffsets[i];
        }
        int[] childNodes = new int[childOffsets[n]];
        int[] fill = Arrays.copyOf(childOffsets, n);
        for (int node = 0; node < n; node++) {
            if (dominator[node] >= 0) {
                childNodes[fill[dominator[node]]++] = node;
            }
        }

        return new DominatorTree(dominator, retained, childOffsets, childNodes, reachableBytes);
    }

    /** The nearest common ancestor of two nodes in the dominator tree built so far. */
    private static int intersect(int a, int b, int[] idom, int[] postorder) {
        while (a != b) {
            while (postorder[a] < postorder[b]) {
                a = idom[a];
            }
            while (postorder[b] < postorder[a]) {
                b = idom[b];
            }
        }
        return a;
    }

    public int size() {
        return dominator.length;
    }

    public boolean isReachable(int node) {
        return dominator[node] != UNREACHABLE;
    }

    /** The node that dominates this node, or VIRTUAL_ROOT, or UNREACHABLE. */
    public int dominatorOf(int node) {
        return dominator[node];
    }

    /** Shallow size of the object plus everything it dominates. An unreachable object retains only itself. */
    public long retainedBytes(int node) {
        return retained[node];
    }

    /** The sum of the shallow sizes of all objects that are reachable from the GC roots. */
    public long reachableBytes() {
        return reachableBytes;
    }

    public int childrenStart(int node) {
        return childOffsets[node];
    }

    public int childrenEnd(int node) {
        return childOffsets[node + 1];
    }

    public int child(int index) {
        return childNodes[index];
    }

    /** The dominated child with the biggest retained size, or -1 if the node dominates nothing. */
    public int largestChild(int node) {
        int best = -1;
        for (int i = childrenStart(node); i < childrenEnd(node); i++) {
            int candidate = childNodes[i];
            if (best == -1 || retained[candidate] > retained[best]) {
                best = candidate;
            }
        }
        return best;
    }
}