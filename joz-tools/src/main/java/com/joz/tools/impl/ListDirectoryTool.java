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
import java.util.Set;
import java.util.stream.Stream;

/** List directory contents as a tree. */
@Component
public class ListDirectoryTool implements Tool {

    private static final int MAX_ENTRIES = 200;
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "__pycache__", ".joz",
            ".gradle", ".idea", ".vscode", "dist", ".next", "vendor");

    @Override
    public String name() {
        return "list_directory";
    }

    @Override
    public String description() {
        return "List directory contents as a tree.";
    }

    @Override
    public JsonNode inputSchema() {
        return ToolSchemaBuilder.objectSchema(
                List.of(
                        Property.of("path", "string", "Directory path relative to project root (optional)"),
                        Property.withDefault("max_depth", "integer", "Maximum depth to traverse", "3")),
                List.of());
    }

    @Override
    public ToolResult execute(JsonNode input, ExecutionContext ctx) {
        var relativePath = input.has("path") ? input.get("path").asText() : ".";
        var maxDepth = input.has("max_depth") ? input.get("max_depth").asInt() : 3;

        var dir = ctx.projectRoot().resolve(relativePath).normalize();
        if (!dir.startsWith(ctx.projectRoot())) {
            return new ToolError("Path escapes project root", ErrorKind.INVALID_INPUT);
        }
        if (!Files.isDirectory(dir)) {
            return new ToolError("Not a directory: " + relativePath, ErrorKind.INVALID_INPUT);
        }

        try {
            var sb = new StringBuilder();
            int[] count = {0};
            int[] total = {0};
            buildTree(dir, dir, "", maxDepth, 0, sb, count, total);
            if (total[0] > MAX_ENTRIES) {
                sb.append("\n[... and %d more entries]".formatted(total[0] - MAX_ENTRIES));
            }
            return new ToolSuccess(sb.toString().strip());
        } catch (IOException e) {
            return new ToolError("Failed to list directory: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.READ;
    }

    private void buildTree(Path root, Path dir, String indent, int maxDepth, int currentDepth,
                           StringBuilder sb, int[] count, int[] total) throws IOException {
        if (currentDepth > maxDepth) return;

        try (Stream<Path> entries = Files.list(dir).sorted()) {
            var sortedEntries = entries.toList();
            for (int i = 0; i < sortedEntries.size(); i++) {
                var entry = sortedEntries.get(i);
                var name = entry.getFileName().toString();

                if (SKIP_DIRS.contains(name)) continue;

                total[0]++;
                if (count[0] >= MAX_ENTRIES) continue;
                count[0]++;

                boolean isLast = (i == sortedEntries.size() - 1);
                var connector = isLast ? "└── " : "├── ";
                var childIndent = indent + (isLast ? "    " : "│   ");

                if (Files.isDirectory(entry)) {
                    sb.append(indent).append(connector).append(name).append("/\n");
                    buildTree(root, entry, childIndent, maxDepth, currentDepth + 1, sb, count, total);
                } else {
                    var size = formatSize(Files.size(entry));
                    sb.append(indent).append(connector).append(name).append(" (").append(size).append(")\n");
                }
            }
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return "%.1fKB".formatted(bytes / 1024.0);
        return "%.1fMB".formatted(bytes / (1024.0 * 1024.0));
    }
}
