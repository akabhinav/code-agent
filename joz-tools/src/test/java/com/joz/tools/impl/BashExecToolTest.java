package com.joz.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joz.common.model.ToolResult;
import com.joz.tools.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BashExecToolTest {

    private BashExecTool tool;
    private ObjectMapper mapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        tool = new BashExecTool();
        mapper = new ObjectMapper();
    }

    @Test
    void executesSimpleCommand() {
        var input = mapper.createObjectNode().put("command", "echo hello");
        var ctx = new ExecutionContext(tempDir, "s1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolSuccess.class, result);
        assertTrue(((ToolResult.ToolSuccess) result).output().contains("hello"));
    }

    @Test
    void capturesExitCode() {
        var input = mapper.createObjectNode().put("command", "exit 42");
        var ctx = new ExecutionContext(tempDir, "s1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolSuccess.class, result);
        assertTrue(((ToolResult.ToolSuccess) result).output().contains("Exit code: 42"));
    }

    @Test
    void handlesTimeout() {
        var input = mapper.createObjectNode()
                .put("command", "sleep 30")
                .put("timeout_seconds", 1);
        var ctx = new ExecutionContext(tempDir, "s1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolError.class, result);
        assertEquals(ToolResult.ErrorKind.TIMEOUT, ((ToolResult.ToolError) result).kind());
    }
}
