package com.heapvortex.service;

import com.heapvortex.dto.MemoryPoolDto;
import java.util.List;

public interface MemoryPoolService {

    /** Current usage of every memory pool of the JVM. */
    List<MemoryPoolDto> collect(String pid);
}