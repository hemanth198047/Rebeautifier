package com.rebeautifier.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WelcomeService {

    private final String appVersion;

    public WelcomeService(@Value("${rebeautifier.app-version:0.6.0}") String appVersion) {
        this.appVersion = appVersion;
    }

    public Map<String, String> welcome() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("app", "Rebeautifier");
        body.put("version", appVersion);
        body.put("message", "Welcome — ready to read and beautify thoughts.");
        return body;
    }
}
