package com.joz.llm;

import com.joz.common.model.Message;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Request to send to an LLM provider. */
public record ChatRequest(
        String systemPrompt,
        List<Message> messages,
        List<ToolDefinition> tools,
        String model,
        double temperature,
        int maxTokens) {

    public ChatRequest {
        messages = List.copyOf(messages);
        tools = tools != null ? List.copyOf(tools) : List.of();
    }

    /** A tool definition to send to the LLM. */
    public record ToolDefinition(String name, String description, JsonNode inputSchema) {}
}
