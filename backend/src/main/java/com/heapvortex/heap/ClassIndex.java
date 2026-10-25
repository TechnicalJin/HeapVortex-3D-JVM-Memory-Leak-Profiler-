package com.heapvortex.heap;

import java.util.HashMap;
import java.util.Map;

/** Pass 1: knows every class, its fields and its name. */
public final class ClassIndex implements HprofVisitor {

    private final Map<Long, String> strings = new HashMap<>();
    private final Map<Long, Long> classNameIds = new HashMap<>();
    private final Map<Long, ClassDefinition> definitions = new HashMap<>();
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
    public void onClassDefinition(ClassDefinition definition) {
        definitions.put(definition.classId(), definition);
    }

    public int idSize() {
        return idSize;
    }

    public ClassDefinition definition(long classId) {
        return definitions.get(classId);
    }

    public String string(long stringId) {
        return strings.get(stringId);
    }

    /** Readable class name, or a placeholder if the class is unknown. */
    public String className(long classId) {
        Long nameId = classNameIds.get(classId);
        String name = nameId == null ? null : strings.get(nameId);
        return name != null
                ? ClassNameFormatter.readable(name)
                : "unknown-class-0x" + Long.toHexString(classId);
    }
}