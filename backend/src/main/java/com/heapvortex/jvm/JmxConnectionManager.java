package com.heapvortex.jvm;

import com.heapvortex.exception.JvmConnectionException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JmxConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(JmxConnectionManager.class);

    private final ConcurrentHashMap<String, JMXConnector> connections = new ConcurrentHashMap<>();

    /** Returns the cached connection for the PID, or opens a new one. */
    public JMXConnector getConnection(String pid) {
        return connections.computeIfAbsent(pid, this::connect);
    }

    /** Closes and forgets a connection, for example after it has gone stale. */
    public void evict(String pid) {
        closeQuietly(connections.remove(pid));
    }

    public int openConnections() {
        return connections.size();
    }

    @PreDestroy
    public void shutdown() {
        connections.values().forEach(this::closeQuietly);
        connections.clear();
    }

    private JMXConnector connect(String pid) {
        VirtualMachine vm = null;
        try {
            vm = VirtualMachine.attach(pid);
            String address = vm.startLocalManagementAgent();
            log.info("Connecting to JVM {} at {}", pid, address);
            return JMXConnectorFactory.connect(new JMXServiceURL(address));
        } catch (AttachNotSupportedException | IOException e) {
            throw new JvmConnectionException("Could not connect to JVM " + pid + ": " + e.getMessage(), e);
        } finally {
            if (vm != null) {
                try {
                    vm.detach();
                } catch (IOException ignored) {
                    // the JMX connection stays valid after detaching
                }
            }
        }
    }

    private void closeQuietly(JMXConnector connector) {
        if (connector == null) {
            return;
        }
        try {
            connector.close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }
}