package com.rebeautifier.controller;

import com.rebeautifier.service.WelcomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    private final WelcomeService welcomeService;

    public HomeController(WelcomeService welcomeService) {
        this.welcomeService = welcomeService;
    }

    @GetMapping("/")
    public Map<String, String> home() {
        return welcomeService.welcome();
    }
}
