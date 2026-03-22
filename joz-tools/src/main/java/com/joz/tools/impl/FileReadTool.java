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
import java.util.stream.Collectors;

/** Read a file's contents, optionally a specific line range. */
@Component
public class FileReadTool implements Tool {

    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB

    @Override
    public String name() {
        return "file_read";
    }

    @Override
    public String description() {
        return "Read a file's contents, optionally a specific line range.";
    }

    @Override
    public JsonNode inputSchema() {
        return ToolSchemaBuilder.objectSchema(
                List.of(
                        Property.of("path", "string", "File path relative to project root"),
                        Property.of("start_line", "integer", "Start line (1-indexed, optional)"),
                        Property.of("end_line", "integer", "End line (inclusive, -1 for end of file, optional)")),
                List.of("path"));
    }

    @Override
    public ToolResult execute(JsonNode input, ExecutionContext ctx) {
        var relativePath = input.get("path").asText();

        if (relativePath.startsWith("/") || relativePath.contains("..")) {
            return new ToolError("Absolute paths and '..' are not allowed", ErrorKind.INVALID_INPUT);
        }

        var path = ctx.projectRoot().resolve(relativePath).normalize();
        if (!path.startsWith(ctx.projectRoot())) {
            return new ToolError("Path escapes project root", ErrorKind.INVALID_INPUT);
        }

        if (!Files.exists(path)) {
            return new ToolError("File not found: " + relativePath, ErrorKind.NOT_FOUND);
        }

        if (Files.isDirectory(path)) {
            return new ToolError("Path is a directory, not a file: " + relativePath, ErrorKind.INVALID_INPUT);
        }

        try {
            if (Files.size(path) > MAX_FILE_SIZE) {
                return readWithLineRange(path, 1, 500,
                        "[truncated — file exceeds 1MB, showing first 500 lines]");
            }

            // Check for binary content
            if (isBinary(path)) {
                return new ToolError("File appears to be binary: " + relativePath, ErrorKind.INVALID_INPUT);
            }

            var startLine = input.has("start_line") ? input.get("start_line").asInt() : -1;
            var endLine = input.has("end_line") ? input.get("end_line").asInt() : -1;

            if (startLine > 0 || endLine > 0) {
                return readWithLineRange(path, Math.max(startLine, 1), endLine, null);
            }

            return readWholeFile(path);
        } catch (IOException e) {
            return new ToolError("Failed to read file: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.READ;
    }

    private ToolResult readWholeFile(Path path) throws IOException {
        var lines = Files.readAllLines(path);
        var result = formatLines(lines, 1);
        return new ToolSuccess(result);
    }

    private ToolResult readWithLineRange(Path path, int start, int end, String notice) throws IOException {
        var allLines = Files.readAllLines(path);
        int effectiveEnd = (end <= 0 || end > allLines.size()) ? allLines.size() : end;
        int effectiveStart = Math.max(1, start);

        if (effectiveStart > allLines.size()) {
            return new ToolError("Start line %d exceeds file length %d".formatted(effectiveStart, allLines.size()),
                    ErrorKind.INVALID_INPUT);
        }

        var subset = allLines.subList(effectiveStart - 1, effectiveEnd);
        var result = formatLines(subset, effectiveStart);
        if (notice != null) {
            result = result + "\n" + notice;
        }
        return new ToolSuccess(result);
    }

    private String formatLines(List<String> lines, int startNum) {
        var sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append("%4d\t%s%n".formatted(startNum + i, lines.get(i)));
        }
        return sb.toString().stripTrailing();
    }

    private boolean isBinary(Path path) throws IOException {
        try (var is = Files.newInputStream(path)) {
            var buffer = is.readNBytes(8192);
            for (byte b : buffer) {
                if (b == 0) return true;
            }
            return false;
        }
    }
}
