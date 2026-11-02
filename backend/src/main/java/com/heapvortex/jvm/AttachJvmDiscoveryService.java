package com.heapvortex.jvm;

import com.heapvortex.dto.JvmInfoDto;
import com.heapvortex.service.JvmDiscoveryService;
import com.sun.tools.attach.VirtualMachine;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AttachJvmDiscoveryService implements JvmDiscoveryService {

    @Override
    public List<JvmInfoDto> listJvms() {
        String ownPid = String.valueOf(ProcessHandle.current().pid());
        return VirtualMachine.list().stream()
                .filter(descriptor -> !descriptor.id().equals(ownPid))
                .map(descriptor -> new JvmInfoDto(descriptor.id(), descriptor.displayName()))
                .toList();
    }

    @Override
    public boolean exists(String pid) {
        return listJvms().stream().anyMatch(jvm -> jvm.pid().equals(pid));
    }
}