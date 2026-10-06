package com.rebeautifier.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaModelMatchTest {

    @Test
    void modelMatchesExactAndTagVariants() {
        assertTrue(OllamaClient.modelMatches("llama3.2:1b", "llama3.2:1b"));
        assertTrue(OllamaClient.modelMatches("llama3.2", "llama3.2:1b"));
        assertTrue(OllamaClient.modelMatches("llama3.2:1b", "llama3.2"));
        assertTrue(OllamaClient.modelMatches("mistral", "mistral:latest"));
        assertFalse(OllamaClient.modelMatches("llama3.2:1b", "mistral:latest"));
        assertFalse(OllamaClient.modelMatches("gpt-4o-mini", "llama3.2:1b"));
    }
}
