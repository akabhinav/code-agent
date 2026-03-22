package com.joz.common.config;

/** LLM provider configuration. */
public record LLMConfig(
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        double temperature) {

    /** Default config for Anthropic Claude. */
    public static LLMConfig defaults() {
        return new LLMConfig(
                "anthropic",
                "claude-sonnet-4-6-20250514",
                System.getenv("ANTHROPIC_API_KEY"),
                "https://api.anthropic.com",
                0.0);
    }

    /** Default config for local Ollama. */
    public static LLMConfig localDefaults() {
        return new LLMConfig(
                "ollama",
                "qwen2.5-coder:14b",
                null,
                "http://localhost:11434",
                0.0);
    }

    /** Merges with overlay — overlay values win when non-null/non-default. */
    public LLMConfig merge(LLMConfig overlay) {
        if (overlay == null) return this;
        return new LLMConfig(
                overlay.provider != null ? overlay.provider : provider,
                overlay.model != null ? overlay.model : model,
                overlay.apiKey != null ? overlay.apiKey : apiKey,
                overlay.baseUrl != null ? overlay.baseUrl : baseUrl,
                overlay.temperature != 0.0 ? overlay.temperature : temperature);
    }
}
