package com.heapvortex.heap;

import java.util.Collections;
import java.util.Map;

/**
 * The references between the objects of a heap dump.
 * Nodes are numbered 0 to size()-1. For primitive arrays, classIdAt() holds the
 * negative type code (for example -8 for byte[]).
 */
public final class ObjectGraph {

    public enum Kind {
        INSTANCE,
        CLASS,
        OBJECT_ARRAY,
        PRIMITIVE_ARRAY
    }

    private static final Kind[] KINDS = Kind.values();

    private final long[] ids;
    private final byte[] kinds;
    private final long[] classIds;
    private final int[] shallowBytes;
    private final int[] edgeOffsets;
    private final int[] edgeTargets;
    private final int[] edgeLabelIds;
    private final String[] labels;
    private final Map<Long, Integer> indexById;
    private final Map<Integer, RootKind> roots;

    ObjectGraph(long[] ids, byte[] kinds, long[] classIds, int[] shallowBytes,
                int[] edgeOffsets, int[] edgeTargets, int[] edgeLabelIds, String[] labels,
                Map<Long, Integer> indexById, Map<Integer, RootKind> roots) {
        this.ids = ids;
        this.kinds = kinds;
        this.classIds = classIds;
        this.shallowBytes = shallowBytes;
        this.edgeOffsets = edgeOffsets;
        this.edgeTargets = edgeTargets;
        this.edgeLabelIds = edgeLabelIds;
        this.labels = labels;
        this.indexById = indexById;
        this.roots = roots;
    }

    public int size() {
        return ids.length;
    }

    public int edgeCount() {
        return edgeTargets.length;
    }

    public long idAt(int node) {
        return ids[node];
    }

    public Kind kindAt(int node) {
        return KINDS[kinds[node]];
    }

    public long classIdAt(int node) {
        return classIds[node];
    }

    public int shallowBytesAt(int node) {
        return shallowBytes[node];
    }

    /** The node of an object id, or -1 if the dump has no such object. */
    public int indexOf(long objectId) {
        Integer index = indexById.get(objectId);
        return index == null ? -1 : index;
    }

    public int edgeStart(int node) {
        return edgeOffsets[node];
    }

    public int edgeEnd(int node) {
        return edgeOffsets[node + 1];
    }

    public int edgeTarget(int edge) {
        return edgeTargets[edge];
    }

    public int edgeLabelId(int edge) {
        return edgeLabelIds[edge];
    }

    /** The field name of an edge, or null for an array element. */
    public String labelOf(int labelId) {
        return labelId < 0 ? null : labels[labelId];
    }

    public Map<Integer, RootKind> roots() {
        return Collections.unmodifiableMap(roots);
    }

    public boolean isRoot(int node) {
        return roots.containsKey(node);
    }

    public RootKind rootKindOf(int node) {
        return roots.get(node);
    }
}