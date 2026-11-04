package com.heapvortex.audit;

import com.heapvortex.dto.GcEventDto;
import com.heapvortex.dto.MemoryPoolDto;
import com.heapvortex.jvm.JmxConnectionManager;
import com.heapvortex.mock.LeakyApp;
import com.heapvortex.service.GcEventService;
import com.heapvortex.service.JvmDiscoveryService;
import com.heapvortex.service.MemoryPoolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.management.MBeanServerConnection;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Week 3 audit: live memory pools and real-time GC events from a separate JVM, through JMX. */
@SpringBootTest
@TestPropertySource(properties = "heapvortex.dumps.dir=target/audit-dumps")
class LiveTelemetryAuditTest {

    @Autowired JvmDiscoveryService discovery;
    @Autowired MemoryPoolService poolService;
    @Autowired GcEventService gcService;
    @Autowired JmxConnectionManager connectionManager;

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
    void streamsMemoryPoolsAndGcEventsFromALiveJvm() throws Exception {
        String pid = String.valueOf(leakyApp.pid());

        long deadline = System.currentTimeMillis() + 15_000;
        while (!discovery.exists(pid) && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }
        assertTrue(discovery.exists(pid), "backend cannot see the mock JVM " + pid);

        BlockingQueue<GcEventDto> events = new LinkedBlockingQueue<>();
        try (GcEventService.Subscription ignored = gcService.subscribe(pid, events::add)) {

            // 1. Memory pools
            List<MemoryPoolDto> pools = poolService.collect(pid);
            pools.forEach(p -> System.out.printf("WEEK3 pool: %s type=%s used=%d committed=%d max=%d%n",
                    p.name(), p.type(), p.used(), p.committed(), p.max()));
            assertFalse(pools.isEmpty(), "no memory pools returned");
            assertTrue(pools.stream().anyMatch(p -> p.type().equals("HEAP") && p.used() > 0),
                    "no heap pool with used memory");

            // 2. Force a garbage collection inside the target JVM through JMX and wait for its event
            MBeanServerConnection server = connectionManager.getConnection(pid).getMBeanServerConnection();
            ManagementFactory.newPlatformMXBeanProxy(server, ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class)
                    .gc();

            GcEventDto event = null;
            long until = System.currentTimeMillis() + 15_000;
            while (event == null && System.currentTimeMillis() < until) {
                GcEventDto next = events.poll(1, TimeUnit.SECONDS);
                if (next != null && next.gcCause() != null && next.gcCause().contains("System.gc")) {
                    event = next;
                }
            }

            assertNotNull(event, "no GC event arrived after System.gc() in the target JVM");
            System.out.printf("WEEK3 gc event: %s | %s | cause=%s | %d ms | before=%d after=%d reclaimed=%d%n",
                    event.gcName(), event.gcAction(), event.gcCause(), event.durationMs(),
                    event.heapBeforeBytes(), event.heapAfterBytes(), event.reclaimedBytes());

            assertEquals(pid, event.pid());
            assertFalse(event.gcName().isBlank());
            assertTrue(event.durationMs() >= 0);
            assertTrue(event.heapBeforeBytes() > 0);
        }
    }
}