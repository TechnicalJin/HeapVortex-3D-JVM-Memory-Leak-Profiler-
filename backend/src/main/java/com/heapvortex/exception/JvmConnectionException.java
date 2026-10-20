package com.heapvortex.exception;

public class JvmConnectionException extends RuntimeException {
    public JvmConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}