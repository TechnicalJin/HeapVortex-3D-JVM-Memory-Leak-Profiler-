package com.heapvortex.jvm;

import com.heapvortex.dto.CpuMetricsDto;
import com.heapvortex.exception.JvmConnectionException;
import com.heapvortex.service.CpuMetricsService;
import org.springframework.stereotype.Service;

import javax.management.MBeanServerConnection;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.UndeclaredThrowableException;

@Service
public class JmxCpuMetricsService implements CpuMetricsService {

    private final JmxConnectionManager connectionManager;

    public JmxCpuMetricsService(JmxConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public CpuMetricsDto collect(String pid) {
        try {
            MBeanServerConnection server = connectionManager.getConnection(pid).getMBeanServerConnection();
            com.sun.management.OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(
                    server, com.sun.management.OperatingSystemMXBean.class);
            return new CpuMetricsDto(
                    pid,
                    toPercent(os.getProcessCpuLoad()),
                    toPercent(os.getCpuLoad()),
                    os.getAvailableProcessors());
        } catch (IOException | UndeclaredThrowableException e) {
            connectionManager.evict(pid);
            throw new JvmConnectionException("Failed to collect CPU metrics for JVM " + pid, e);
        }
    }

    /** JMX reports a load between 0 and 1, or a negative number when it is not available yet. */
    static double toPercent(double load) {
        if (Double.isNaN(load) || load < 0) {
            return -1;
        }
        return Math.min(100, load * 100);
    }
}