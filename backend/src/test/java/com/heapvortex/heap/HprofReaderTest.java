package com.heapvortex.heap;

import com.heapvortex.exception.HprofFormatException;
import com.heapvortex.support.HprofTestWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HprofReaderTest {

    /** Remembers everything the reader reports. */
    private static final class Recorder implements HprofVisitor {
        String version;
        int idSize;
        long timestamp;
        final Map<Long, String> strings = new LinkedHashMap<>();
        final Map<Long, Long> classes = new LinkedHashMap<>();
        final List<RootKind> roots = new ArrayList<>();
        int classDumps;
        int instances;
        long instanceBytes;
        int objectArrays;
        int primitiveArrays;

        @Override
        public void onHeader(String version, int idSize, long timestampMillis) {
            this.version = version;
            this.idSize = idSize;
            this.timestamp = timestampMillis;
        }

        @Override
        public void onString(long id, String value) {
            strings.put(id, value);
        }

        @Override
        public void onLoadClass(long classObjectId, long classNameStringId) {
            classes.put(classObjectId, classNameStringId);
        }

        @Override
        public void onRoot(RootKind kind, long objectId) {
            roots.add(kind);
        }

        @Override
        public void onClassDump(long classObjectId, long superClassId, int instanceSize) {
            classDumps++;
        }

        @Override
        public void onInstance(long objectId, long classObjectId, int dataLength) {
            instances++;
            instanceBytes += dataLength;
        }

        @Override
        public void onObjectArray(long arrayId, long arrayClassId, int length) {
            objectArrays++;
        }

        @Override
        public void onPrimitiveArray(long arrayId, int elementType, int length) {
            primitiveArrays++;
        }
    }

    private static Recorder read(byte[] bytes) throws IOException {
        Recorder recorder = new Recorder();
        HprofReader.read(new ByteArrayInputStream(bytes), recorder);
        return recorder;
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 8})
    void readsTheHeader(int idSize) throws IOException {
        Recorder recorder = read(new HprofTestWriter(idSize).toBytes());

        assertTrue(recorder.version.startsWith("JAVA PROFILE 1.0"));
        assertEquals(idSize, recorder.idSize);
        assertEquals(1_700_000_000_000L, recorder.timestamp);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 8})
    void readsStringsAndLoadedClasses(int idSize) throws IOException {
        byte[] bytes = new HprofTestWriter(idSize)
                .string(1, "java/lang/String")
                .string(2, "com/example/Foo")
                .loadClass(100, 1)
                .loadClass(101, 2)
                .toBytes();

        Recorder recorder = read(bytes);

        assertEquals("java/lang/String", recorder.strings.get(1L));
        assertEquals("com/example/Foo", recorder.strings.get(2L));
        assertEquals(1L, recorder.classes.get(100L));
        assertEquals(2L, recorder.classes.get(101L));
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 8})
    void readsEveryKindOfHeapSubRecord(int idSize) throws IOException {
        byte[] bytes = new HprofTestWriter(idSize)
                .classDump(100, 0)
                .instance(200, 100, new byte[8])
                .instance(201, 100, new byte[4])
                .objectArray(300, 100, 0, 0, 0)
                .primitiveArray(400, 10, 5)
                .root(RootKind.STICKY_CLASS, 100)
                .root(RootKind.THREAD_OBJECT, 200)
                .root(RootKind.JNI_GLOBAL, 200)
                .root(RootKind.JAVA_FRAME, 200)
                .toBytes();

        Recorder recorder = read(bytes);

        assertEquals(1, recorder.classDumps);
        assertEquals(2, recorder.instances);
        assertEquals(12, recorder.instanceBytes);
        assertEquals(1, recorder.objectArrays);
        assertEquals(1, recorder.primitiveArrays);
        assertEquals(
                List.of(RootKind.STICKY_CLASS, RootKind.THREAD_OBJECT, RootKind.JNI_GLOBAL, RootKind.JAVA_FRAME),
                recorder.roots);
    }

    @Test
    void fourByteIdsAreReadAsUnsignedNumbers() throws IOException {
        byte[] bytes = new HprofTestWriter(4).string(0xF0000001L, "big").toBytes();

        assertEquals("big", read(bytes).strings.get(0xF0000001L));
    }

    @Test
    void eightByteIdsKeepAllTheirBits() throws IOException {
        long id = 0x7F00_0000_1234_5678L;
        byte[] bytes = new HprofTestWriter(8).string(id, "big").toBytes();

        assertEquals("big", read(bytes).strings.get(id));
    }

    @Test
    void skipsTopLevelRecordsItDoesNotKnow() throws IOException {
        byte[] bytes = new HprofTestWriter(8)
                .unknownRecord(0x05, 20)
                .string(1, "after")
                .toBytes();

        assertEquals("after", read(bytes).strings.get(1L));
    }

    @Test
    void aDumpWithoutRecordsIsValid() throws IOException {
        Recorder recorder = read(new HprofTestWriter(8).toBytes());

        assertTrue(recorder.strings.isEmpty());
        assertEquals(0, recorder.instances);
    }

    @Test
    void rejectsFilesThatAreNotHprof() {
        byte[] notHprof = "definitely not a heap dump".getBytes();
        byte[] withNul = Arrays.copyOf(notHprof, notHprof.length + 1);

        HprofFormatException exception = assertThrows(HprofFormatException.class, () -> read(withNul));

        assertTrue(exception.getMessage().contains("Not an HPROF"));
    }

    @Test
    void rejectsEmptyFiles() {
        assertThrows(HprofFormatException.class, () -> read(new byte[0]));
    }

    @Test
    void rejectsTruncatedFiles() {
        byte[] complete = new HprofTestWriter(8)
                .classDump(100, 0)
                .instance(200, 100, new byte[16])
                .toBytes();
        byte[] truncated = Arrays.copyOf(complete, complete.length - 3);

        assertThrows(HprofFormatException.class, () -> read(truncated));
    }

    @Test
    void rejectsAnUnsupportedIdentifierSize() {
        HprofFormatException exception = assertThrows(
                HprofFormatException.class, () -> read(new HprofTestWriter(3).toBytes()));

        assertTrue(exception.getMessage().contains("identifier size"));
    }

    @Test
    void rejectsAnUnknownHeapSubRecord() {
        byte[] bytes = new HprofTestWriter(8).rawSubRecord(0x99).toBytes();

        HprofFormatException exception = assertThrows(HprofFormatException.class, () -> read(bytes));

        assertTrue(exception.getMessage().contains("0x99"));
    }
}