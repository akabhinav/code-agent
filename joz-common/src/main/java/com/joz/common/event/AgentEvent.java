package com.joz.common.event;

import com.joz.common.model.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;

/** Events emitted by the agent loop — consumed by renderers. */
public sealed interface AgentEvent
        permits AgentEvent.ThinkingEvent, AgentEvent.ToolCallEvent,
                AgentEvent.ToolResultEvent, AgentEvent.TextChunkEvent,
                AgentEvent.PlanUpdateEvent, AgentEvent.CompletionEvent,
                AgentEvent.ErrorEvent {

    /** Agent is reasoning about the next step. */
    record ThinkingEvent(String thought) implements AgentEvent {}

    /** Agent wants to call a tool. */
    record ToolCallEvent(String id, String toolName, JsonNode input, boolean needsApproval)
            implements AgentEvent {}

    /** A tool has returned its result. */
    record ToolResultEvent(String id, String toolName, ToolResult result)
            implements AgentEvent {}

    /** A chunk of streaming text from the LLM. */
    record TextChunkEvent(String text) implements AgentEvent {}

    /** A plan step has been updated. */
    record PlanUpdateEvent(String planId, int stepIndex, String status)
            implements AgentEvent {}

    /** The agent loop has completed. */
    record CompletionEvent(String summary, int turns, int toolCalls, Duration elapsed)
            implements AgentEvent {}

    /** An error occurred during agent execution. */
    record ErrorEvent(String message, Throwable cause) implements AgentEvent {}
}
