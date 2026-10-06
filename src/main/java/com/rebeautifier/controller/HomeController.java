package com.rebeautifier.controller;

import com.rebeautifier.service.WelcomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Retained from the original scaffold; API-prefixed so it does not collide with the SPA.
 */
@RestController
public class HomeController {

    private final WelcomeService welcomeService;

    public HomeController(WelcomeService welcomeService) {
        this.welcomeService = welcomeService;
    }

    @GetMapping("/api/v1")
    public Map<String, String> home() {
        return welcomeService.welcome();
    }
}
