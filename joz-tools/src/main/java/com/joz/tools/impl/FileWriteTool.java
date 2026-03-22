package com.joz.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.joz.common.model.ToolCategory;
import com.joz.common.model.ToolResult;
import com.joz.common.model.ToolResult.ErrorKind;
import com.joz.common.model.ToolResult.ToolError;
import com.joz.common.model.ToolResult.ToolSuccess;
import com.joz.common.schema.ToolSchemaBuilder;
import com.joz.common.schema.ToolSchemaBuilder.Property;
import com.joz.tools.ExecutionContext;
import com.joz.tools.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Create, overwrite, or patch a file. */
@Component
public class FileWriteTool implements Tool {

    @Override
    public String name() {
        return "file_write";
    }

    @Override
    public String description() {
        return "Create, overwrite, or patch a file. Use mode 'patch' for str_replace edits.";
    }

    @Override
    public JsonNode inputSchema() {
        return ToolSchemaBuilder.objectSchema(
                List.of(
                        Property.of("path", "string", "File path relative to project root"),
                        Property.of("content", "string", "File content (for create/overwrite modes)"),
                        Property.ofEnum("mode", "Write mode", List.of("create", "overwrite", "patch")),
                        Property.of("old_str", "string", "Exact string to find (patch mode only)"),
                        Property.of("new_str", "string", "Replacement string (patch mode only)")),
                List.of("path", "mode"));
    }

    @Override
    public ToolResult execute(JsonNode input, ExecutionContext ctx) {
        var relativePath = input.get("path").asText();
        var mode = input.get("mode").asText();

        if (relativePath.startsWith("/") || relativePath.contains("..")) {
            return new ToolError("Absolute paths and '..' are not allowed", ErrorKind.INVALID_INPUT);
        }

        var path = ctx.projectRoot().resolve(relativePath).normalize();
        if (!path.startsWith(ctx.projectRoot())) {
            return new ToolError("Path escapes project root", ErrorKind.INVALID_INPUT);
        }

        return switch (mode) {
            case "create" -> createFile(path, input, relativePath);
            case "overwrite" -> overwriteFile(path, input, relativePath);
            case "patch" -> patchFile(path, input, relativePath);
            default -> new ToolError("Unknown mode: " + mode, ErrorKind.INVALID_INPUT);
        };
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.WRITE;
    }

    private ToolResult createFile(Path path, JsonNode input, String relativePath) {
        if (Files.exists(path)) {
            return new ToolError("File already exists: " + relativePath + ". Use 'overwrite' mode.", ErrorKind.INVALID_INPUT);
        }
        return writeContent(path, input, relativePath);
    }

    private ToolResult overwriteFile(Path path, JsonNode input, String relativePath) {
        return writeContent(path, input, relativePath);
    }

    private ToolResult writeContent(Path path, JsonNode input, String relativePath) {
        if (!input.has("content")) {
            return new ToolError("'content' is required for create/overwrite mode", ErrorKind.INVALID_INPUT);
        }
        var content = input.get("content").asText();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            return new ToolSuccess("Wrote %d bytes to %s".formatted(content.getBytes().length, relativePath));
        } catch (IOException e) {
            return new ToolError("Failed to write file: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }

    private ToolResult patchFile(Path path, JsonNode input, String relativePath) {
        if (!input.has("old_str") || !input.has("new_str")) {
            return new ToolError("'old_str' and 'new_str' are required for patch mode", ErrorKind.INVALID_INPUT);
        }

        if (!Files.exists(path)) {
            return new ToolError("File not found: " + relativePath, ErrorKind.NOT_FOUND);
        }

        var oldStr = input.get("old_str").asText();
        var newStr = input.get("new_str").asText();

        try {
            var content = Files.readString(path);
            int firstIdx = content.indexOf(oldStr);
            if (firstIdx == -1) {
                return new ToolError("String not found in file", ErrorKind.INVALID_INPUT);
            }

            int secondIdx = content.indexOf(oldStr, firstIdx + 1);
            if (secondIdx != -1) {
                return new ToolError("Multiple matches found — be more specific", ErrorKind.INVALID_INPUT);
            }

            var newContent = content.substring(0, firstIdx) + newStr + content.substring(firstIdx + oldStr.length());
            Files.writeString(path, newContent);
            return new ToolSuccess("Patched %s: replaced %d chars".formatted(relativePath, oldStr.length()));
        } catch (IOException e) {
            return new ToolError("Failed to patch file: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }
}
