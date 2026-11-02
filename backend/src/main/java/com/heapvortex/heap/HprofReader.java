package com.heapvortex.heap;

import com.heapvortex.exception.HprofFormatException;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Streaming reader for the HPROF heap dump format. */
public final class HprofReader {

    private static final int TAG_UTF8 = 0x01;
    private static final int TAG_LOAD_CLASS = 0x02;
    private static final int TAG_HEAP_DUMP = 0x0C;
    private static final int TAG_HEAP_DUMP_SEGMENT = 0x1C;

    private static final int ROOT_UNKNOWN = 0xFF;
    private static final int ROOT_JNI_GLOBAL = 0x01;
    private static final int ROOT_JNI_LOCAL = 0x02;
    private static final int ROOT_JAVA_FRAME = 0x03;
    private static final int ROOT_NATIVE_STACK = 0x04;
    private static final int ROOT_STICKY_CLASS = 0x05;
    private static final int ROOT_THREAD_BLOCK = 0x06;
    private static final int ROOT_MONITOR_USED = 0x07;
    private static final int ROOT_THREAD_OBJECT = 0x08;
    private static final int CLASS_DUMP = 0x20;
    private static final int INSTANCE_DUMP = 0x21;
    private static final int OBJECT_ARRAY_DUMP = 0x22;
    private static final int PRIMITIVE_ARRAY_DUMP = 0x23;

    private static final int MAX_STRING_BYTES = 16 * 1024 * 1024;

    private static final int MAX_ARRAY_ELEMENTS = 10_000_000;

    private final Input in;
    private final HprofVisitor visitor;
    private int idSize;

    private HprofReader(InputStream stream, HprofVisitor visitor) {
        this.in = new Input(stream);
        this.visitor = visitor;
    }

