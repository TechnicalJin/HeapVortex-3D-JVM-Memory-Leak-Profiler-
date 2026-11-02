package com.heapvortex.jvm;

import com.heapvortex.dto.JvmInfoDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttachJvmDiscoveryServiceTest {

    private final AttachJvmDiscoveryService discovery = new AttachJvmDiscoveryService();

    private Process child;
    private String childPid;

    @BeforeEach
    void startChildJvm() throws Exception {
        String javaBin = System.getProperty("java.home") + "/bin/java";
        child = new ProcessBuilder(javaBin, "-cp", "target/test-classes", "com.heapvortex.support.SleepMain")
                .inheritIO()
                .start();
        childPid = String.valueOf(child.pid());

        // the new JVM needs a moment before it becomes attachable
        long deadline = System.currentTimeMillis() + 10_000;
        while (!discovery.exists(childPid) && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
    }

    @AfterEach
    void stopChildJvm() {
        child.destroyForcibly();
    }

    @Test
    void listFindsTheChildJvm() {
        List<JvmInfoDto> jvms = discovery.listJvms();
        assertTrue(jvms.stream().anyMatch(jvm -> jvm.pid().equals(childPid)));
    }

    @Test
    void listExcludesTheTestJvmItself() {
        String ownPid = String.valueOf(ProcessHandle.current().pid());
        assertTrue(discovery.listJvms().stream().noneMatch(jvm -> jvm.pid().equals(ownPid)));
    }

    @Test
    void everyEntryHasAPid() {
        discovery.listJvms().forEach(jvm -> {
            assertNotNull(jvm.pid());
            assertFalse(jvm.pid().isBlank());
        });
    }

    @Test
    void existsIsTrueForTheChildJvm() {
        assertTrue(discovery.exists(childPid));
    }

    @Test
    void existsIsFalseForAnUnknownPid() {
        assertFalse(discovery.exists("999999"));
    }

    @Test
    void existsIsFalseAfterTheChildJvmStops() throws Exception {
        child.destroyForcibly().waitFor();
        long deadline = System.currentTimeMillis() + 10_000;
        while (discovery.exists(childPid) && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertFalse(discovery.exists(childPid));
    }
}