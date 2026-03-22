package com.joz.common.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultTest {

    @Test
    void successContainsOutput() {
        var result = new ToolResult.ToolSuccess("file contents here");
        assertInstanceOf(ToolResult.ToolSuccess.class, result);
        assertEquals("file contents here", result.output());
    }

    @Test
    void errorContainsKind() {
        var result = new ToolResult.ToolError("file not found", ToolResult.ErrorKind.NOT_FOUND);
        assertInstanceOf(ToolResult.ToolError.class, result);
        assertEquals(ToolResult.ErrorKind.NOT_FOUND, result.kind());
    }

    @Test
    void exhaustiveSwitchOnToolResult() {
        ToolResult result = new ToolResult.ToolSuccess("ok");
        var text = switch (result) {
            case ToolResult.ToolSuccess(var output) -> "success: " + output;
            case ToolResult.ToolError(var error, var kind) -> "error: " + error;
        };
        assertEquals("success: ok", text);
    }
}
