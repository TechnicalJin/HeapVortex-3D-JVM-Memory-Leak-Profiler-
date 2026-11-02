package com.heapvortex.exception;

public class ObjectNotFoundException extends RuntimeException {
    public ObjectNotFoundException(String objectId) {
        super("Object not found: " + objectId);
    }
}