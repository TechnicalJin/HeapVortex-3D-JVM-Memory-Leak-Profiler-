package com.heapvortex.jvm;

import com.heapvortex.exception.InvalidRequestException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JmxConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(JmxConnectionManager.class);

    /** A JVM id like remote:localhost:9010 means: a JVM with TLS-protected JMX on that host and port. */
    public static final String REMOTE_PREFIX = "remote:";
    private static final Pattern REMOTE_ID = Pattern.compile("remote:([A-Za-z0-9._-]+):(\\d{1,5})");

    private final ConcurrentHashMap<String, JMXConnector> connections = new ConcurrentHashMap<>();

    public static String remotePid(String host, int port) {
        return REMOTE_PREFIX + host + ":" + port;
    }

    public static boolean isRemote(String pid) {
        return pid != null && pid.startsWith(REMOTE_PREFIX);
    }

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
        return isRemote(pid) ? connectRemote(pid) : connectLocal(pid);
    }

    private JMXConnector connectRemote(String pid) {
        Matcher matcher = REMOTE_ID.matcher(pid);
        if (!matcher.matches()) {
            throw new InvalidRequestException("Invalid remote JVM id: " + pid);
        }
        String host = matcher.group(1);
        int port = Integer.parseInt(matcher.group(2));
        if (port < 1 || port > 65535) {
            throw new InvalidRequestException("Invalid remote JMX port: " + port);
        }
        try {
            log.info("Connecting to {}:{} over TLS", host, port);
            return SecureJmxConnector.connect(host, port);
        } catch (IOException e) {
            throw new JvmConnectionException(
                    "Could not open a TLS JMX connection to " + host + ":" + port + ": " + e.getMessage(), e);
        }
    }

    private JMXConnector connectLocal(String pid) {
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