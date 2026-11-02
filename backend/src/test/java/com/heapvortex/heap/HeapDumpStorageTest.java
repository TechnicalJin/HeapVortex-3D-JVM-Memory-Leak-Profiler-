package com.heapvortex.heap;

import com.heapvortex.dto.HeapDumpInfoDto;
import com.heapvortex.exception.HeapDumpNotFoundException;
import com.heapvortex.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeapDumpStorageTest {

    @TempDir
    Path tempDir;

    private HeapDumpStorage storage;

    @BeforeEach
    void setUp() {
        storage = new HeapDumpStorage(tempDir.toString());
    }

    private Path createDump(String name, int bytes) throws IOException {
        Path file = tempDir.resolve(name + ".hprof");
        Files.write(file, new byte[bytes]);
        return file;
    }

    @Test
    void newDumpPathIsInsideTheDirectoryAndNamedAfterThePid() {
        Path path = storage.newDumpPath("1234");

        assertEquals(storage.directory(), path.getParent());
        assertTrue(path.getFileName().toString().startsWith("heap-1234-"));
        assertTrue(path.getFileName().toString().endsWith(".hprof"));
    }

    @Test
    void newDumpPathNeverReturnsAnExistingFile() throws IOException {
        Path first = storage.newDumpPath("1234");
        Files.write(first, new byte[1]);

        Path second = storage.newDumpPath("1234");

        assertNotEquals(first, second);
    }

    @Test
    void newDumpPathRejectsInvalidPids() {
        assertThrows(InvalidRequestException.class, () -> storage.newDumpPath("12a"));
        assertThrows(InvalidRequestException.class, () -> storage.newDumpPath(""));
        assertThrows(InvalidRequestException.class, () -> storage.newDumpPath("../1"));
    }

    @Test
    void listIgnoresFilesThatAreNotHeapDumps() throws IOException {
        createDump("heap-1-20261022-100000", 10);
        Files.writeString(tempDir.resolve("notes.txt"), "hello");

        assertEquals(1, storage.list().size());
    }

    @Test
    void listIsSortedNewestFirst() throws IOException {
        Path older = createDump("heap-1-20261022-100000", 10);
        Path newer = createDump("heap-2-20261022-110000", 10);
        Files.setLastModifiedTime(older, FileTime.from(Instant.parse("2026-10-22T10:00:00Z")));
        Files.setLastModifiedTime(newer, FileTime.from(Instant.parse("2026-10-22T11:00:00Z")));

        List<HeapDumpInfoDto> dumps = storage.list();

        assertEquals("heap-2-20261022-110000", dumps.get(0).id());
        assertEquals("heap-1-20261022-100000", dumps.get(1).id());
    }

    @Test
    void describeReadsPidAndSizeFromOurFileName() throws IOException {
        createDump("heap-4242-20261022-100000", 2048);

        HeapDumpInfoDto info = storage.get("heap-4242-20261022-100000");

        assertEquals("4242", info.pid());
        assertEquals(2048, info.sizeBytes());
        assertEquals("heap-4242-20261022-100000.hprof", info.fileName());
    }

    @Test
    void externalFilesHaveAnUnknownPid() throws IOException {
        createDump("java_pid77", 5);

        assertEquals("unknown", storage.get("java_pid77").pid());
    }

    @Test
    void resolveRejectsIdsThatTryToLeaveTheDirectory() {
        assertThrows(InvalidRequestException.class, () -> storage.resolve("../secret"));
        assertThrows(InvalidRequestException.class, () -> storage.resolve("a/b"));
        assertThrows(InvalidRequestException.class, () -> storage.resolve("..%2Fsecret"));
        assertThrows(InvalidRequestException.class, () -> storage.resolve(""));
        assertThrows(InvalidRequestException.class, () -> storage.resolve(null));
    }

    @Test
    void resolveFailsForAnUnknownId() {
        assertThrows(HeapDumpNotFoundException.class, () -> storage.resolve("heap-9-20261022-100000"));
    }

    @Test
    void deleteRemovesTheFile() throws IOException {
        Path file = createDump("heap-1-20261022-100000", 10);

        storage.delete("heap-1-20261022-100000");

        assertFalse(Files.exists(file));
        assertThrows(HeapDumpNotFoundException.class, () -> storage.get("heap-1-20261022-100000"));
    }
}