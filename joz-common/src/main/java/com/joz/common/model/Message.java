package com.joz.common.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Core message types for the conversation history. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "role")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Message.UserMessage.class, name = "user"),
    @JsonSubTypes.Type(value = Message.AssistantMessage.class, name = "assistant"),
    @JsonSubTypes.Type(value = Message.SystemMessage.class, name = "system"),
    @JsonSubTypes.Type(value = Message.ToolResultMessage.class, name = "tool_result")
})
public sealed interface Message
        permits Message.UserMessage, Message.AssistantMessage,
                Message.SystemMessage, Message.ToolResultMessage {

    /** A message from the user. */
    record UserMessage(String content) implements Message {}

    /** A message from the assistant, optionally containing tool calls. */
    record AssistantMessage(String text, List<ToolCall> toolCalls) implements Message {
        public AssistantMessage {
            toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
        }
    }

    /** A system prompt message. */
    record SystemMessage(String content) implements Message {}

    /** A tool result returned to the assistant. */
    record ToolResultMessage(String toolUseId, String toolName, String content) implements Message {}
}
