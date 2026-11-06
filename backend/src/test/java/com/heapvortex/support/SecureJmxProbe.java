package com.heapvortex.support;

import com.heapvortex.jvm.SecureJmxConnector;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/** Connects to a JMX port and prints the pid of the JVM behind it. Arguments: host port [plain]. */
public final class SecureJmxProbe {

    private SecureJmxProbe() {
    }

    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        boolean plain = args.length > 2 && args[2].equals("plain");

        JMXConnector connector = plain
                ? JMXConnectorFactory.connect(
                        new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi"))
                : SecureJmxConnector.connect(host, port);
        try (connector) {
            RuntimeMXBean runtime = ManagementFactory.getPlatformMXBean(
                    connector.getMBeanServerConnection(), RuntimeMXBean.class);
            System.out.println("PROBE_OK pid=" + runtime.getPid());
        }
    }
}