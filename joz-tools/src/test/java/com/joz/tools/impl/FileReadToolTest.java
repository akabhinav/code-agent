package com.joz.tools.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joz.common.model.ToolResult;
import com.joz.tools.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileReadToolTest {

    private FileReadTool tool;
    private ObjectMapper mapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        tool = new FileReadTool();
        mapper = new ObjectMapper();
    }

    @Test
    void readExistingFile() throws IOException {
        Files.writeString(tempDir.resolve("test.txt"), "line1\nline2\nline3\n");
        var input = mapper.createObjectNode().put("path", "test.txt");
        var ctx = new ExecutionContext(tempDir, "session-1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolSuccess.class, result);
        var output = ((ToolResult.ToolSuccess) result).output();
        assertTrue(output.contains("line1"));
        assertTrue(output.contains("line2"));
    }

    @Test
    void readNonexistentFileReturnsError() {
        var input = mapper.createObjectNode().put("path", "missing.txt");
        var ctx = new ExecutionContext(tempDir, "session-1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolError.class, result);
        assertEquals(ToolResult.ErrorKind.NOT_FOUND, ((ToolResult.ToolError) result).kind());
    }

    @Test
    void rejectsPathTraversal() {
        var input = mapper.createObjectNode().put("path", "../etc/passwd");
        var ctx = new ExecutionContext(tempDir, "session-1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolError.class, result);
        assertEquals(ToolResult.ErrorKind.INVALID_INPUT, ((ToolResult.ToolError) result).kind());
    }

    @Test
    void readWithLineRange() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "a\nb\nc\nd\ne\n");
        var input = mapper.createObjectNode()
                .put("path", "lines.txt")
                .put("start_line", 2)
                .put("end_line", 4);
        var ctx = new ExecutionContext(tempDir, "session-1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolSuccess.class, result);
        var output = ((ToolResult.ToolSuccess) result).output();
        assertTrue(output.contains("b"));
        assertTrue(output.contains("c"));
        assertTrue(output.contains("d"));
        assertFalse(output.contains("\t" + "a"));
    }
}
