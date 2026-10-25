package com.heapvortex.support;

import com.heapvortex.heap.PrimitiveTypes;
import com.heapvortex.heap.RootKind;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Builds small, valid HPROF files in memory for tests. */
public final class HprofTestWriter {

    private final int idSize;
    private final ByteArrayOutputStream file = new ByteArrayOutputStream();
    private final ByteArrayOutputStream heap = new ByteArrayOutputStream();

    public HprofTestWriter(int idSize) {
        this.idSize = idSize;
        file.writeBytes("JAVA PROFILE 1.0.2".getBytes(StandardCharsets.US_ASCII));
        file.write(0);
        file.writeBytes(u4(idSize));
        file.writeBytes(u8(1_700_000_000_000L));
    }

    public HprofTestWriter string(long id, String text) {
        record(0x01, concat(id(id), text.getBytes(StandardCharsets.UTF_8)));
        return this;
    }

    public HprofTestWriter loadClass(long classId, long nameId) {
        record(0x02, concat(u4(1), id(classId), u4(0), id(nameId)));
        return this;
    }

    /** A class without fields. */
    public HprofTestWriter classDump(long classId, long superClassId) {
        return classDump(classId, superClassId, new long[0], new int[0], new long[0], new long[0]);
    }

    /**
     * A class with instance fields (name id and type code each, type 2 is a reference,
     * 10 is an int) and static fields that all hold references.
     */
    public HprofTestWriter classDump(long classId, long superClassId,
                                     long[] fieldNameIds, int[] fieldTypes,
                                     long[] staticNameIds, long[] staticRefs) {
        heap.write(0x20);
        heap.writeBytes(id(classId));
        heap.writeBytes(u4(0));
        heap.writeBytes(id(superClassId));
        for (int i = 0; i < 5; i++) {
            heap.writeBytes(id(0));
        }
        heap.writeBytes(u4(0));
        heap.writeBytes(u2(0));
        heap.writeBytes(u2(staticNameIds.length));
        for (int i = 0; i < staticNameIds.length; i++) {
            heap.writeBytes(id(staticNameIds[i]));
            heap.write(2);
            heap.writeBytes(id(staticRefs[i]));
        }
        heap.writeBytes(u2(fieldNameIds.length));
        for (int i = 0; i < fieldNameIds.length; i++) {
            heap.writeBytes(id(fieldNameIds[i]));
            heap.write(fieldTypes[i]);
        }
        return this;
    }

    public HprofTestWriter instance(long objectId, long classId, byte[] data) {
        heap.write(0x21);
        heap.writeBytes(id(objectId));
        heap.writeBytes(u4(0));
        heap.writeBytes(id(classId));
        heap.writeBytes(u4(data.length));
        heap.writeBytes(data);
        return this;
    }

    public HprofTestWriter objectArray(long arrayId, long arrayClassId, long... elements) {
        heap.write(0x22);
        heap.writeBytes(id(arrayId));
        heap.writeBytes(u4(0));
        heap.writeBytes(u4(elements.length));
        heap.writeBytes(id(arrayClassId));
        for (long element : elements) {
            heap.writeBytes(id(element));
        }
        return this;
    }

    public HprofTestWriter primitiveArray(long arrayId, int type, int length) {
        heap.write(0x23);
        heap.writeBytes(id(arrayId));
        heap.writeBytes(u4(0));
        heap.writeBytes(u4(length));
        heap.write(type);
        heap.writeBytes(new byte[length * PrimitiveTypes.size(type, idSize)]);
        return this;
    }

    public HprofTestWriter root(RootKind kind, long objectId) {
        int tag = switch (kind) {
            case UNKNOWN -> 0xFF;
            case JNI_GLOBAL -> 0x01;
            case JNI_LOCAL -> 0x02;
            case JAVA_FRAME -> 0x03;
            case NATIVE_STACK -> 0x04;
            case STICKY_CLASS -> 0x05;
            case THREAD_BLOCK -> 0x06;
            case MONITOR_USED -> 0x07;
            case THREAD_OBJECT -> 0x08;
        };
        int extraBytes = switch (kind) {
            case JNI_GLOBAL -> idSize;
            case JNI_LOCAL, JAVA_FRAME, THREAD_OBJECT -> 8;
            case NATIVE_STACK, THREAD_BLOCK -> 4;
            default -> 0;
        };
        heap.write(tag);
        heap.writeBytes(id(objectId));
        heap.writeBytes(new byte[extraBytes]);
        return this;
    }

    /** A heap sub-record with a tag that does not exist, to test error handling. */
    public HprofTestWriter rawSubRecord(int tag) {
        heap.write(tag);
        return this;
    }

    /** A top-level record with a tag the reader does not know, to test skipping. */
    public HprofTestWriter unknownRecord(int tag, int length) {
        record(tag, new byte[length]);
        return this;
    }

    /** Encodes ids one after the other, which is how reference fields are stored in instance data. */
    public byte[] refs(long... ids) {
        byte[] result = new byte[0];
        for (long value : ids) {
            result = concat(result, id(value));
        }
        return result;
    }

    public byte[] toBytes() {
        if (heap.size() > 0) {
            record(0x1C, heap.toByteArray());
            heap.reset();
        }
        return file.toByteArray();
    }

    private void record(int tag, byte[] body) {
        file.write(tag);
        file.writeBytes(u4(0));
        file.writeBytes(u4(body.length));
        file.writeBytes(body);
    }

    private byte[] id(long value) {
        return idSize == 4 ? u4((int) value) : u8(value);
    }

    private static byte[] u2(int value) {
        return ByteBuffer.allocate(2).putShort((short) value).array();
    }

    private static byte[] u4(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    private static byte[] u8(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}