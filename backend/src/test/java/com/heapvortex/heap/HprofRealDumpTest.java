package com.heapvortex.heap;

import com.heapvortex.dto.ClassHistogramEntryDto;
import com.heapvortex.dto.HeapSummaryDto;
import com.sun.management.HotSpotDiagnosticMXBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HprofRealDumpTest {

    static final class LeakMarker {
        final byte[] payload = new byte[16];
    }

    private static final List<LeakMarker> HOLDER = new ArrayList<>();

    @TempDir
    Path tempDir;

    @AfterEach
    void release() {
        HOLDER.clear();
    }

    private Path dumpOwnHeap() throws Exception {
        Path dump = tempDir.resolve("own.hprof");
        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).dumpHeap(dump.toString(), true);
        return dump;
    }

    @Test
    void parsesARealDumpAndFindsTheKnownInstances() throws Exception {
        for (int i = 0; i < 1000; i++) {
            HOLDER.add(new LeakMarker());
        }
        Path dump = dumpOwnHeap();

        ClassHistogramBuilder histogram = new ClassHistogramBuilder();
        HeapSummaryBuilder summary = new HeapSummaryBuilder();
        HprofReader.read(dump, histogram);
        HprofReader.read(dump, summary);

        ClassHistogramEntryDto marker = histogram.build().stream()
                .filter(entry -> entry.className().endsWith("HprofRealDumpTest$LeakMarker"))
                .findFirst()
                .orElseThrow();
        assertEquals(1000, marker.instanceCount());

        ClassHistogramEntryDto bytes = histogram.build().stream()
                .filter(entry -> entry.className().equals("byte[]"))
                .findFirst()
                .orElseThrow();
        assertTrue(bytes.instanceCount() >= 1000);
        assertTrue(bytes.dataBytes() >= 16_000);

        HeapSummaryDto result = summary.build();
        assertTrue(result.hprofVersion().startsWith("JAVA PROFILE 1.0"));
        assertTrue(result.classCount() > 100);
        assertTrue(result.instanceCount() >= 1000);
        assertTrue(result.gcRootsByKind().get("STICKY_CLASS") > 0);
    }

    @Test
    void aSecondReadOfTheSameFileGivesTheSameCounts() throws Exception {
        HOLDER.add(new LeakMarker());
        Path dump = dumpOwnHeap();

        HeapSummaryBuilder first = new HeapSummaryBuilder();
        HeapSummaryBuilder second = new HeapSummaryBuilder();
        HprofReader.read(dump, first);
        HprofReader.read(dump, second);

        assertEquals(first.build().instanceCount(), second.build().instanceCount());
    }
}