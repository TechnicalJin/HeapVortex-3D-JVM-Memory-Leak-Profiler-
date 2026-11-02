package com.heapvortex.heap;

import com.heapvortex.dto.GraphStatsDto;
import com.heapvortex.dto.ObjectDetailsDto;
import com.heapvortex.dto.ObjectSummaryDto;
import com.heapvortex.exception.InvalidRequestException;
import com.heapvortex.exception.ObjectNotFoundException;
import com.heapvortex.support.SampleHeaps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HprofHeapGraphServiceTest {

    private static final String DUMP = "heap-1-20261022-100000";

    @TempDir
    Path tempDir;

    private HprofHeapGraphService service;

    @BeforeEach
    void setUp() throws IOException {
        Files.write(tempDir.resolve(DUMP + ".hprof"), SampleHeaps.holderAndChildren());
        HeapDumpStorage storage = new HeapDumpStorage(tempDir.toString());
        service = new HprofHeapGraphService(new HeapLoader(storage, 1000, 2));
    }

    @Test
    void statsCountNodesEdgesAndRoots() {
        GraphStatsDto stats = service.stats(DUMP);

        assertEquals(8, stats.objectCount());
        assertEquals(5, stats.edgeCount());
        assertEquals(2, stats.rootCount());
        assertEquals(3L, stats.objectsByKind().get("CLASS"));
        assertEquals(3L, stats.objectsByKind().get("INSTANCE"));
        assertEquals(1L, stats.objectsByKind().get("OBJECT_ARRAY"));
        assertEquals(1L, stats.objectsByKind().get("PRIMITIVE_ARRAY"));
    }

    @Test
    void instancesAreFoundByTypeName() {
        List<ObjectSummaryDto> children = service.instancesOf(DUMP, "Child", 10);

        assertEquals(List.of("0x3e8", "0x3e9"), children.stream().map(ObjectSummaryDto::objectId).toList());
        assertEquals(2, children.get(0).referenceCount());
        assertEquals(20, children.get(0).shallowBytes());
    }

    @Test
    void theLimitIsRespected() {
        assertEquals(1, service.instancesOf(DUMP, "Child", 1).size());
    }

    @Test
    void anUnknownClassHasNoInstances() {
        assertTrue(service.instancesOf(DUMP, "does.not.Exist", 10).isEmpty());
    }

    @Test
    void primitiveArraysAreFoundByTheirTypeName() {
        assertEquals(1, service.instancesOf(DUMP, "byte[]", 10).size());
    }

    @Test
    void classObjectsAreNotListedAsInstances() {
        assertTrue(service.instancesOf(DUMP, "java.lang.Class", 10).isEmpty());
    }

    @Test
    void objectDetailsListTheReferencesWithTheirFieldNames() {
        ObjectDetailsDto details = service.object(DUMP, "0x3e8", 10);

        assertEquals("INSTANCE", details.kind());
        assertEquals("Child", details.className());
        assertEquals(2, details.referenceCount());
        assertEquals("next", details.references().get(0).field());
        assertEquals("0x3e9", details.references().get(0).objectId());
        assertEquals("Child", details.references().get(0).className());
        assertEquals("baseRef", details.references().get(1).field());
        assertEquals("Base", details.references().get(1).className());
    }

    @Test
    void aClassObjectShowsItsStaticReferences() {
        ObjectDetailsDto details = service.object(DUMP, "0x64", 10);

        assertEquals("CLASS", details.kind());
        assertEquals("class Holder", details.className());
        assertEquals("static INSTANCE", details.references().get(0).field());
        assertEquals("0x3e8", details.references().get(0).objectId());
    }

    @Test
    void arrayElementsAreLabelledAsElements() {
        ObjectDetailsDto details = service.object(DUMP, "0x7d0", 10);

        assertEquals("OBJECT_ARRAY", details.kind());
        assertEquals("java.lang.Object[]", details.className());
        assertEquals(2, details.references().size());
        assertEquals("[element]", details.references().get(0).field());
    }

    @Test
    void maxReferencesCapsTheListButNotTheCount() {
        ObjectDetailsDto details = service.object(DUMP, "0x3e8", 1);

        assertEquals(1, details.references().size());
        assertEquals(2, details.referenceCount());
    }

    @Test
    void idsWorkWithoutThe0xPrefix() {
        assertEquals("0x3e8", service.object(DUMP, "3e8", 10).objectId());
    }

    @Test
    void anUnknownObjectIsNotFound() {
        assertThrows(ObjectNotFoundException.class, () -> service.object(DUMP, "0x1", 10));
    }

    @Test
    void anInvalidObjectIdIsRejected() {
        assertThrows(InvalidRequestException.class, () -> service.object(DUMP, "zzz", 10));
    }
}