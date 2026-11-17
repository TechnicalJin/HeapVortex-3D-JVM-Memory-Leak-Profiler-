package com.heapvortex.controller;

import com.heapvortex.dto.MemoryPoolDto;
import com.heapvortex.exception.JvmNotFoundException;
import com.heapvortex.service.JvmDiscoveryService;
import com.heapvortex.service.MemoryPoolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jvms/{pid}/pools")
public class MemoryPoolController {

    private final MemoryPoolService poolService;
    private final JvmDiscoveryService discoveryService;

    public MemoryPoolController(MemoryPoolService poolService, JvmDiscoveryService discoveryService) {
        this.poolService = poolService;
        this.discoveryService = discoveryService;
    }

    @GetMapping
    public List<MemoryPoolDto> pools(@PathVariable String pid) {
        if (!discoveryService.exists(pid)) {
            throw new JvmNotFoundException(pid);
        }
        return poolService.collect(pid);
    }
}