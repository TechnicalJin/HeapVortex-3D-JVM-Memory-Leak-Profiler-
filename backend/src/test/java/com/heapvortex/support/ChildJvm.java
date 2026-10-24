package com.heapvortex.support;

import com.heapvortex.service.JvmDiscoveryService;

/** Starts a throw-away JVM (SleepMain) that tests can attach to. */
public final class ChildJvm {

    private final Process process;

    private ChildJvm(Process process) {
        this.process = process;
    }

    public static ChildJvm start(JvmDiscoveryService discovery) throws Exception {
        String javaBin = System.getProperty("java.home") + "/bin/java";
        Process process = new ProcessBuilder(javaBin, "-cp", "target/test-classes", SleepMain.class.getName())
                .inheritIO()
                .start();
        ChildJvm child = new ChildJvm(process);

        // the new JVM needs a moment before it becomes attachable
        long deadline = System.currentTimeMillis() + 10_000;
        while (!discovery.exists(child.pid()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        return child;
    }

    public String pid() {
        return String.valueOf(process.pid());
    }

    /** Stops the process and waits until it has really exited. */
    public void stop() throws InterruptedException {
        process.destroyForcibly().waitFor();
    }
}