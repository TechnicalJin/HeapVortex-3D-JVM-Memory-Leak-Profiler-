package com.heapvortex.service;

import com.heapvortex.dto.GcEventDto;

import java.util.function.Consumer;

public interface GcEventService {

    /** Calls the consumer for every garbage collection of the JVM until the subscription is closed. */
    Subscription subscribe(String pid, Consumer<GcEventDto> consumer);

    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}