package com.heapvortex.heap;

import com.heapvortex.dto.GcRootPathDto;
import com.heapvortex.exception.ObjectNotFoundException;
import com.heapvortex.support.HprofTestWriter;
import com.heapvortex.support.SampleHeaps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GcRootPathTest {

    private static final String DUMP = "heap-1-20261022-100000";

    @TempDir
    Path tempDir;

    private HprofHeapGraphService service;

    private void load(byte[] bytes) throws IOException {
        Files.write(tempDir.resolve(DUMP + ".hprof"), bytes);
        service = new HprofHeapGraphService(
                new HeapLoader(new HeapDumpStorage(tempDir.toString()), 1000, 2));
    }

    /** Class Holder (a GC root) has a static REF to node 1. Node 1 -> node 2 -> node 3. */
    private static byte[] chain() {
        HprofTestWriter writer = new HprofTestWriter(8);
        return writer
                .string(1, "Holder").string(2, "Node").string(10, "next").string(13, "REF")
                .loadClass(100, 1).loadClass(200, 2)
                .classDump(200, 0, new long[]{10}, new int[]{2}, new long[0], new long[0])
                .classDump(100, 0, new long[0], new int[0], new long[]{13}, new long[]{1})
                .instance(1, 200, writer.refs(2))
                .instance(2, 200, writer.refs(3))
                .instance(3, 200, writer.refs(0))
                .root(RootKind.STICKY_CLASS, 100)
                .toBytes();
    }

    private static List<String> ids(GcRootPathDto path) {
        return path.path().stream().map(GcRootPathDto.PathNode::objectId).toList();
    }

    @Test
    void findsThePathFromTheClassThroughItsStaticField() throws IOException {
        load(chain());

        GcRootPathDto result = service.pathToGcRoot(DUMP, "0x3", 100);

        assertTrue(result.pathFound());
        assertEquals("STICKY_CLASS", result.rootKind());
        assertEquals("0x3", result.targetObjectId());
        assertEquals(List.of("0x64", "0x1", "0x2", "0x3"), ids(result));
        assertEquals("class Holder", result.path().get(0).typeName());
        assertNull(result.path().get(0).referenceFromPrevious());
        assertEquals("static REF", result.path().get(1).referenceFromPrevious());
        assertEquals("next", result.path().get(2).referenceFromPrevious());
        assertEquals("next", result.path().get(3).referenceFromPrevious());
    }

    @Test
    void maxDepthLimitsTheSearch() throws IOException {
        load(chain());

        assertFalse(service.pathToGcRoot(DUMP, "0x3", 2).pathFound());
        assertTrue(service.pathToGcRoot(DUMP, "0x3", 3).pathFound());
    }

    @Test
    void aRootObjectIsItsOwnPath() throws IOException {
        load(chain());

        GcRootPathDto result = service.pathToGcRoot(DUMP, "0x64", 10);

        assertTrue(result.pathFound());
        assertEquals(List.of("0x64"), ids(result));
    }

    @Test
    void cyclesDoNotLoopForever() throws IOException {
        HprofTestWriter writer = new HprofTestWriter(8);
        load(writer
                .string(1, "Holder").string(2, "Node").string(10, "next").string(13, "REF")
                .loadClass(100, 1).loadClass(200, 2)
                .classDump(200, 0, new long[]{10}, new int[]{2}, new long[0], new long[0])
                .classDump(100, 0, new long[0], new int[0], new long[]{13}, new long[]{1})
                .instance(1, 200, writer.refs(2))
                .instance(2, 200, writer.refs(1))
                .root(RootKind.STICKY_CLASS, 100)
                .toBytes());

        assertEquals(List.of("0x64", "0x1", "0x2"), ids(service.pathToGcRoot(DUMP, "0x2", 100)));
    }

    @Test
    void theShortestOfSeveralPathsIsUsed() throws IOException {
        load(SampleHeaps.holderAndChildren());

        // CHILD_2 is referenced by CHILD_1 (a root) and by an array. One hop is shorter.
        GcRootPathDto result = service.pathToGcRoot(DUMP, "0x3e9", 100);

        assertEquals("THREAD_OBJECT", result.rootKind());
        assertEquals(List.of("0x3e8", "0x3e9"), ids(result));
        assertEquals("next", result.path().get(1).referenceFromPrevious());
    }

    @Test
    void anObjectNobodyReferencesHasNoPath() throws IOException {
        load(SampleHeaps.holderAndChildren());

        GcRootPathDto result = service.pathToGcRoot(DUMP, "0xbb8", 100); // the byte array

        assertFalse(result.pathFound());
        assertNull(result.rootKind());
        assertTrue(result.path().isEmpty());
    }

    @Test
    void anUnknownObjectIsNotFound() throws IOException {
        load(SampleHeaps.holderAndChildren());

        assertThrows(ObjectNotFoundException.class, () -> service.pathToGcRoot(DUMP, "0x1", 100));
    }
}