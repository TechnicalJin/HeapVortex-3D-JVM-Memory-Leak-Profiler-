package com.heapvortex.support;

import com.heapvortex.heap.RootKind;

import java.nio.ByteBuffer;

public final class SampleHeaps {

    public static final long HOLDER_CLASS = 100;
    public static final long CHILD_CLASS = 200;
    public static final long BASE_CLASS = 300;
    public static final long ARRAY_CLASS = 400;
    public static final long CHILD_1 = 1000;
    public static final long CHILD_2 = 1001;
    public static final long BASE_1 = 1002;
    public static final long OBJECT_ARRAY = 2000;
    public static final long BYTE_ARRAY = 3000;

    private SampleHeaps() {
    }

    /**
     * Holder has a static field INSTANCE that points to CHILD_1. Child extends Base.
     * CHILD_1 refers to CHILD_2 (field next) and BASE_1 (field baseRef, declared in Base).
     * An object array holds CHILD_1, CHILD_2, null and an object that is not in the dump.
     * Roots: STICKY_CLASS on Holder (listed twice) and THREAD_OBJECT on CHILD_1.
     */
    public static byte[] holderAndChildren() {
        return new HprofTestWriter(8)
                .string(1, "Holder")
                .string(2, "Child")
                .string(3, "Base")
                .string(5, "[Ljava/lang/Object;")
                .string(10, "next")
                .string(11, "size")
                .string(12, "baseRef")
                .string(13, "INSTANCE")
                .loadClass(HOLDER_CLASS, 1)
                .loadClass(CHILD_CLASS, 2)
                .loadClass(BASE_CLASS, 3)
                .loadClass(ARRAY_CLASS, 5)
                .classDump(BASE_CLASS, 0, new long[]{12}, new int[]{2}, new long[0], new long[0])
                .classDump(CHILD_CLASS, BASE_CLASS, new long[]{10, 11}, new int[]{2, 10}, new long[0], new long[0])
                .classDump(HOLDER_CLASS, 0, new long[0], new int[0], new long[]{13}, new long[]{CHILD_1})
                .instance(CHILD_1, CHILD_CLASS, childData(CHILD_2, 7, BASE_1))
                .instance(CHILD_2, CHILD_CLASS, childData(0, 0, 0))
                .instance(BASE_1, BASE_CLASS, new byte[8])
                .objectArray(OBJECT_ARRAY, ARRAY_CLASS, CHILD_1, CHILD_2, 0, 99_999)
                .primitiveArray(BYTE_ARRAY, 8, 4)
                .root(RootKind.STICKY_CLASS, HOLDER_CLASS)
                .root(RootKind.THREAD_OBJECT, CHILD_1)
                .root(RootKind.STICKY_CLASS, HOLDER_CLASS)
                .toBytes();
    }

    /** Field values of a Child: next (8 bytes), size (4 bytes), then baseRef of the super class (8 bytes). */
    private static byte[] childData(long next, int size, long baseRef) {
        return ByteBuffer.allocate(8 + 4 + 8).putLong(next).putInt(size).putLong(baseRef).array();
    }
}