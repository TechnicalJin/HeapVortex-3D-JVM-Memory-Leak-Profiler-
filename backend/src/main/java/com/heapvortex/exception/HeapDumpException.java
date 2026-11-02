package com.heapvortex.exception;

public class HeapDumpException extends RuntimeException {
    public HeapDumpException(String message, Throwable cause) {
        super(message, cause);
    }
}