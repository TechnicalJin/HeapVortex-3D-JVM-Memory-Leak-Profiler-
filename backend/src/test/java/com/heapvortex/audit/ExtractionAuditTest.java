package com.heapvortex.audit;

import com.heapvortex.dto.DominatorSuspectDto;
import com.heapvortex.dto.HeapDumpComparisonDto;
import com.heapvortex.dto.HeapDumpInfoDto;
import com.heapvortex.heap.HeapDumpComparisonService;
import com.heapvortex.heap.RetainedSizeService;
import com.heapvortex.mock.LeakyApp;
import com.heapvortex.service.HeapDumpService;
import com.heapvortex.service.JvmDiscoveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extraction audit: attach to a real, separate JVM that leaks memory,
 * take heap dumps through JMX and check that the backend names the leaking class.
 */
@SpringBootTest
@TestPropertySource(properties = "heapvortex.dumps.dir=target/audit-dumps")
class ExtractionAuditTest {

    private static final String LEAK_CLASS = "com.heapvortex.mock.LeakyApp$LeakedSession";

    @Autowired JvmDiscoveryService discovery;
    @Autowired HeapDumpService dumpService;
    @Autowired RetainedSizeService retainedService;
    @Autowired HeapDumpComparisonService comparisonService;

    private Process leakyApp;

    @BeforeEach
    void startLeakyApp() throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path classes = Path.of(LeakyApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        leakyApp = new ProcessBuilder(java.toString(), "-Xmx256m", "-cp", classes.toString(),
                LeakyApp.class.getName())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    @AfterEach
    void stopLeakyApp() {
        if (leakyApp != null) {
            leakyApp.destroyForcibly();
        }
    }

    @Test
    void attachesToLeakingJvmAndNamesTheLeakingClass() throws Exception {
        String pid = String.valueOf(leakyApp.pid());

        // 1. The JVM must be discoverable through the Attach API.
        long deadline = System.currentTimeMillis() + 15_000;
        while (!discovery.exists(pid) && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }
        assertTrue(discovery.exists(pid), "backend cannot see the mock JVM " + pid);
        System.out.println("AUDIT attached to mock JVM pid=" + pid);

        // 2. Two dumps, a few seconds apart, while the leak grows.
        Thread.sleep(4_000);
        HeapDumpInfoDto baseline = dumpService.create(pid, true);
        Thread.sleep(6_000);
        HeapDumpInfoDto later = dumpService.create(pid, true);
        System.out.println("AUDIT dumps: baseline=" + baseline.id() + " later=" + later.id());

        // 3. Single dump: dominator tree and retained size must point at the leaking class.
        List<DominatorSuspectDto> suspects = retainedService.suspects(later.id(), 3, 100);
        suspects.forEach(s -> System.out.printf(
                "AUDIT dominator suspect: top=%s retained=%d chain=%s accumulation=%s leakClass=%s rootKind=%s%n",
                s.topClassName(), s.topRetainedBytes(), s.dominatorChain(),
                s.accumulationClassName(), s.likelyLeakClass(),
                s.pathToGcRoot().rootKind()));
        assertTrue(suspects.stream().anyMatch(s ->
                        LEAK_CLASS.equals(s.likelyLeakClass()) && s.pathToGcRoot().pathFound()),
                "leaking class not found by retained-size analysis");

        // 4. Two dumps: the growth comparison must also contain the leaking class.
        HeapDumpComparisonDto comparison = comparisonService.compare(baseline.id(), later.id(), 100);
        comparison.classGrowth().stream().limit(5).forEach(c -> System.out.printf(
                "AUDIT growth: %s +%d instances, +%d bytes%n",
                c.className(), c.instanceCountGrowth(), c.dataBytesGrowth()));
        assertTrue(comparison.classGrowth().stream().anyMatch(c ->
                        LEAK_CLASS.equals(c.className()) && c.instanceCountGrowth() > 0),
                "leaking class not found by the dump comparison");
    }
}