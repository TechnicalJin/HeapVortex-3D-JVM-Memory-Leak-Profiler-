package com.heapvortex.controller;

import com.heapvortex.dto.JvmInfoDto;
import com.heapvortex.dto.JvmMetricsDto;
import com.heapvortex.exception.JvmNotFoundException;
import com.heapvortex.service.JvmDiscoveryService;
import com.heapvortex.service.JvmTelemetryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jvms")
public class JvmController {

    private final JvmDiscoveryService discoveryService;
    private final JvmTelemetryService telemetryService;

    public JvmController(JvmDiscoveryService discoveryService, JvmTelemetryService telemetryService) {
        this.discoveryService = discoveryService;
        this.telemetryService = telemetryService;
    }

    @GetMapping
    public List<JvmInfoDto> list() {
        return discoveryService.listJvms();
    }

    @GetMapping("/{pid}/metrics")
    public JvmMetricsDto metrics(@PathVariable String pid) {
        if (!discoveryService.exists(pid)) {
            throw new JvmNotFoundException(pid);
        }
        return telemetryService.collect(pid);
    }
}