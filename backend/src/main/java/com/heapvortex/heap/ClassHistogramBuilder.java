package com.heapvortex.heap;

import com.heapvortex.dto.ClassHistogramEntryDto;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts instances and data bytes per class.
 * Class names are resolved at the end, so it does not matter in which order the
 * name records and the objects appear in the file.
 * Note: all strings of the dump are kept in memory. That is fine for the dumps of
 * Week 2 (names of classes, fields and methods), but it is a limit for huge dumps.
 */
public final class ClassHistogramBuilder implements HprofVisitor {

    private final Map<Long, String> strings = new HashMap<>();
    private final Map<Long, Long> classNameIds = new HashMap<>();
    private final Map<Long, long[]> objectStats = new HashMap<>();
    private final Map<Integer, long[]> primitiveStats = new HashMap<>();
    private int idSize;

    @Override
    public void onHeader(String version, int idSize, long timestampMillis) {
        this.idSize = idSize;
    }

    @Override
    public void onString(long id, String value) {
        strings.put(id, value);
    }

    @Override
    public void onLoadClass(long classObjectId, long classNameStringId) {
        classNameIds.put(classObjectId, classNameStringId);
    }

    @Override
    public void onInstance(long objectId, long classObjectId, int dataLength) {
        add(objectStats, classObjectId, dataLength);
    }

    @Override
    public void onObjectArray(long arrayId, long arrayClassId, int length) {
        add(objectStats, arrayClassId, (long) length * idSize);
    }

    @Override
    public void onPrimitiveArray(long arrayId, int elementType, int length) {
        add(primitiveStats, elementType, (long) length * PrimitiveTypes.size(elementType, idSize));
    }

    /** All classes, biggest data size first. */
    public List<ClassHistogramEntryDto> build() {
        Map<String, long[]> byName = new HashMap<>();

        objectStats.forEach((classId, stats) ->
                merge(byName, ClassNameFormatter.readable(nameOf(classId)), stats));
        primitiveStats.forEach((type, stats) ->
                merge(byName, PrimitiveTypes.name(type) + "[]", stats));

        return byName.entrySet().stream()
                .map(entry -> new ClassHistogramEntryDto(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingLong(ClassHistogramEntryDto::dataBytes).reversed()
                        .thenComparing(ClassHistogramEntryDto::className))
                .toList();
    }

    private String nameOf(long classId) {
        Long nameId = classNameIds.get(classId);
        String name = nameId == null ? null : strings.get(nameId);
        return name != null ? name : "unknown-class-0x" + Long.toHexString(classId);
    }

    private static <K> void add(Map<K, long[]> map, K key, long bytes) {
        long[] stats = map.computeIfAbsent(key, k -> new long[2]);
        stats[0]++;
        stats[1] += bytes;
    }

    /** The same class name can exist more than once, for example in two class loaders. */
    private static void merge(Map<String, long[]> map, String name, long[] stats) {
        long[] total = map.computeIfAbsent(name, k -> new long[2]);
        total[0] += stats[0];
        total[1] += stats[1];
    }
}