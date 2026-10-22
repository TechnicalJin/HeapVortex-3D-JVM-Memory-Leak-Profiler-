package com.heapvortex.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI heapVortexOpenApi() {
        return new OpenAPI().info(new Info()
                .title("HeapVortex API")
                .version("0.1.0")
                .description("JVM discovery and live JMX telemetry"));
    }
}