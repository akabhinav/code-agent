package com.joz.llm;

import com.joz.common.model.ToolCall;
import java.util.List;

/** Response from an LLM provider. */
public record LLMResponse(
        String text,
        List<ToolCall> toolCalls,
        StopReason stopReason,
        Usage usage) {

    public LLMResponse {
        toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
    }

    /** Whether the model wants to call tools. */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /** Why the model stopped generating. */
    public enum StopReason {
        END_TURN,
        TOOL_USE,
        MAX_TOKENS,
        ERROR
    }

    /** Token usage stats. */
    public record Usage(int inputTokens, int outputTokens) {}
}
