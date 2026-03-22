package com.joz.common.model;

import com.fasterxml.jackson.databind.JsonNode;

/** A tool invocation requested by the LLM. */
public record ToolCall(String id, String name, JsonNode input) {}
