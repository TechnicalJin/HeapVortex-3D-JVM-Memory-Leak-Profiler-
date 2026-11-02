
package com.heapvortex.heap;

import java.util.Collections;
import java.util.Map;

/**
 * The references between the objects of a heap dump.
 * Nodes are numbered 0 to size()-1. For primitive arrays, classIdAt() holds
 * the negative type code (for example -8 for byte[]).
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

    // Outgoing edges: source object -> referenced object.
    private final int[] edgeOffsets;
    private final int[] edgeTargets;
    private final int[] edgeLabelIds;

    // Incoming edges: referenced object -> objects that reference it.
    private final int[] incomingOffsets;
    private final int[] incomingSources;
    private final int[] incomingLabelIds;

    private final String[] labels;
    private final Map<Long, Integer> indexById;
    private final Map<Integer, RootKind> roots;

    public static final int LABEL_NOT_FOUND = -2;

    ObjectGraph(
            long[] ids,
            byte[] kinds,
            long[] classIds,
            int[] shallowBytes,
            int[] edgeOffsets,
            int[] edgeTargets,
            int[] edgeLabelIds,
            String[] labels,
            int[] incomingOffsets,
            int[] incomingSources,
            int[] incomingLabelIds,
            Map<Long, Integer> indexById,
            Map<Integer, RootKind> roots) {

        this.ids = ids;
        this.kinds = kinds;
        this.classIds = classIds;
        this.shallowBytes = shallowBytes;

        this.edgeOffsets = edgeOffsets;
        this.edgeTargets = edgeTargets;
        this.edgeLabelIds = edgeLabelIds;

        this.incomingOffsets = incomingOffsets;
        this.incomingSources = incomingSources;
        this.incomingLabelIds = incomingLabelIds;

        this.labels = labels;
        this.indexById = indexById;
        this.roots = roots;
    }

    public int labelIdOf(String name) {
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals(name)) {
                return i;
            }
        }
        return LABEL_NOT_FOUND;
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

    /** The node of an object ID, or -1 if the dump has no such object. */
    public int indexOf(long objectId) {
        Integer index = indexById.get(objectId);
        return index == null ? -1 : index;
    }

    /** Start of the outgoing edges for a node. */
    public int edgeStart(int node) {
        return edgeOffsets[node];
    }

    /** End of the outgoing edges for a node. */
    public int edgeEnd(int node) {
        return edgeOffsets[node + 1];
    }

    /** Target node of an outgoing edge. */
    public int edgeTarget(int edge) {
        return edgeTargets[edge];
    }

    /** Label ID of an outgoing edge. */
    public int edgeLabelId(int edge) {
        return edgeLabelIds[edge];
    }

    /** Start of the incoming edges for a node. */
    public int incomingStart(int node) {
        return incomingOffsets[node];
    }

    /** End of the incoming edges for a node. */
    public int incomingEnd(int node) {
        return incomingOffsets[node + 1];
    }

    /** Source node that references the target of an incoming edge. */
    public int incomingSource(int edge) {
        return incomingSources[edge];
    }

    /** Label ID of an incoming edge. */
    public int incomingLabelId(int edge) {
        return incomingLabelIds[edge];
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