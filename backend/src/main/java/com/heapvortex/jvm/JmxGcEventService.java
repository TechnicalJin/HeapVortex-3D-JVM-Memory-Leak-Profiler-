package com.heapvortex.jvm;

import com.heapvortex.dto.GcEventDto;
import com.heapvortex.exception.JvmConnectionException;
import com.heapvortex.service.GcEventService;
import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Listens to the GC notifications that every HotSpot JVM publishes through JMX. */
@Service
public class JmxGcEventService implements GcEventService {

    private static final Logger log = LoggerFactory.getLogger(JmxGcEventService.class);

    private final JmxConnectionManager connectionManager;

    public JmxGcEventService(JmxConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Subscription subscribe(String pid, Consumer<GcEventDto> consumer) {
        MBeanServerConnection server;
        ObjectName pattern;
        try {
            server = connectionManager.getConnection(pid).getMBeanServerConnection();
            pattern = new ObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*");
        } catch (IOException | MalformedObjectNameException e) {
            throw new JvmConnectionException("Could not subscribe to GC events of JVM " + pid, e);
        }

        Set<String> heapPools = heapPoolNames(server);

        // The filter stays null on purpose: a filter object would have to be serializable to travel to the target JVM.
        NotificationListener listener = (notification, handback) -> {
            if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                return;
            }
            try {
                GarbageCollectionNotificationInfo info =
                        GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
                consumer.accept(toDto(pid, info, heapPools));
            } catch (RuntimeException e) {
                log.debug("Ignored a GC notification of JVM {}", pid, e);
            }
        };

        List<ObjectName> registered = new ArrayList<>();
        try {
            for (ObjectName name : server.queryNames(pattern, null)) {
                try {
                    server.addNotificationListener(name, listener, null, null);
                    registered.add(name);
                } catch (InstanceNotFoundException | RuntimeException e) {
                    log.debug("GC bean {} of JVM {} does not send notifications", name, pid);
                }
            }
        } catch (IOException e) {
            connectionManager.evict(pid);
            throw new JvmConnectionException("Could not subscribe to GC events of JVM " + pid, e);
        }

        if (registered.isEmpty()) {
            throw new JvmConnectionException("JVM " + pid + " does not publish GC notifications", null);
        }

        return () -> {
            for (ObjectName name : registered) {
                try {
                    server.removeNotificationListener(name, listener);
                } catch (InstanceNotFoundException | ListenerNotFoundException | IOException ignored) {
                    // the JVM may already be gone
                }
            }
        };
    }

    private static GcEventDto toDto(String pid, GarbageCollectionNotificationInfo info, Set<String> heapPools) {
        GcInfo gc = info.getGcInfo();
        long before = sumUsed(gc.getMemoryUsageBeforeGc(), heapPools);
        long after = sumUsed(gc.getMemoryUsageAfterGc(), heapPools);
        return new GcEventDto(
                pid,
                info.getGcName(),
                info.getGcAction(),
                info.getGcCause(),
                gc.getDuration(),
                before,
                after,
                Math.max(0, before - after),
                Instant.now());
    }

    /** Heap pools only, so Metaspace and the code cache do not distort the reclaimed bytes. */
    private static long sumUsed(Map<String, MemoryUsage> usage, Set<String> heapPools) {
        long sum = 0;
        for (Map.Entry<String, MemoryUsage> entry : usage.entrySet()) {
            if (heapPools.isEmpty() || heapPools.contains(entry.getKey())) {
                sum += entry.getValue().getUsed();
            }
        }
        return sum;
    }

    private static Set<String> heapPoolNames(MBeanServerConnection server) {
        Set<String> names = new HashSet<>();
        try {
            ObjectName pattern = new ObjectName(ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE + ",*");
            for (ObjectName name : server.queryNames(pattern, null)) {
                MemoryPoolMXBean pool = ManagementFactory.newPlatformMXBeanProxy(
                        server, name.getCanonicalName(), MemoryPoolMXBean.class);
                if (pool.getType() == MemoryType.HEAP) {
                    names.add(pool.getName());
                }
            }
        } catch (IOException | MalformedObjectNameException | UndeclaredThrowableException e) {
            log.debug("Could not read the heap pool names, all pools will be counted", e);
        }
        return names;
    }
}