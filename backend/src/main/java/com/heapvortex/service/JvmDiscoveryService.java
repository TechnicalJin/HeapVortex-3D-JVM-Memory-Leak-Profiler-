package com.heapvortex.service;

import com.heapvortex.dto.JvmInfoDto;

import java.util.List;

public interface JvmDiscoveryService {

    /** Returns all local JVMs that can be attached to, excluding the backend itself. */
    List<JvmInfoDto> listJvms();

    boolean exists(String pid);
}