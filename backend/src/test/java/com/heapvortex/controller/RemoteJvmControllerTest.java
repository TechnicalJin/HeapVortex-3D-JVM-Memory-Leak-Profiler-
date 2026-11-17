package com.heapvortex.controller;

import com.heapvortex.dto.JvmInfoDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteJvmControllerTest {

    @Test
    void listsOnlyValidTargets() {
        List<JvmInfoDto> list = new RemoteJvmController("localhost:9010, bad, host:99999, 127.0.0.1:9011").list();

        assertEquals(2, list.size());
        assertEquals("remote:localhost:9010", list.get(0).pid());
        assertEquals("remote:127.0.0.1:9011", list.get(1).pid());
    }

    @Test
    void anEmptySettingGivesAnEmptyList() {
        assertTrue(new RemoteJvmController("").list().isEmpty());
    }
}