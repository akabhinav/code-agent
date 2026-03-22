package com.joz.llm.provider;

import com.joz.llm.ChatRequest;
import com.joz.llm.LLMResponse;
import java.util.List;

/** SPI interface for LLM providers. */
public interface LLMProvider {

    /** Provider name (e.g. "anthropic", "ollama"). */
    String name();

    /** Send a chat request and return the response (synchronous). */
    LLMResponse chat(ChatRequest request);

    /** List of supported model identifiers. */
    List<String> supportedModels();
}
