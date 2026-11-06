package com.heapvortex.jvm;

import com.heapvortex.mock.LeakyApp;
import com.heapvortex.support.SecureJmxProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Week 4 audit: JMX over TLS. A separate JVM exposes JMX with TLS and a self-signed certificate.
 * Clients run in their own JVMs, because Java reads its trust settings only once per JVM.
 */
@Timeout(180)
class SecureJmxConnectorTest {

    private static final String PASSWORD = "changeit";

    private record Result(int exitCode, String output) {}

    @TempDir
    Path dir;

    private final String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    private Process target;
    private Path targetLog;
    private int port;
    private Path trustStore;

    @BeforeEach
    void startSecureTarget() throws Exception {
        Path keyStore = dir.resolve("keystore.p12");
        Path certificate = dir.resolve("server.cer");
        trustStore = dir.resolve("truststore.p12");
        targetLog = dir.resolve("target.log");

        keytool("-genkeypair", "-alias", "heapvortex", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "2", "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12",
                "-keystore", keyStore.toString(), "-storepass", PASSWORD);
        keytool("-exportcert", "-alias", "heapvortex", "-keystore", keyStore.toString(),
                "-storepass", PASSWORD, "-file", certificate.toString());
        keytool("-importcert", "-noprompt", "-alias", "heapvortex", "-file", certificate.toString(),
                "-keystore", trustStore.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD);

        port = freePort();
        target = new ProcessBuilder(java, "-Xmx256m",
                "-Dcom.sun.management.jmxremote.port=" + port,
                "-Dcom.sun.management.jmxremote.rmi.port=" + port,
                "-Dcom.sun.management.jmxremote.host=127.0.0.1",
                "-Dcom.sun.management.jmxremote.authenticate=false",
                "-Dcom.sun.management.jmxremote.ssl=true",
                "-Dcom.sun.management.jmxremote.registry.ssl=true",
                "-Djava.rmi.server.hostname=127.0.0.1",
                "-Djavax.net.ssl.keyStore=" + keyStore,
                "-Djavax.net.ssl.keyStorePassword=" + PASSWORD,
                "-cp", codeSource(LeakyApp.class).toString(), LeakyApp.class.getName())
                .redirectErrorStream(true)
                .redirectOutput(targetLog.toFile())
                .start();
    }

    @AfterEach
    void stopSecureTarget() {
        if (target != null) {
            target.destroyForcibly();
        }
    }

    @Test
    void aClientThatTrustsTheCertificateCanReadMetrics() throws Exception {
        waitUntilSecureJmxIsUp();

        Result result = probe(true, false);

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("PROBE_OK pid=" + target.pid()), result.output());
        System.out.println("WEEK4 TLS trusted client: " + result.output().trim());
    }

    @Test
    void aClientThatDoesNotTrustTheCertificateIsRefused() throws Exception {
        waitUntilSecureJmxIsUp();

        Result result = probe(false, false);

        assertNotEquals(0, result.exitCode(), "an untrusted client must not get through: " + result.output());
        System.out.println("WEEK4 TLS untrusted client refused: " + firstLineAbout(result.output(),
                "PKIX", "SSLHandshakeException", "certificate"));
    }

    @Test
    void aPlainTextClientIsRefused() throws Exception {
        waitUntilSecureJmxIsUp();

        Result result = probe(true, true);

        assertNotEquals(0, result.exitCode(), "a plain-text client must not get through: " + result.output());
        System.out.println("WEEK4 TLS plain-text client refused: " + firstLineAbout(result.output(),
                "Exception", "refused", "non-JRMP"));
    }

    private void waitUntilSecureJmxIsUp() throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        Result last = null;
        while (System.currentTimeMillis() < deadline) {
            last = probe(true, false);
            if (last.exitCode() == 0) {
                return;
            }
            if (!target.isAlive()) {
                break;
            }
            Thread.sleep(1_500);
        }
        fail("the secure JMX port " + port + " did not come up."
                + "\ntarget JVM still running: " + target.isAlive()
                + "\n--- last probe (exit code " + (last == null ? "n/a" : last.exitCode()) + ") ---\n"
                + (last == null ? "" : tail(last.output()))
                + "\n--- output of the target JVM ---\n" + tail(readQuietly(targetLog)));
    }

    private Result probe(boolean trusted, boolean plain) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(java);
        if (trusted) {
            command.add("-Djavax.net.ssl.trustStore=" + trustStore);
            command.add("-Djavax.net.ssl.trustStorePassword=" + PASSWORD);
        }
        command.add("-cp");
        command.add(codeSource(SecureJmxProbe.class) + File.pathSeparator + codeSource(SecureJmxConnector.class));
        command.add(SecureJmxProbe.class.getName());
        command.add("127.0.0.1");
        command.add(String.valueOf(port));
        if (plain) {
            command.add("plain");
        }

        Path log = Files.createTempFile(dir, "probe", ".log");
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        boolean finished = process.waitFor(40, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = Files.readString(log);
        return new Result(finished ? process.exitValue() : -1, output);
    }

    private static String firstLineAbout(String output, String... words) {
        for (String line : output.split("\\R")) {
            for (String word : words) {
                if (line.contains(word)) {
                    return line.trim();
                }
            }
        }
        return output.isBlank() ? "(no output)" : output.lines().findFirst().orElse("").trim();
    }

    private static String tail(String text) {
        return text.length() <= 3000 ? text : "..." + text.substring(text.length() - 3000);
    }

    private static String readQuietly(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file) : "";
        } catch (IOException e) {
            return "(cannot read " + file + ": " + e.getMessage() + ")";
        }
    }

    private static Path codeSource(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
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