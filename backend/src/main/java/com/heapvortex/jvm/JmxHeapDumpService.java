package com.heapvortex.jvm;

import com.heapvortex.dto.HeapDumpInfoDto;
import com.heapvortex.exception.HeapDumpException;
import com.heapvortex.heap.HeapDumpStorage;
import com.heapvortex.service.HeapDumpService;
import com.sun.management.HotSpotDiagnosticMXBean;
import org.springframework.stereotype.Service;

import javax.management.MBeanServerConnection;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class JmxHeapDumpService implements HeapDumpService {

    private static final String HOTSPOT_DIAGNOSTIC = "com.sun.management:type=HotSpotDiagnostic";

    private final JmxConnectionManager connectionManager;
    private final HeapDumpStorage storage;

    public JmxHeapDumpService(JmxConnectionManager connectionManager, HeapDumpStorage storage) {
        this.connectionManager = connectionManager;
        this.storage = storage;
    }

    @Override
    public HeapDumpInfoDto create(String pid, boolean live) {
        Path file = storage.newDumpPath(pid);
        try {
            MBeanServerConnection server = connectionManager.getConnection(pid).getMBeanServerConnection();
            HotSpotDiagnosticMXBean diagnostic = ManagementFactory.newPlatformMXBeanProxy(
                    server, HOTSPOT_DIAGNOSTIC, HotSpotDiagnosticMXBean.class);

            // the target JVM writes the file itself, it must be able to write to this folder
            diagnostic.dumpHeap(file.toString(), live);

            return storage.describe(file);

        } catch (IOException | UndeclaredThrowableException e) {
            connectionManager.evict(pid);
            deleteQuietly(file);
            throw new HeapDumpException("Heap dump failed for JVM " + pid + ": " + e.getMessage(), e);
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // a half-written file is not a problem worth failing on
        }
    }
}