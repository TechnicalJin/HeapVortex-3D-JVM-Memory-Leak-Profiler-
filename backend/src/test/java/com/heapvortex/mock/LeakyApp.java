package com.heapvortex.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A tiny app with a deliberate leak: every session is added to a static list and never removed. */
public final class LeakyApp {

    private static final int MAX_LEAKED = 4000; // safety stop, so an orphaned process stays small
    private static final List<LeakedSession> SESSIONS = new ArrayList<>();
    private static final Map<Integer, String> BOUNDED_CACHE = new HashMap<>(); // healthy, never grows

    private static final class LeakedSession {
        final long id;
        final String owner;
        final byte[] payload;

        LeakedSession(long id, String owner, byte[] payload) {
            this.id = id;
            this.owner = owner;
            this.payload = payload;
        }
    }

    private LeakyApp() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("LeakyApp started, pid=" + ProcessHandle.current().pid());
        long id = 0;
        while (true) {
            if (SESSIONS.size() < MAX_LEAKED) {
                SESSIONS.add(new LeakedSession(id, "user-" + (id % 50), new byte[32 * 1024]));
            }
            BOUNDED_CACHE.put((int) (id % 100), "value-" + id);
            byte[] shortLived = new byte[8 * 1024]; // normal garbage
            id++;
            Thread.sleep(20);
        }
    }
}