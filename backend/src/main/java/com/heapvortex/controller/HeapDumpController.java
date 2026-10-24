package com.heapvortex.controller;

import com.heapvortex.dto.HeapDumpInfoDto;
import com.heapvortex.exception.JvmNotFoundException;
import com.heapvortex.heap.HeapDumpStorage;
import com.heapvortex.service.HeapDumpService;
import com.heapvortex.service.JvmDiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class HeapDumpController {

    private final JvmDiscoveryService discoveryService;
    private final HeapDumpService heapDumpService;
    private final HeapDumpStorage storage;

    public HeapDumpController(JvmDiscoveryService discoveryService,
                              HeapDumpService heapDumpService,
                              HeapDumpStorage storage) {
        this.discoveryService = discoveryService;
        this.heapDumpService = heapDumpService;
        this.storage = storage;
    }

    @PostMapping("/jvms/{pid}/heapdump")
    public ResponseEntity<HeapDumpInfoDto> create(@PathVariable String pid,
                                                  @RequestParam(defaultValue = "true") boolean live) {
        if (!discoveryService.exists(pid)) {
            throw new JvmNotFoundException(pid);
        }
        HeapDumpInfoDto info = heapDumpService.create(pid, live);
        return ResponseEntity.created(URI.create("/api/heapdumps/" + info.id())).body(info);
    }

    @GetMapping("/heapdumps")
    public List<HeapDumpInfoDto> list() {
        return storage.list();
    }

    @GetMapping("/heapdumps/{id}")
    public HeapDumpInfoDto get(@PathVariable String id) {
        return storage.get(id);
    }

    @DeleteMapping("/heapdumps/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        storage.delete(id);
        return ResponseEntity.noContent().build();
    }
}