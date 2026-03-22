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

class FileWriteToolTest {

    private FileWriteTool tool;
    private ObjectMapper mapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        tool = new FileWriteTool();
        mapper = new ObjectMapper();
    }

    @Test
    void createNewFile() throws IOException {
        var input = mapper.createObjectNode()
                .put("path", "new.txt")
                .put("mode", "create")
                .put("content", "hello world");
        var ctx = new ExecutionContext(tempDir, "s1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolSuccess.class, result);
        assertEquals("hello world", Files.readString(tempDir.resolve("new.txt")));
    }

    @Test
    void createFailsIfFileExists() throws IOException {
        Files.writeString(tempDir.resolve("exists.txt"), "old");
        var input = mapper.createObjectNode()
                .put("path", "exists.txt")
                .put("mode", "create")
                .put("content", "new");
        var ctx = new ExecutionContext(tempDir, "s1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolError.class, result);
    }

    @Test
    void patchReplacesExactMatch() throws IOException {
        Files.writeString(tempDir.resolve("code.java"), "return user.getName();");
        var input = mapper.createObjectNode()
                .put("path", "code.java")
                .put("mode", "patch")
                .put("old_str", "return user.getName();")
                .put("new_str", "return user != null ? user.getName() : null;");
        var ctx = new ExecutionContext(tempDir, "s1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolSuccess.class, result);
        assertEquals("return user != null ? user.getName() : null;",
                Files.readString(tempDir.resolve("code.java")));
    }

    @Test
    void patchFailsOnMultipleMatches() throws IOException {
        Files.writeString(tempDir.resolve("dup.txt"), "foo bar foo");
        var input = mapper.createObjectNode()
                .put("path", "dup.txt")
                .put("mode", "patch")
                .put("old_str", "foo")
                .put("new_str", "baz");
        var ctx = new ExecutionContext(tempDir, "s1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolError.class, result);
        assertTrue(((ToolResult.ToolError) result).error().contains("Multiple"));
    }
}
