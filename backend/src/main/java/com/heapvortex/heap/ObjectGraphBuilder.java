package com.heapvortex.heap;

import com.heapvortex.exception.HeapTooLargeException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pass 2: reads the objects and builds the graph. Needs the ClassIndex from pass 1. */
public final class ObjectGraphBuilder implements HprofVisitor {

    public static final int NO_LABEL = -1;

    private final ClassIndex classes;
    private final int maxObjects;
    private int idSize;

    private final Map<Long, Integer> indexById = new HashMap<>();
    private final LongList ids = new LongList();
    private final IntList kinds = new IntList();
    private final LongList classIds = new LongList();
    private final IntList shallow = new IntList();

    private final IntList edgeSource = new IntList();
    private final LongList edgeTarget = new LongList();
    private final IntList edgeLabel = new IntList();

    private final Map<String, Integer> labelIds = new HashMap<>();
    private final List<String> labels = new ArrayList<>();

    private final LongList rootIds = new LongList();
    private final IntList rootKinds = new IntList();

    public ObjectGraphBuilder(ClassIndex classes, int maxObjects) {
        this.classes = classes;
        this.maxObjects = maxObjects;
    }

    @Override
    public boolean wantsDetails() {
        return true;
    }

    @Override
    public void onHeader(String version, int idSize, long timestampMillis) {
        this.idSize = idSize;
    }

    @Override
    public void onRoot(RootKind kind, long objectId) {
        rootIds.add(objectId);
        rootKinds.add(kind.ordinal());
    }

    @Override
    public void onClassDefinition(ClassDefinition definition) {
        int node = addNode(definition.classId(), ObjectGraph.Kind.CLASS, definition.classId(), 0);
        long[] names = definition.staticFieldNameIds();
        long[] references = definition.staticReferences();
        for (int i = 0; i < references.length; i++) {
            if (references[i] != 0) {
                addEdge(node, references[i], label("static " + fieldName(names[i])));
            }
        }
    }

    @Override
    public void onInstanceData(long objectId, long classObjectId, byte[] data) {
        int node = addNode(objectId, ObjectGraph.Kind.INSTANCE, classObjectId, data.length);
        collectInstanceReferences(node, classObjectId, data);
    }

    @Override
    public void onObjectArrayElements(long arrayId, long arrayClassId, long[] elements) {
        int node = addNode(arrayId, ObjectGraph.Kind.OBJECT_ARRAY, arrayClassId, (long) elements.length * idSize);
        for (long element : elements) {
            if (element != 0) {
                addEdge(node, element, NO_LABEL);
            }
        }
    }

    @Override
    public void onPrimitiveArray(long arrayId, int elementType, int length) {
        long bytes = (long) length * PrimitiveTypes.size(elementType, idSize);
        addNode(arrayId, ObjectGraph.Kind.PRIMITIVE_ARRAY, -elementType, bytes);
    }

    /**
     * The data of an instance holds the fields of its own class first, then the fields of the
     * super class, and so on up the chain. Only reference fields (type 2) become edges.
     */
    private void collectInstanceReferences(int node, long classId, byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        long currentClass = classId;
        int depth = 0;
        while (currentClass != 0 && depth++ < 1000) {
            ClassDefinition definition = classes.definition(currentClass);
            if (definition == null) {
                return;
            }
            long[] names = definition.instanceFieldNameIds();
            int[] types = definition.instanceFieldTypes();
            for (int i = 0; i < types.length; i++) {
                int size = PrimitiveTypes.size(types[i], idSize);
                if (buffer.remaining() < size) {
                    return;
                }
                if (types[i] == 2) {
                    long reference = idSize == 4 ? buffer.getInt() & 0xFFFFFFFFL : buffer.getLong();
                    if (reference != 0) {
                        addEdge(node, reference, label(fieldName(names[i])));
                    }
                } else {
                    buffer.position(buffer.position() + size);
                }
            }
            currentClass = definition.superClassId();
        }
    }

    private String fieldName(long nameId) {
        String name = classes.string(nameId);
        return name != null ? name : "?";
    }

    private int label(String name) {
        return labelIds.computeIfAbsent(name, key -> {
            labels.add(key);
            return labels.size() - 1;
        });
    }

    private int addNode(long id, ObjectGraph.Kind kind, long classId, long bytes) {
        Integer existing = indexById.get(id);
        if (existing != null) {
            return existing;
        }
        if (ids.size() >= maxObjects) {
            throw new HeapTooLargeException(maxObjects);
        }
        int index = ids.size();
        indexById.put(id, index);
        ids.add(id);
        kinds.add(kind.ordinal());
        classIds.add(classId);
        shallow.add((int) Math.min(Integer.MAX_VALUE, bytes));
        return index;
    }

    private void addEdge(int source, long targetId, int labelId) {
        edgeSource.add(source);
        edgeTarget.add(targetId);
        edgeLabel.add(labelId);
    }

    /** Resolves the references to node numbers and packs the edges into the compact layout. */
    public ObjectGraph build() {
        int nodeCount = ids.size();
        int rawEdges = edgeSource.size();

        int[] resolved = new int[rawEdges];
        int[] offsets = new int[nodeCount + 1];
        int validEdges = 0;
        for (int e = 0; e < rawEdges; e++) {
            Integer target = indexById.get(edgeTarget.get(e));
            if (target == null) {
                resolved[e] = -1; // reference to an object that is not in the dump
                continue;
            }
            resolved[e] = target;
            offsets[edgeSource.get(e) + 1]++;
            validEdges++;
        }
        for (int i = 0; i < nodeCount; i++) {
            offsets[i + 1] += offsets[i];
        }

        int[] targets = new int[validEdges];
        int[] edgeLabels = new int[validEdges];
        int[] cursor = Arrays.copyOf(offsets, nodeCount);
        for (int e = 0; e < rawEdges; e++) {
            if (resolved[e] < 0) {
                continue;
            }
            int position = cursor[edgeSource.get(e)]++;
            targets[position] = resolved[e];
            edgeLabels[position] = edgeLabel.get(e);
        }

        byte[] kindArray = new byte[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            kindArray[i] = (byte) kinds.get(i);
        }

        RootKind[] rootKindValues = RootKind.values();
        Map<Integer, RootKind> rootMap = new LinkedHashMap<>();
        for (int r = 0; r < rootIds.size(); r++) {
            Integer node = indexById.get(rootIds.get(r));
            if (node != null) {
                rootMap.putIfAbsent(node, rootKindValues[rootKinds.get(r)]);
            }
        }

        return new ObjectGraph(
                ids.toArray(), kindArray, classIds.toArray(), shallow.toArray(),
                offsets, targets, edgeLabels, labels.toArray(new String[0]),
                indexById, rootMap);
    }

    private static final class IntList {
        private int[] data = new int[1024];
        private int size;

        void add(int value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, size * 2);
            }
            data[size++] = value;
        }

        int get(int index) {
            return data[index];
        }

        int size() {
            return size;
        }

        int[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }

    private static final class LongList {
        private long[] data = new long[1024];
        private int size;

        void add(long value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, size * 2);
            }
            data[size++] = value;
        }

        long get(int index) {
            return data[index];
        }

        int size() {
            return size;
        }

        long[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}