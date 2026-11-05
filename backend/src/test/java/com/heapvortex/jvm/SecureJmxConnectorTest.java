package com.heapvortex.jvm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.net.ssl.SSLContext;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Week 4 audit: JMX over TLS. A JMX server with a self-signed certificate runs inside the test JVM. */
@Timeout(60)
class SecureJmxConnectorTest {

    private static final char[] PASSWORD = "changeit".toCharArray();

    @TempDir
    Path dir;

    private SSLContext originalDefault;
    private Path trustStore;
    private Registry registry;
    private JMXConnectorServer server;
    private int port;

    @BeforeEach
    void startTlsServer() throws Exception {
        originalDefault = SSLContext.getDefault();

        Path keyStore = dir.resolve("keystore.p12");
        Path certificate = dir.resolve("server.cer");
        trustStore = dir.resolve("truststore.p12");

        keytool("-genkeypair", "-alias", "heapvortex", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "2", "-dname", "CN=localhost", "-storetype", "PKCS12",
                "-keystore", keyStore.toString(), "-storepass", "changeit");
        keytool("-exportcert", "-alias", "heapvortex", "-keystore", keyStore.toString(),
                "-storepass", "changeit", "-file", certificate.toString());
        keytool("-importcert", "-noprompt", "-alias", "heapvortex", "-file", certificate.toString(),
                "-keystore", trustStore.toString(), "-storetype", "PKCS12", "-storepass", "changeit");

        SslRMIServerSocketFactory serverFactory =
                new SslRMIServerSocketFactory(TlsSupport.serverContext(keyStore, PASSWORD), null, null, false);
        SslRMIClientSocketFactory clientFactory = new SslRMIClientSocketFactory();

        port = freePort();
        registry = LocateRegistry.createRegistry(port, clientFactory, serverFactory);

        Map<String, Object> env = new HashMap<>();
        env.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, clientFactory);
        env.put(RMIConnectorServer.RMI_SERVER_SOCKET_FACTORY_ATTRIBUTE, serverFactory);
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://localhost/jndi/rmi://localhost:" + port + "/jmxrmi");
        server = JMXConnectorServerFactory.newJMXConnectorServer(url, env, ManagementFactory.getPlatformMBeanServer());
        server.start();
    }

    @AfterEach
    void stopTlsServer() throws Exception {
        SSLContext.setDefault(originalDefault);
        if (server != null) {
            server.stop();
        }
        if (registry != null) {
            UnicastRemoteObject.unexportObject(registry, true);
        }
    }

    @Test
    void readsMetricsOverTlsWhenTheCertificateIsTrusted() throws Exception {
        SSLContext.setDefault(TlsSupport.clientContext(trustStore, PASSWORD));

        try (JMXConnector connector = SecureJmxConnector.connect("localhost", port)) {
            MBeanServerConnection mbeans = connector.getMBeanServerConnection();

            RuntimeMXBean runtime = ManagementFactory.getPlatformMXBean(mbeans, RuntimeMXBean.class);
            assertEquals(ProcessHandle.current().pid(), runtime.getPid());

            com.sun.management.OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(
                    mbeans, com.sun.management.OperatingSystemMXBean.class);
            assertTrue(os.getAvailableProcessors() > 0);
            System.out.printf("WEEK4 TLS ok: pid=%d processors=%d cpuLoad=%.3f%n",
                    runtime.getPid(), os.getAvailableProcessors(), os.getProcessCpuLoad());
        }
    }

    @Test
    void refusesAClientThatDoesNotTrustTheCertificate() {
        // the default context still trusts only the JDK certificate authorities, not our self-signed certificate
        IOException error = assertThrows(IOException.class, () -> SecureJmxConnector.connect("localhost", port).close());
        System.out.println("WEEK4 TLS refused untrusted client: " + rootCause(error).getClass().getSimpleName());
    }

    @Test
    void refusesAPlainTextClient() {
        assertThrows(IOException.class, () -> {
            JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + port + "/jmxrmi");
            JMXConnectorFactory.connect(url).close();
        });
        System.out.println("WEEK4 TLS refused plain-text client");
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void keytool(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), "keytool failed: " + output);
    }
}