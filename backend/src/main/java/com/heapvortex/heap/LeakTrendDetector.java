package com.heapvortex.heap;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Looks at the lowest heap usage between garbage collections (the "floor").
 * A flat floor is healthy. A floor that keeps rising is a leak suspect.
 */
public final class LeakTrendDetector {

    public enum State { LEARNING, STABLE, SUSPECTED }

    public record Verdict(State state, int floorSamples, long firstFloor, long lastFloor, double growthPercent) {}

    /** A floor may dip by this share and still count as "not falling" (noise). */
    private static final double NOISE = 0.01;

    private final int window;
    private final double minGrowthPercent;
    private final Deque<Long> floors = new ArrayDeque<>();

    private long lastGcCount = -1;
    private long minSinceGc = Long.MAX_VALUE;

    public LeakTrendDetector(int window, double minGrowthPercent) {
        if (window < 2) {
            throw new IllegalArgumentException("window must be at least 2");
        }
        this.window = window;
        this.minGrowthPercent = minGrowthPercent;
    }

    /** Feed one telemetry sample: the total GC count of the JVM and its current heap usage. */
    public Verdict add(long totalGcCount, long heapUsed) {
        minSinceGc = Math.min(minSinceGc, heapUsed);

        if (lastGcCount >= 0 && totalGcCount > lastGcCount) {
            floors.addLast(minSinceGc);
            while (floors.size() > window) {
                floors.removeFirst();
            }
            minSinceGc = heapUsed;
        }
        lastGcCount = totalGcCount;
        return evaluate();
    }

    private Verdict evaluate() {
        if (floors.size() < window) {
            long first = floors.isEmpty() ? 0 : floors.peekFirst();
            long last = floors.isEmpty() ? 0 : floors.peekLast();
            return new Verdict(State.LEARNING, floors.size(), first, last, 0);
        }

        long previous = -1;
        boolean rising = true;
        for (long floor : floors) {
            if (previous >= 0 && floor < previous * (1 - NOISE)) {
                rising = false;
            }
            previous = floor;
        }

        long first = floors.peekFirst();
        long last = floors.peekLast();
        double growth = first == 0 ? 0 : 100.0 * (last - first) / first;
        State state = rising && growth >= minGrowthPercent ? State.SUSPECTED : State.STABLE;
        return new Verdict(state, floors.size(), first, last, growth);
    }
}