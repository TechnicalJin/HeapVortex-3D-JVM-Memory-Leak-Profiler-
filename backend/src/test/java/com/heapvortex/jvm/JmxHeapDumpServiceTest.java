package com.heapvortex.jvm;

import com.heapvortex.dto.HeapDumpInfoDto;
import com.heapvortex.exception.JvmConnectionException;
import com.heapvortex.heap.HeapDumpStorage;
import com.heapvortex.support.ChildJvm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JmxHeapDumpServiceTest {

    @TempDir
    Path tempDir;

    private final AttachJvmDiscoveryService discovery = new AttachJvmDiscoveryService();
    private final JmxConnectionManager connectionManager = new JmxConnectionManager();

    private HeapDumpStorage storage;
    private JmxHeapDumpService service;
    private ChildJvm child;

    @BeforeEach
    void setUp() throws Exception {
        storage = new HeapDumpStorage(tempDir.toString());
        service = new JmxHeapDumpService(connectionManager, storage);
        child = ChildJvm.start(discovery);
    }

    @AfterEach
    void cleanUp() throws Exception {
        connectionManager.shutdown();
        child.stop();
    }

    @Test
    void createWritesAnHprofFileForTheChildJvm() {
        HeapDumpInfoDto info = service.create(child.pid(), true);

        assertEquals(child.pid(), info.pid());
        assertTrue(info.sizeBytes() > 0);
        assertTrue(Files.exists(storage.resolve(info.id())));
    }

    @Test
    void theFileStartsWithTheHprofHeader() throws Exception {
        HeapDumpInfoDto info = service.create(child.pid(), true);

        byte[] header;
        try (InputStream in = Files.newInputStream(storage.resolve(info.id()))) {
            header = in.readNBytes(16);
        }

        assertEquals("JAVA PROFILE 1.0", new String(header, StandardCharsets.US_ASCII));
    }

    @Test
    void liveAndNonLiveDumpsBothWork() {
        assertTrue(service.create(child.pid(), false).sizeBytes() > 0);
        assertTrue(service.create(child.pid(), true).sizeBytes() > 0);
        assertEquals(2, storage.list().size());
    }

    @Test
    void unknownPidFailsAndLeavesNoFileBehind() {
        assertThrows(JvmConnectionException.class, () -> service.create("999999", true));
        assertEquals(0, storage.list().size());
    }
}