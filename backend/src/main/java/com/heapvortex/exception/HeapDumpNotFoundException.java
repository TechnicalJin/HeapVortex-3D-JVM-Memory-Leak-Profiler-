package com.heapvortex.exception;

public class HeapDumpNotFoundException extends RuntimeException {
    public HeapDumpNotFoundException(String id) {
        super("Heap dump not found: " + id);
    }
}