    public static void read(Path file, HprofVisitor visitor) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            read(stream, visitor);
        }
    }

    public static void read(InputStream stream, HprofVisitor visitor) throws IOException {
        try {
            new HprofReader(stream, visitor).readAll();
        } catch (EOFException e) {
            throw new HprofFormatException("Heap dump is truncated or incomplete", e);
        }
    }

    private void readAll() throws IOException {
        String version = in.readNullTerminated(64);
        if (!version.startsWith("JAVA PROFILE 1.0")) {
            throw new HprofFormatException("Not an HPROF file (header: " + version + ")");
        }
        idSize = in.readInt();
        if (idSize != 4 && idSize != 8) {
            throw new HprofFormatException("Unsupported identifier size: " + idSize);
        }
        long timestamp = in.readLong();
        visitor.onHeader(version, idSize, timestamp);

        while (true) {
            int tag = in.readTagOrEnd();
            if (tag < 0) {
                return;
            }
            in.readInt(); // time offset, not needed
            long length = in.readUnsignedInt();
            switch (tag) {
                case TAG_UTF8 -> readUtf8(length);
                case TAG_LOAD_CLASS -> readLoadClass(length);
                case TAG_HEAP_DUMP, TAG_HEAP_DUMP_SEGMENT -> readHeapSegment(length);
                default -> in.skip(length);
            }
        }
    }

    private void readUtf8(long length) throws IOException {
        long id = readId();
        long textBytes = length - idSize;
        if (textBytes < 0) {
            throw new HprofFormatException("Invalid string record");
        }
        if (textBytes > MAX_STRING_BYTES) {
            in.skip(textBytes);
            return;
        }
        byte[] bytes = in.readBytes((int) textBytes);
        visitor.onString(id, new String(bytes, StandardCharsets.UTF_8));
    }

    private void readLoadClass(long length) throws IOException {
        long start = in.position();
        in.skip(4); // class serial number
        long classObjectId = readId();
        in.skip(4); // stack trace serial number
        long nameStringId = readId();
        visitor.onLoadClass(classObjectId, nameStringId);
        skipRest(start, length);
    }

    private void skipRest(long start, long length) throws IOException {
        long remaining = length - (in.position() - start);
        if (remaining > 0) {
            in.skip(remaining);
        }
    }

    private void readHeapSegment(long length) throws IOException {
        long end = in.position() + length;
        while (in.position() < end) {
            int subTag = in.readU1();
            switch (subTag) {
                case ROOT_UNKNOWN -> readRoot(RootKind.UNKNOWN, 0);
                case ROOT_JNI_GLOBAL -> readRoot(RootKind.JNI_GLOBAL, idSize);
                case ROOT_JNI_LOCAL -> readRoot(RootKind.JNI_LOCAL, 8);
                case ROOT_JAVA_FRAME -> readRoot(RootKind.JAVA_FRAME, 8);
                case ROOT_NATIVE_STACK -> readRoot(RootKind.NATIVE_STACK, 4);
                case ROOT_STICKY_CLASS -> readRoot(RootKind.STICKY_CLASS, 0);
                case ROOT_THREAD_BLOCK -> readRoot(RootKind.THREAD_BLOCK, 4);
                case ROOT_MONITOR_USED -> readRoot(RootKind.MONITOR_USED, 0);
                case ROOT_THREAD_OBJECT -> readRoot(RootKind.THREAD_OBJECT, 8);
                case CLASS_DUMP -> readClassDump();
                case INSTANCE_DUMP -> readInstanceDump();
                case OBJECT_ARRAY_DUMP -> readObjectArrayDump();
                case PRIMITIVE_ARRAY_DUMP -> readPrimitiveArrayDump();
                default -> throw new HprofFormatException(
                        "Unknown heap dump sub-record 0x" + Integer.toHexString(subTag));
            }
        }
        if (in.position() != end) {
            throw new HprofFormatException("A heap dump record is longer than its declared length");
        }
    }

    /** A root is an object id followed by a few extra bytes that we do not need. */
    private void readRoot(RootKind kind, int extraBytes) throws IOException {
        long objectId = readId();
        in.skip(extraBytes);
        visitor.onRoot(kind, objectId);
    }

    private void readClassDump() throws IOException {
        long classId = readId();
        in.skip(4); // stack trace serial number
        long superClassId = readId();
        in.skip(5L * idSize); // class loader, signers, protection domain, two reserved
        int instanceSize = in.readInt();

        int constantPoolSize = in.readU2();
        for (int i = 0; i < constantPoolSize; i++) {
            in.skip(2); // constant pool index
            int type = in.readU1();
            in.skip(PrimitiveTypes.size(type, idSize));
        }

        int staticCount = in.readU2();
        long[] staticNames = new long[staticCount];
        long[] staticReferences = new long[staticCount];
        int referenceStatics = 0;
        for (int i = 0; i < staticCount; i++) {
            long nameId = readId();
            int type = in.readU1();
            if (type == 2) {
                staticNames[referenceStatics] = nameId;
                staticReferences[referenceStatics] = readId();
                referenceStatics++;
            } else {
                in.skip(PrimitiveTypes.size(type, idSize));
            }
        }

        int fieldCount = in.readU2();
        long[] fieldNames = new long[fieldCount];
        int[] fieldTypes = new int[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
            fieldNames[i] = readId();
            fieldTypes[i] = in.readU1();
        }

        visitor.onClassDump(classId, superClassId, instanceSize);
        visitor.onClassDefinition(new ClassDefinition(
                classId, superClassId, fieldNames, fieldTypes,
                Arrays.copyOf(staticNames, referenceStatics),
                Arrays.copyOf(staticReferences, referenceStatics)));
    }

    private void readInstanceDump() throws IOException {
        long objectId = readId();
        in.skip(4); // stack trace serial number
        long classId = readId();
        long dataLength = in.readUnsignedInt();
        if (dataLength > Integer.MAX_VALUE) {
            throw new HprofFormatException("Instance data is too large");
        }
        visitor.onInstance(objectId, classId, (int) dataLength);
        if (visitor.wantsDetails()) {
            visitor.onInstanceData(objectId, classId, in.readBytes((int) dataLength));
        } else {
            in.skip(dataLength);
        }
    }

    private void readObjectArrayDump() throws IOException {
        long arrayId = readId();
        in.skip(4); // stack trace serial number
        int length = in.readInt();
        long arrayClassId = readId();
        if (length < 0) {
            throw new HprofFormatException("Negative array length");
        }
        visitor.onObjectArray(arrayId, arrayClassId, length);
        if (visitor.wantsDetails() && length <= MAX_ARRAY_ELEMENTS) {
            long[] elements = new long[length];
            for (int i = 0; i < length; i++) {
                elements[i] = readId();
            }
            visitor.onObjectArrayElements(arrayId, arrayClassId, elements);
        } else {
            in.skip((long) length * idSize);
        }
    }

    private void readPrimitiveArrayDump() throws IOException {
        long arrayId = readId();
        in.skip(4); // stack trace serial number
        int length = in.readInt();
        int type = in.readU1();
        if (length < 0) {
            throw new HprofFormatException("Negative array length");
        }
        in.skip((long) length * PrimitiveTypes.size(type, idSize));
        visitor.onPrimitiveArray(arrayId, type, length);
    }

    /** Identifiers are 4 or 8 bytes. Four-byte ids are read as unsigned numbers. */
    private long readId() throws IOException {
        return idSize == 4 ? in.readUnsignedInt() : in.readLong();
    }

    /** Wraps the stream and counts every byte that is consumed. */
    private static final class Input {

        private final DataInputStream in;
        private long position;

        Input(InputStream stream) {
            this.in = new DataInputStream(new BufferedInputStream(stream, 1 << 20));
        }

        long position() {
            return position;
        }

        /** Reads the next record tag, or returns -1 at the clean end of the file. */
        int readTagOrEnd() throws IOException {
            int b = in.read();
            if (b >= 0) {
                position++;
            }
            return b;
        }

        int readU1() throws IOException {
            int value = in.readUnsignedByte();
            position += 1;
            return value;
        }

        int readU2() throws IOException {
            int value = in.readUnsignedShort();
            position += 2;
            return value;
        }

        int readInt() throws IOException {
            int value = in.readInt();
            position += 4;
            return value;
        }

        long readUnsignedInt() throws IOException {
            return readInt() & 0xFFFFFFFFL;
        }

        long readLong() throws IOException {
            long value = in.readLong();
            position += 8;
            return value;
        }

        byte[] readBytes(int count) throws IOException {
            byte[] bytes = new byte[count];
            in.readFully(bytes);
            position += count;
            return bytes;
        }

        void skip(long count) throws IOException {
            if (count < 0) {
                throw new HprofFormatException("Negative skip length");
            }
            in.skipNBytes(count);
            position += count;
        }

        String readNullTerminated(int maxLength) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (true) {
                int b = in.read();
                if (b < 0) {
                    throw new EOFException();
                }
                position++;
                if (b == 0) {
                    return bytes.toString(StandardCharsets.US_ASCII);
                }
                if (bytes.size() >= maxLength) {
                    throw new HprofFormatException("Not an HPROF file (header too long)");
                }
                bytes.write(b);
            }
        }
    }
}