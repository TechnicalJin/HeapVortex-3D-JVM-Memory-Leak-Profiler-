package com.heapvortex.heap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeakTrendDetectorTest {

    /** Simulates one garbage collection: a sample before it (high heap) and one after it (the floor). */
    private static LeakTrendDetector.Verdict cycle(LeakTrendDetector d, long gcCount, long floor) {
        d.add(gcCount - 1, floor + 5_000_000); // heap before the collection
        return d.add(gcCount, floor);          // the GC count changed here
    }

    @Test
    void staysInLearningUntilTheWindowIsFull() {
        LeakTrendDetector d = new LeakTrendDetector(5, 10.0);
        d.add(0, 100_000_000);
        LeakTrendDetector.Verdict v = cycle(d, 1, 100_000_000);

        assertEquals(LeakTrendDetector.State.LEARNING, v.state());
    }

    @Test
    void aFlatFloorIsStable() {
        LeakTrendDetector d = new LeakTrendDetector(5, 10.0);
        d.add(0, 100_000_000);
        LeakTrendDetector.Verdict v = null;
        for (int gc = 1; gc <= 6; gc++) {
            v = cycle(d, gc, 100_000_000 + (gc % 2) * 500_000); // tiny noise only
        }

        assertEquals(LeakTrendDetector.State.STABLE, v.state());
    }

    @Test
    void aRisingFloorIsSuspected() {
        LeakTrendDetector d = new LeakTrendDetector(5, 10.0);
        d.add(0, 100_000_000);
        LeakTrendDetector.Verdict v = null;
        for (int gc = 1; gc <= 6; gc++) {
            v = cycle(d, gc, 100_000_000 + gc * 10_000_000L);
        }

        assertEquals(LeakTrendDetector.State.SUSPECTED, v.state());
        assertTrue(v.growthPercent() >= 10.0);
        assertTrue(v.lastFloor() > v.firstFloor());
    }

    @Test
    void aFloorThatDropsAgainIsNotASuspect() {
        LeakTrendDetector d = new LeakTrendDetector(5, 10.0);
        d.add(0, 100_000_000);
        long[] floors = {100, 130, 160, 120, 150, 110};
        LeakTrendDetector.Verdict v = null;
        for (int i = 0; i < floors.length; i++) {
            v = cycle(d, i + 1, floors[i] * 1_000_000L);
        }

        assertEquals(LeakTrendDetector.State.STABLE, v.state());
    }

    @Test
    void slowGrowthBelowTheThresholdIsStable() {
        LeakTrendDetector d = new LeakTrendDetector(5, 10.0);
        d.add(0, 100_000_000);
        LeakTrendDetector.Verdict v = null;
        for (int gc = 1; gc <= 6; gc++) {
            v = cycle(d, gc, 100_000_000 + gc * 100_000L); // about 0.5% in total
        }

        assertEquals(LeakTrendDetector.State.STABLE, v.state());
    }
}