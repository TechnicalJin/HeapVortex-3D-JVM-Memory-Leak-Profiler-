package com.heapvortex.heap;

import com.heapvortex.exception.HeapDumpNotFoundException;
import com.heapvortex.exception.HeapTooLargeException;
import com.heapvortex.support.HprofTestWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HeapLoaderTest {

    @TempDir
    Path tempDir;

    private HeapDumpStorage storage;
    private HeapLoader loader;

    @BeforeEach
    void setUp() {
        storage = new HeapDumpStorage(tempDir.toString());
        loader = new HeapLoader(storage, 1000, 2);
    }

    private static byte[] dumpWithInstances(int count) {
        HprofTestWriter writer = new HprofTestWriter(8).string(1, "A").loadClass(100, 1).classDump(100, 0);
        for (int i = 0; i < count; i++) {
            writer.instance(10 + i, 100, new byte[8]);
        }
        return writer.toBytes();
    }

    private void write(String id, byte[] bytes) throws IOException {
        Files.write(tempDir.resolve(id + ".hprof"), bytes);
    }

    @Test
    void theSameFileIsLoadedOnlyOnce() throws IOException {
        write("a", dumpWithInstances(2));

        assertSame(loader.load("a"), loader.load("a"));
    }

    @Test
    void aChangedFileIsLoadedAgain() throws IOException {
        write("a", dumpWithInstances(2));
        LoadedHeap first = loader.load("a");

        Files.write(tempDir.resolve("a.hprof"), dumpWithInstances(5));

        assertNotSame(first, loader.load("a"));
    }

    @Test
    void theLeastRecentlyUsedGraphIsDroppedWhenTheCacheIsFull() throws IOException {
        write("a", dumpWithInstances(1));
        write("b", dumpWithInstances(2));
        write("c", dumpWithInstances(3));
        LoadedHeap first = loader.load("a");
        loader.load("b");
        loader.load("c");

        assertNotSame(first, loader.load("a"));
    }

    @Test
    void anUnknownDumpIsNotFound() {
        assertThrows(HeapDumpNotFoundException.class, () -> loader.load("missing"));
    }

    @Test
    void aDumpOverTheObjectLimitIsRejected() throws IOException {
        write("big", dumpWithInstances(50));
        HeapLoader small = new HeapLoader(storage, 10, 2);

        assertThrows(HeapTooLargeException.class, () -> small.load("big"));
    }
}