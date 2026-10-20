package com.heapvortex.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExceptionMessagesTest {

    @Test
    void notFoundMessageContainsThePid() {
        assertEquals("JVM not found: 42", new JvmNotFoundException("42").getMessage());
    }

    @Test
    void connectionExceptionKeepsTheCause() {
        RuntimeException cause = new RuntimeException("root");
        JvmConnectionException ex = new JvmConnectionException("failed", cause);
        assertEquals("failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}