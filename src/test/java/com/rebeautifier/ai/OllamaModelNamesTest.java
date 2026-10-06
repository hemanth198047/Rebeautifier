package com.rebeautifier.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaModelNamesTest {

    @Test
    void detectsCloudApiModelIds() {
        assertTrue(OllamaModelNames.looksLikeCloudApiModel("gpt-4o-mini"));
        assertTrue(OllamaModelNames.looksLikeCloudApiModel("GPT-4o"));
        assertTrue(OllamaModelNames.looksLikeCloudApiModel("o1-mini"));
        assertTrue(OllamaModelNames.looksLikeCloudApiModel("claude-3-5-sonnet"));
        assertTrue(OllamaModelNames.looksLikeCloudApiModel("gemini-1.5-flash"));
        assertFalse(OllamaModelNames.looksLikeCloudApiModel("llama3.2:1b"));
        assertFalse(OllamaModelNames.looksLikeCloudApiModel("mistral"));
        assertFalse(OllamaModelNames.looksLikeCloudApiModel("qwen2.5:7b"));
    }

    @Test
    void detectsMissingLibraryErrors() {
        assertTrue(OllamaModelNames.looksLikeMissingLibraryModel(
                "pull model manifest: file does not exist"));
        assertTrue(OllamaModelNames.looksLikeMissingLibraryModel("model 'foo' not found"));
        assertFalse(OllamaModelNames.looksLikeMissingLibraryModel(
                "dial tcp 104.18.16.170:443: i/o timeout"));
    }

    @Test
    void sanitizeForOllamaReplacesCloudIds() {
        assertEquals("llama3.2:1b", OllamaModelNames.sanitizeForOllama("gpt-4o-mini", "llama3.2:1b"));
        assertEquals("llama3.2:1b", OllamaModelNames.sanitizeForOllama("GPT-4o", null));
        assertEquals("mistral", OllamaModelNames.sanitizeForOllama("mistral", "llama3.2:1b"));
    }
}
