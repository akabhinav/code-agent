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

class ListDirectoryToolTest {

    private ListDirectoryTool tool;
    private ObjectMapper mapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        tool = new ListDirectoryTool();
        mapper = new ObjectMapper();
    }

    @Test
    void listsFilesAndDirectories() throws IOException {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("README.md"), "# test");
        Files.writeString(tempDir.resolve("src/Main.java"), "class Main {}");

        var input = mapper.createObjectNode();
        var ctx = new ExecutionContext(tempDir, "s1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolSuccess.class, result);
        var output = ((ToolResult.ToolSuccess) result).output();
        assertTrue(output.contains("README.md"));
        assertTrue(output.contains("src/"));
    }

    @Test
    void skipsGitDirectory() throws IOException {
        Files.createDirectories(tempDir.resolve(".git"));
        Files.writeString(tempDir.resolve("file.txt"), "content");

        var input = mapper.createObjectNode();
        var ctx = new ExecutionContext(tempDir, "s1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolSuccess.class, result);
        var output = ((ToolResult.ToolSuccess) result).output();
        assertFalse(output.contains(".git"));
        assertTrue(output.contains("file.txt"));
    }

    @Test
    void rejectsPathOutsideRoot() {
        var input = mapper.createObjectNode().put("path", "../../etc");
        var ctx = new ExecutionContext(tempDir, "s1", Map.of());

        var result = tool.execute(input, ctx);
        assertInstanceOf(ToolResult.ToolError.class, result);
    }
}
