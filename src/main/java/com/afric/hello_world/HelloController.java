package com.afric.hello_world;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HelloController {

    @Value("${app.environment:production}")
    private String environment;

    @Value("${app.version:1.0.0}")
    private String version;

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of(
                "message", "Hello from @PS",
                "environment", environment,
                "version", version,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "afric-hello-world"
        );
    }
}
