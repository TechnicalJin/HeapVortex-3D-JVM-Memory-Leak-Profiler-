package com.heapvortex.controller;

import com.heapvortex.dto.JvmInfoDto;
import com.heapvortex.jvm.JmxConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The TLS-protected JMX targets from heapvortex.jmx.remote-targets, for example "localhost:9010,10.0.0.5:9010". */
@RestController
@RequestMapping("/api/remote-jvms")
public class RemoteJvmController {

    private static final Pattern TARGET = Pattern.compile("([A-Za-z0-9._-]+):(\\d{1,5})");

    private final List<JvmInfoDto> targets;

    public RemoteJvmController(@Value("${heapvortex.jmx.remote-targets:}") String configured) {
        List<JvmInfoDto> list = new ArrayList<>();
        for (String entry : configured.split(",")) {
            Matcher matcher = TARGET.matcher(entry.trim());
            if (!matcher.matches()) {
                continue;
            }
            int port = Integer.parseInt(matcher.group(2));
            if (port >= 1 && port <= 65535) {
                list.add(new JvmInfoDto(
                        JmxConnectionManager.remotePid(matcher.group(1), port),
                        "Secure JMX " + entry.trim() + " (TLS)"));
            }
        }
        this.targets = List.copyOf(list);
    }

    @GetMapping
    public List<JvmInfoDto> list() {
        return targets;
    }
}