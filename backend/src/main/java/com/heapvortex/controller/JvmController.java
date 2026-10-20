package com.heapvortex.controller;

import com.heapvortex.dto.JvmInfoDto;
import com.heapvortex.service.JvmDiscoveryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jvms")
public class JvmController {

    private final JvmDiscoveryService discoveryService;

    public JvmController(JvmDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping
    public List<JvmInfoDto> list() {
        return discoveryService.listJvms();
    }
}