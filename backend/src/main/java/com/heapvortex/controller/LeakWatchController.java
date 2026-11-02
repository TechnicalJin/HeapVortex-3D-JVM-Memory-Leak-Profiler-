package com.heapvortex.controller;

import com.heapvortex.dto.WatchStatusDto;
import com.heapvortex.exception.InvalidRequestException;
import com.heapvortex.exception.JvmNotFoundException;
import com.heapvortex.heap.LeakWatchService;
import com.heapvortex.service.JvmDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jvms/{pid}/watch")
public class LeakWatchController {

    private final LeakWatchService watchService;
    private final JvmDiscoveryService discoveryService;

    public LeakWatchController(LeakWatchService watchService, JvmDiscoveryService discoveryService) {
        this.watchService = watchService;
        this.discoveryService = discoveryService;
    }

    @PostMapping
    public WatchStatusDto start(@PathVariable String pid,
                                @RequestParam(defaultValue = "2000") long intervalMs,
                                @RequestParam(defaultValue = "true") boolean autoDump) {
        if (!discoveryService.exists(pid)) {
            throw new JvmNotFoundException(pid);
        }
        if (intervalMs < 500 || intervalMs > 60_000) {
            throw new InvalidRequestException("intervalMs must be between 500 and 60000");
        }
        return watchService.start(pid, intervalMs, autoDump);
    }

    @GetMapping
    public ResponseEntity<WatchStatusDto> status(@PathVariable String pid) {
        WatchStatusDto status = watchService.status(pid);
        return status == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(status);
    }

    @DeleteMapping
    public ResponseEntity<Void> stop(@PathVariable String pid) {
        watchService.stop(pid);
        return ResponseEntity.noContent().build();
    }
}