package com.heapvortex.jvm;

import com.heapvortex.dto.GcMetricsDto;
import com.heapvortex.dto.JvmMetricsDto;
import com.heapvortex.dto.MemoryMetricsDto;
import com.heapvortex.dto.ThreadMetricsDto;
import com.heapvortex.exception.JvmConnectionException;
import com.heapvortex.service.JvmTelemetryService;
import org.springframework.stereotype.Service;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class JmxJvmTelemetryService implements JvmTelemetryService {

    private final JmxConnectionManager connectionManager;

    public JmxJvmTelemetryService(JmxConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public JvmMetricsDto collect(String pid) {
        try {
            MBeanServerConnection server = connectionManager.getConnection(pid).getMBeanServerConnection();

            MemoryMXBean memoryBean = ManagementFactory.newPlatformMXBeanProxy(
                    server, ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class);
            ThreadMXBean threadBean = ManagementFactory.newPlatformMXBeanProxy(
                    server, ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);

            MemoryUsage heap = memoryBean.getHeapMemoryUsage();
            MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();

            MemoryMetricsDto memory = new MemoryMetricsDto(
                    heap.getUsed(), heap.getCommitted(), heap.getMax(),
                    nonHeap.getUsed(), nonHeap.getCommitted());

            ThreadMetricsDto threads = new ThreadMetricsDto(
                    threadBean.getThreadCount(),
                    threadBean.getPeakThreadCount(),
                    threadBean.getDaemonThreadCount(),
                    threadBean.getTotalStartedThreadCount());

            List<GcMetricsDto> gc = new ArrayList<>();
            ObjectName pattern = new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*");
            for (ObjectName name : server.queryNames(pattern, null)) {
                GarbageCollectorMXBean gcBean = ManagementFactory.newPlatformMXBeanProxy(
                        server, name.getCanonicalName(), GarbageCollectorMXBean.class);
                gc.add(new GcMetricsDto(gcBean.getName(), gcBean.getCollectionCount(), gcBean.getCollectionTime()));
            }

            return new JvmMetricsDto(pid, Instant.now(), memory, threads, gc);

        } catch (IOException | MalformedObjectNameException | UndeclaredThrowableException e) {
            // The target JVM probably exited. Drop the cached connection so the next call reconnects.
            connectionManager.evict(pid);
            throw new JvmConnectionException("Failed to collect metrics for JVM " + pid, e);
        }
    }
}