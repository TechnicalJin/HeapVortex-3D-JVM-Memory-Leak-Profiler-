package com.heapvortex.exception;

public class HprofFormatException extends RuntimeException {
    public HprofFormatException(String message) {
        super(message);
    }

    public HprofFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}