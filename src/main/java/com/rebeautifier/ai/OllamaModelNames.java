package com.rebeautifier.ai;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Helps distinguish Ollama library models from cloud/API model ids (e.g. gpt-4o-mini).
 */
public final class OllamaModelNames {

    public static final String DEFAULT_OLLAMA_MODEL = "llama3.2:1b";

    private static final Pattern CLOUD_API_MODEL = Pattern.compile(
            "^(gpt-|chatgpt-|o[0-9]|o1-|o3-|o4-|claude-|gemini-|text-embedding-|davinci|curie|babbage|ada)",
            Pattern.CASE_INSENSITIVE);

    private OllamaModelNames() {
    }

    public static boolean looksLikeCloudApiModel(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String trimmed = model.trim();
        return CLOUD_API_MODEL.matcher(trimmed).find();
    }

    /**
     * When Local Ollama is selected, cloud/API model ids are invalid — replace with the default Ollama model.
     */
    public static String sanitizeForOllama(String model, String fallback) {
        String defaultModel = fallback == null || fallback.isBlank() || looksLikeCloudApiModel(fallback)
                ? DEFAULT_OLLAMA_MODEL
                : fallback.trim();
        if (model == null || model.isBlank() || looksLikeCloudApiModel(model)) {
            return defaultModel;
        }
        return model.trim();
    }

    public static boolean looksLikeMissingLibraryModel(String registryErr) {
        if (registryErr == null || registryErr.isBlank()) {
            return false;
        }
        String lower = registryErr.toLowerCase(Locale.ROOT);
        return lower.contains("file does not exist")
                || lower.contains("not found")
                || lower.contains("model '") && lower.contains("' not found")
                || lower.contains("404");
    }

    public static String cloudApiModelHint(String model) {
        return "“" + model + "” is an API/cloud model id, not an Ollama library model. "
                + "With Local Ollama selected, use a library name such as " + DEFAULT_OLLAMA_MODEL + ". "
                + "To use “" + model + "”, switch Provider to “API key (OpenAI-compatible)”, add your API key, and Save — do not use Pull model.";
    }

    public static String missingLibraryModelHint(String model) {
        if (looksLikeCloudApiModel(model)) {
            return cloudApiModelHint(model);
        }
        return "Ollama has no library model named “" + model + "”. "
                + "Pick an Ollama model such as " + DEFAULT_OLLAMA_MODEL + " (see https://ollama.com/library), "
                + "or switch Provider to “API key (OpenAI-compatible)” for OpenAI-style model ids.";
    }
}
