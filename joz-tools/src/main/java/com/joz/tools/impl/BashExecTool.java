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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Execute a shell command and return stdout/stderr. */
@Component
public class BashExecTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BashExecTool.class);
    private static final int MAX_OUTPUT_BYTES = 100 * 1024;

    @Override
    public String name() {
        return "bash_exec";
    }

    @Override
    public String description() {
        return "Execute a shell command and return stdout, stderr, and exit code.";
    }

    @Override
    public JsonNode inputSchema() {
        return ToolSchemaBuilder.objectSchema(
                List.of(
                        Property.of("command", "string", "The shell command to run"),
                        Property.of("working_dir", "string", "Working directory (optional, defaults to project root)"),
                        Property.withDefault("timeout_seconds", "integer", "Timeout in seconds", "120")),
                List.of("command"));
    }

    @Override
    public ToolResult execute(JsonNode input, ExecutionContext ctx) {
        var command = input.get("command").asText();
        var workingDir = input.has("working_dir")
                ? Path.of(input.get("working_dir").asText())
                : ctx.projectRoot();
        var timeoutSeconds = input.has("timeout_seconds")
                ? input.get("timeout_seconds").asInt()
                : 120;

        log.info("Executing: {}", command);

        try {
            var pb = new ProcessBuilder("bash", "-c", command)
                    .directory(workingDir.toFile())
                    .redirectErrorStream(false);

            pb.environment().putAll(ctx.env());
            var process = pb.start();

            var stdoutThread = Thread.ofVirtual().start(() -> {});
            var stdout = new String(process.getInputStream().readNBytes(MAX_OUTPUT_BYTES));
            var stderr = new String(process.getErrorStream().readNBytes(MAX_OUTPUT_BYTES));

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ToolError("Command timed out after %d seconds".formatted(timeoutSeconds), ErrorKind.TIMEOUT);
            }

            int exitCode = process.exitValue();
            var output = new StringBuilder();
            output.append("Exit code: ").append(exitCode).append("\n\n");
            if (!stdout.isBlank()) {
                output.append("STDOUT:\n").append(truncate(stdout)).append("\n");
            }
            if (!stderr.isBlank()) {
                output.append("STDERR:\n").append(truncate(stderr)).append("\n");
            }

            return new ToolSuccess(output.toString().strip());
        } catch (IOException e) {
            return new ToolError("Failed to execute command: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolError("Command interrupted", ErrorKind.EXECUTION_FAILED);
        }
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.EXECUTE;
    }

    private String truncate(String text) {
        if (text.length() > MAX_OUTPUT_BYTES) {
            return text.substring(0, MAX_OUTPUT_BYTES) + "\n[truncated — output too large]";
        }
        return text;
    }
}
