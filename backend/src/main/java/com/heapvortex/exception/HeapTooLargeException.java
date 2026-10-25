package com.heapvortex.exception;

public class HeapTooLargeException extends RuntimeException {
    public HeapTooLargeException(int maxObjects) {
        super("The heap dump has more than " + maxObjects
                + " objects. Raise heapvortex.analysis.max-objects and the -Xmx of the backend.");
    }
}