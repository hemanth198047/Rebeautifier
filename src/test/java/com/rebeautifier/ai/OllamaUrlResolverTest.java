package com.rebeautifier.ai;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaUrlResolverTest {

    @Test
    void candidatesPreferConfiguredUrlThenCommonFallbacks() {
        List<String> urls = OllamaUrlResolver.candidates("http://ollama:11434/");
        assertEquals("http://ollama:11434", urls.get(0));
        assertTrue(urls.contains("http://host.docker.internal:11434"));
        assertTrue(urls.contains("http://127.0.0.1:11434"));
        assertTrue(urls.contains("http://localhost:11434"));
    }

    @Test
    void trimTrailingSlashDefaultsBlankToLocalhost() {
        assertEquals("http://localhost:11434", OllamaUrlResolver.trimTrailingSlash("  "));
        assertEquals("http://ollama:11434", OllamaUrlResolver.trimTrailingSlash("http://ollama:11434/"));
    }

    @Test
    void looksLikeDnsFailureDetectsUnresolvedAddress() {
        assertTrue(OllamaUrlResolver.looksLikeDnsFailure(
                new java.nio.channels.UnresolvedAddressException()));
        assertTrue(OllamaUrlResolver.looksLikeDnsFailure(
                new RuntimeException(new java.net.UnknownHostException("ollama"))));
        assertFalse(OllamaUrlResolver.looksLikeDnsFailure(new RuntimeException("connection refused")));
    }

    @Test
    void findReachableReturnsACandidateFromTheList() {
        HttpClient client = HttpClient.newHttpClient();
        String preferred = "http://definitely-not-a-real-ollama-host.invalid:11434";
        String resolved = OllamaUrlResolver.findReachable(client, preferred);
        assertTrue(OllamaUrlResolver.candidates(preferred).contains(resolved));
    }

    @Test
    void unresolvedHintMentionsComposeService() {
        String hint = OllamaUrlResolver.unresolvedHint("http://ollama:11434");
        assertTrue(hint.contains("http://ollama:11434"));
        assertTrue(hint.contains("start.sh") || hint.contains("start.bat"));
    }
}
