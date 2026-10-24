package com.heapvortex.heap;

import com.heapvortex.dto.HeapSummaryDto;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Counts classes, objects, arrays and GC roots. */
public final class HeapSummaryBuilder implements HprofVisitor {

    private String version = "unknown";
    private int idSize;
    private long timestampMillis;
    private long classCount;
    private long instanceCount;
    private long objectArrayCount;
    private long primitiveArrayCount;
    private final Map<RootKind, Long> roots = new EnumMap<>(RootKind.class);

    @Override
    public void onHeader(String version, int idSize, long timestampMillis) {
        this.version = version;
        this.idSize = idSize;
        this.timestampMillis = timestampMillis;
    }

    @Override
    public void onRoot(RootKind kind, long objectId) {
        roots.merge(kind, 1L, Long::sum);
    }

    @Override
    public void onClassDump(long classObjectId, long superClassId, int instanceSize) {
        classCount++;
    }

    @Override
    public void onInstance(long objectId, long classObjectId, int dataLength) {
        instanceCount++;
    }

    @Override
    public void onObjectArray(long arrayId, long arrayClassId, int length) {
        objectArrayCount++;
    }

    @Override
    public void onPrimitiveArray(long arrayId, int elementType, int length) {
        primitiveArrayCount++;
    }

    public HeapSummaryDto build() {
        // every kind is listed, also with 0, so the frontend gets a stable shape
        Map<String, Long> rootsByKind = new LinkedHashMap<>();
        for (RootKind kind : RootKind.values()) {
            rootsByKind.put(kind.name(), roots.getOrDefault(kind, 0L));
        }
        return new HeapSummaryDto(
                version,
                idSize,
                Instant.ofEpochMilli(timestampMillis),
                classCount,
                instanceCount,
                objectArrayCount,
                primitiveArrayCount,
                rootsByKind);
    }
}