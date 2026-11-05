package com.heapvortex.jvm;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Opens a JMX connection to a JVM that was started with com.sun.management.jmxremote.ssl=true. */
public final class SecureJmxConnector {

    private SecureJmxConnector() {
    }

    public static JMXConnector connect(String host, int port) throws IOException {
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi");
        Map<String, Object> env = new HashMap<>();
        // the registry lookup is encrypted too, the stub that comes back carries its own TLS socket factory
        env.put("com.sun.jndi.rmi.factory.socket", new SslRMIClientSocketFactory());
        return JMXConnectorFactory.connect(url, env);
    }
}