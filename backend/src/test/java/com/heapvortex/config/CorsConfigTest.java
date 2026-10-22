package com.heapvortex.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {

    private CorsConfiguration apiConfiguration() {
        // the anonymous subclass makes the protected getCorsConfigurations() reachable
        var registry = new CorsRegistry() {
            Map<String, CorsConfiguration> configurations() {
                return getCorsConfigurations();
            }
        };
        new CorsConfig().addCorsMappings(registry);
        return registry.configurations().get("/api/**");
    }

    @Test
    void apiPathsAreConfigured() {
        assertNotNull(apiConfiguration());
    }

    @Test
    void viteDevServerOriginIsAllowed() {
        assertTrue(apiConfiguration().getAllowedOrigins().contains("http://localhost:5173"));
    }

    @Test
    void onlyReadMethodsAreAllowed() {
        assertTrue(apiConfiguration().getAllowedMethods().contains("GET"));
        assertFalse(apiConfiguration().getAllowedMethods().contains("DELETE"));
    }
}