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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Search for code symbols — function names, class names, imports. */
@Component
public class CodeSearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(CodeSearchTool.class);

    @Override
    public String name() {
        return "code_search";
    }

    @Override
    public String description() {
        return "Search for code symbols — function names, class names, imports.";
    }

    @Override
    public JsonNode inputSchema() {
        return ToolSchemaBuilder.objectSchema(
                List.of(
                        Property.of("query", "string", "Symbol or pattern to find"),
                        Property.ofEnum("type", "Symbol type to search for",
                                List.of("function", "class", "interface", "import", "any"))),
                List.of("query"));
    }

    @Override
    public ToolResult execute(JsonNode input, ExecutionContext ctx) {
        var query = input.get("query").asText();
        var type = input.has("type") ? input.get("type").asText() : "any";

        var pattern = buildPattern(query, type);

        // Try ripgrep first
        var rgResult = tryRipgrep(pattern, ctx.projectRoot());
        if (rgResult != null) return rgResult;

        // Fallback to Java search
        return javaSearch(pattern, ctx.projectRoot());
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.READ;
    }

    private String buildPattern(String query, String type) {
        return switch (type) {
            case "function" -> "\\b(public|private|protected|static)?\\s+\\w+\\s+" + Pattern.quote(query) + "\\s*\\(";
            case "class" -> "\\b(class|record|enum)\\s+" + Pattern.quote(query);
            case "interface" -> "\\binterface\\s+" + Pattern.quote(query);
            case "import" -> "\\bimport\\s+.*" + Pattern.quote(query);
            default -> Pattern.quote(query);
        };
    }

    private ToolResult tryRipgrep(String pattern, Path searchPath) {
        try {
            var cmd = List.of("rg", "--no-heading", "--line-number",
                    "--context", "3", "--max-count", "30",
                    "--glob", "*.java", "--glob", "*.py", "--glob", "*.ts",
                    "--glob", "*.js", "--glob", "*.go", "--glob", "*.rs",
                    pattern, searchPath.toString());

            var pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            var process = pb.start();
            var output = new String(process.getInputStream().readNBytes(100 * 1024));
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return new ToolError("Search timed out", ErrorKind.TIMEOUT);
            }

            if (process.exitValue() == 0) {
                return new ToolSuccess(output.strip());
            } else if (process.exitValue() == 1) {
                return new ToolSuccess("No matches found.");
            }
            return null;
        } catch (IOException e) {
            log.debug("ripgrep not available for code search, falling back");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolError("Search interrupted", ErrorKind.EXECUTION_FAILED);
        }
    }

    private ToolResult javaSearch(String pattern, Path searchPath) {
        try {
            var regex = Pattern.compile(pattern);
            var results = new StringBuilder();
            int count = 0;

            try (Stream<Path> paths = Files.walk(searchPath)) {
                var files = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> isSourceFile(p.getFileName().toString()))
                        .filter(p -> !p.toString().contains("/.git/"))
                        .toList();

                for (var file : files) {
                    if (count >= 30) break;
                    try {
                        var lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size() && count < 30; i++) {
                            if (regex.matcher(lines.get(i)).find()) {
                                var rel = searchPath.relativize(file);
                                // Add context
                                int start = Math.max(0, i - 3);
                                int end = Math.min(lines.size(), i + 4);
                                results.append("--- %s:%d ---\n".formatted(rel, i + 1));
                                for (int j = start; j < end; j++) {
                                    var prefix = (j == i) ? ">" : " ";
                                    results.append("%s %4d: %s%n".formatted(prefix, j + 1, lines.get(j)));
                                }
                                results.append("\n");
                                count++;
                            }
                        }
                    } catch (IOException ignored) {}
                }
            }

            return new ToolSuccess(count == 0 ? "No matches found." : results.toString().strip());
        } catch (IOException e) {
            return new ToolError("Search failed: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }

    private boolean isSourceFile(String name) {
        return name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".ts") ||
               name.endsWith(".js") || name.endsWith(".go") || name.endsWith(".rs") ||
               name.endsWith(".kt") || name.endsWith(".scala") || name.endsWith(".rb");
    }
}
