package com.heapvortex.heap;

/** The kinds of GC roots that a heap dump can contain. */
public enum RootKind {
    UNKNOWN,
    JNI_GLOBAL,
    JNI_LOCAL,
    JAVA_FRAME,
    NATIVE_STACK,
    STICKY_CLASS,
    THREAD_BLOCK,
    MONITOR_USED,
    THREAD_OBJECT
}