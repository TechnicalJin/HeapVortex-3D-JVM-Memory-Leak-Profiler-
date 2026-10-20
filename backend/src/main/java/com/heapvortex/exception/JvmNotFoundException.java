package com.heapvortex.exception;

public class JvmNotFoundException extends RuntimeException {
    public JvmNotFoundException(String pid) {
        super("JVM not found: " + pid);
    }
}