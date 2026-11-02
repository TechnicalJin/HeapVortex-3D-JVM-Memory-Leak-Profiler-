package com.heapvortex.heap;

/** Receives the things the reader finds in a heap dump. Override only what you need. */
public interface HprofVisitor {

    default void onHeader(String version, int idSize, long timestampMillis) {
    }

    default void onString(long id, String value) {
    }

    default void onLoadClass(long classObjectId, long classNameStringId) {
    }

    default void onRoot(RootKind kind, long objectId) {
    }

    default void onClassDump(long classObjectId, long superClassId, int instanceSize) {
    }

    default void onInstance(long objectId, long classObjectId, int dataLength) {
    }

    default void onObjectArray(long arrayId, long arrayClassId, int length) {
    }

    default void onPrimitiveArray(long arrayId, int elementType, int length) {
    }

    default boolean wantsDetails() {
        return false;
    }

    default void onClassDefinition(ClassDefinition definition) {
    }

    default void onInstanceData(long objectId, long classObjectId, byte[] data) {
    }

    default void onObjectArrayElements(long arrayId, long arrayClassId, long[] elements) {
    }
}