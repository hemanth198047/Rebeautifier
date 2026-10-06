package com.rebeautifier.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "rebeautifier");
    }

    @GetMapping("/ready")
    public Map<String, Object> ready() {
        boolean dbUp = false;
        try (Connection connection = dataSource.getConnection()) {
            dbUp = connection.isValid(2);
        } catch (Exception ignored) {
            dbUp = false;
        }
        return Map.of(
                "status", dbUp ? "UP" : "DOWN",
                "database", dbUp ? "UP" : "DOWN"
        );
    }
}
