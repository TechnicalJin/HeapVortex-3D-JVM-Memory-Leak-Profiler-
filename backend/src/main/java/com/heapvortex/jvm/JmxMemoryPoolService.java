package com.heapvortex.jvm;

import com.heapvortex.dto.MemoryPoolDto;
import com.heapvortex.exception.JvmConnectionException;
import com.heapvortex.service.MemoryPoolService;
import org.springframework.stereotype.Service;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class JmxMemoryPoolService implements MemoryPoolService {

    private final JmxConnectionManager connectionManager;

    public JmxMemoryPoolService(JmxConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public List<MemoryPoolDto> collect(String pid) {
        try {
            MBeanServerConnection server = connectionManager.getConnection(pid).getMBeanServerConnection();
            ObjectName pattern = new ObjectName(ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE + ",*");

            List<MemoryPoolDto> result = new ArrayList<>();
            for (ObjectName name : server.queryNames(pattern, null)) {
                MemoryPoolMXBean pool = ManagementFactory.newPlatformMXBeanProxy(
                        server, name.getCanonicalName(), MemoryPoolMXBean.class);
                MemoryUsage usage = pool.getUsage();
                if (usage == null) {
                    continue; // the pool is no longer valid
                }
                MemoryUsage peak = pool.getPeakUsage();
                MemoryUsage afterGc = pool.getCollectionUsage();
                result.add(new MemoryPoolDto(
                        pool.getName(),
                        pool.getType().name(),
                        usage.getUsed(),
                        usage.getCommitted(),
                        usage.getMax(),
                        peak == null ? 0 : peak.getUsed(),
                        afterGc == null ? -1 : afterGc.getUsed()));
            }
            result.sort(Comparator.comparing(MemoryPoolDto::type).thenComparing(MemoryPoolDto::name));
            return result;

        } catch (IOException | MalformedObjectNameException | UndeclaredThrowableException e) {
            connectionManager.evict(pid);
            throw new JvmConnectionException("Failed to collect memory pools for JVM " + pid, e);
        }
    }
}