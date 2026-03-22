package com.joz.common.model;

/** Result of executing a tool — either success or error. */
public sealed interface ToolResult permits ToolResult.ToolSuccess, ToolResult.ToolError {

    /** Successful tool execution with output text. */
    record ToolSuccess(String output) implements ToolResult {}

    /** Failed tool execution with error details. */
    record ToolError(String error, ErrorKind kind) implements ToolResult {}

    /** Categories of tool execution errors. */
    enum ErrorKind {
        PERMISSION_DENIED,
        NOT_FOUND,
        EXECUTION_FAILED,
        TIMEOUT,
        INVALID_INPUT
    }
}
