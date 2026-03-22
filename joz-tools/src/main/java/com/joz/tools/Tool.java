package com.joz.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.joz.common.model.ToolCategory;
import com.joz.common.model.ToolResult;

/** Core interface for all agent tools. */
public interface Tool {

    /** Unique tool name (e.g. "bash_exec"). */
    String name();

    /** Human-readable description of what this tool does. */
    String description();

    /** JSON Schema for the tool's input parameters. */
    JsonNode inputSchema();

    /** Execute the tool with the given input and context. */
    ToolResult execute(JsonNode input, ExecutionContext ctx);

    /** Permission category for this tool. */
    ToolCategory category();
}
