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

/** Search file contents using regex, powered by ripgrep with fallback. */
@Component
public class FileSearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FileSearchTool.class);

    @Override
    public String name() {
        return "file_search";
    }

    @Override
    public String description() {
        return "Search file contents using regex (powered by ripgrep).";
    }

    @Override
    public JsonNode inputSchema() {
        return ToolSchemaBuilder.objectSchema(
                List.of(
                        Property.of("pattern", "string", "Regex pattern to search for"),
                        Property.of("path", "string", "Directory to search (optional, defaults to project root)"),
                        Property.of("include", "string", "Glob pattern like '*.java' (optional)"),
                        Property.withDefault("max_results", "integer", "Maximum results to return", "50")),
                List.of("pattern"));
    }

    @Override
    public ToolResult execute(JsonNode input, ExecutionContext ctx) {
        var pattern = input.get("pattern").asText();
        var searchPath = input.has("path")
                ? ctx.projectRoot().resolve(input.get("path").asText())
                : ctx.projectRoot();
        var include = input.has("include") ? input.get("include").asText() : null;
        var maxResults = input.has("max_results") ? input.get("max_results").asInt() : 50;

        // Try ripgrep first
        var rgResult = tryRipgrep(pattern, searchPath, include, maxResults);
        if (rgResult != null) return rgResult;

        // Fallback to Java-based search
        return javaSearch(pattern, searchPath, include, maxResults);
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.READ;
    }

    private ToolResult tryRipgrep(String pattern, Path searchPath, String include, int maxResults) {
        try {
            var cmd = new ArrayList<String>();
            cmd.addAll(List.of("rg", "--no-heading", "--line-number", "--max-count", String.valueOf(maxResults)));
            if (include != null) {
                cmd.addAll(List.of("--glob", include));
            }
            cmd.add(pattern);
            cmd.add(searchPath.toString());

            var pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            var process = pb.start();
            var output = new String(process.getInputStream().readNBytes(100 * 1024));
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return new ToolError("Search timed out", ErrorKind.TIMEOUT);
            }

            if (process.exitValue() == 0) {
                return new ToolSuccess(output.isBlank() ? "No matches found." : output.strip());
            } else if (process.exitValue() == 1) {
                return new ToolSuccess("No matches found.");
            }
            // rg not found or other error — fall through to java search
            return null;
        } catch (IOException e) {
            log.debug("ripgrep not available, falling back to Java search");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolError("Search interrupted", ErrorKind.EXECUTION_FAILED);
        }
    }

    private ToolResult javaSearch(String pattern, Path searchPath, String include, int maxResults) {
        try {
            var regex = Pattern.compile(pattern);
            var results = new StringBuilder();
            int count = 0;

            try (Stream<Path> paths = Files.walk(searchPath)) {
                var files = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> !isIgnored(p))
                        .filter(p -> include == null || matchesGlob(p, include))
                        .toList();

                for (var file : files) {
                    if (count >= maxResults) break;
                    try {
                        var lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size() && count < maxResults; i++) {
                            if (regex.matcher(lines.get(i)).find()) {
                                var rel = searchPath.relativize(file);
                                results.append("%s:%d: %s%n".formatted(rel, i + 1, lines.get(i).strip()));
                                count++;
                            }
                        }
                    } catch (IOException ignored) {
                        // Skip unreadable files
                    }
                }
            }

            return new ToolSuccess(count == 0 ? "No matches found." : results.toString().strip());
        } catch (IOException e) {
            return new ToolError("Search failed: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }

    private boolean isIgnored(Path path) {
        var str = path.toString();
        return str.contains("/.git/") || str.contains("/node_modules/") ||
               str.contains("/target/") || str.contains("/build/") ||
               str.contains("/__pycache__/") || str.contains("/.joz/");
    }

    private boolean matchesGlob(Path path, String glob) {
        var fileName = path.getFileName().toString();
        var globPattern = glob.replace(".", "\\.").replace("*", ".*");
        return fileName.matches(globPattern);
    }
}
