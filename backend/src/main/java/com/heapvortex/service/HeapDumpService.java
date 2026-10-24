package com.heapvortex.service;

import com.heapvortex.dto.HeapDumpInfoDto;

public interface HeapDumpService {

    /**
     * Makes the JVM with the given PID write a heap dump.
     *
     * @param live if true, the JVM runs a full GC first, so only reachable objects are dumped
     */
    HeapDumpInfoDto create(String pid, boolean live);
}