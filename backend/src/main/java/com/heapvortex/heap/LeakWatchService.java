package com.heapvortex.heap;

import com.heapvortex.dto.GcMetricsDto;
import com.heapvortex.dto.JvmMetricsDto;
import com.heapvortex.dto.WatchStatusDto;
import com.heapvortex.service.HeapDumpService;
import com.heapvortex.service.JvmTelemetryService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Polls the telemetry of a JVM and reports when its heap floor keeps rising. */
@Service
public class LeakWatchService {

    private static final Logger log = LoggerFactory.getLogger(LeakWatchService.class);
    private static final int WINDOW = 5;
    private static final double MIN_GROWTH_PERCENT = 10.0;

    private final JvmTelemetryService telemetryService;
    private final HeapDumpService heapDumpService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, Watch> watches = new ConcurrentHashMap<>();

    private static final class Watch {
        final LeakTrendDetector detector = new LeakTrendDetector(WINDOW, MIN_GROWTH_PERCENT);
        volatile ScheduledFuture<?> task;
        volatile WatchStatusDto status;
        boolean dumped;
    }

    public LeakWatchService(JvmTelemetryService telemetryService, HeapDumpService heapDumpService) {
        this.telemetryService = telemetryService;
        this.heapDumpService = heapDumpService;
    }

    public WatchStatusDto start(String pid, long intervalMs, boolean autoDump) {
        stop(pid);
        Watch watch = new Watch();
        watch.status = new WatchStatusDto(pid, "LEARNING", 0, 0, 0, null, "Collecting samples");
        watches.put(pid, watch);
        long interval = Math.max(500, intervalMs);
        watch.task = scheduler.scheduleWithFixedDelay(
                () -> poll(pid, watch, autoDump), interval, interval, TimeUnit.MILLISECONDS);
        return watch.status;
    }

    public void stop(String pid) {
        Watch watch = watches.remove(pid);
        if (watch != null && watch.task != null) {
            watch.task.cancel(false);
        }
    }

    public WatchStatusDto status(String pid) {
        Watch watch = watches.get(pid);
        return watch == null ? null : watch.status;
    }

    public List<WatchStatusDto> all() {
        List<WatchStatusDto> result = new ArrayList<>();
        watches.values().forEach(w -> result.add(w.status));
        return result;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void poll(String pid, Watch watch, boolean autoDump) {
        try {
            JvmMetricsDto metrics = telemetryService.collect(pid);
            long gcCount = metrics.gc().stream().mapToLong(GcMetricsDto::collectionCount).sum();
            long heapUsed = metrics.memory().heapUsed();

            LeakTrendDetector.Verdict verdict = watch.detector.add(gcCount, heapUsed);
            String dumpId = watch.status.dumpId();
            String message = switch (verdict.state()) {
                case LEARNING -> "Collecting samples (" + verdict.floorSamples() + " of " + WINDOW + ")";
                case STABLE -> "Heap floor is stable";
                case SUSPECTED -> String.format(
                        "Heap floor rose %.1f%% over the last %d collections", verdict.growthPercent(), WINDOW);
            };

            if (verdict.state() == LeakTrendDetector.State.SUSPECTED && autoDump && !watch.dumped) {
                watch.dumped = true; // one automatic dump per watch
                dumpId = heapDumpService.create(pid, true).id();
                message += ". Heap dump taken: " + dumpId;
            }

            watch.status = new WatchStatusDto(pid, verdict.state().name(), verdict.floorSamples(),
                    verdict.growthPercent(), heapUsed, dumpId, message);
        } catch (RuntimeException e) {
            log.warn("Leak watch for JVM {} stopped", pid, e);
            watch.status = new WatchStatusDto(pid, "ERROR", 0, 0, 0, watch.status.dumpId(),
                    "Watch stopped: " + e.getMessage());
            watches.remove(pid, watch);
            if (watch.task != null) {
                watch.task.cancel(false);
            }
        }
    }
}