package com.rebeautifier.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WelcomeService {

    public Map<String, String> welcome() {
        return Map.of(
                "app", "Rebeautifier",
                "message", "Welcome — ready to read and beautify thoughts."
        );
    }
}
