package com.joz.cli.render;

import com.joz.common.event.AgentEvent;
import com.joz.common.event.AgentEvent.*;
import com.joz.common.model.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.util.function.Consumer;

/** ANSI terminal renderer for AgentEvents. */
@Component
public class TerminalRenderer implements Consumer<AgentEvent> {

    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String CYAN = "\033[36m";
    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String YELLOW = "\033[33m";
    private static final String BLUE = "\033[34m";
    private static final String MAGENTA = "\033[35m";

    private final PrintStream out;

    public TerminalRenderer() {
        this.out = System.out;
    }

    public TerminalRenderer(PrintStream out) {
        this.out = out;
    }

    @Override
    public void accept(AgentEvent event) {
        switch (event) {
            case ThinkingEvent(var thought) -> renderThinking(thought);
            case ToolCallEvent(var id, var name, var input, var needsApproval) ->
                    renderToolCall(name, input, needsApproval);
            case ToolResultEvent(var id, var name, var result) ->
                    renderToolResult(name, result);
            case TextChunkEvent(var text) -> renderText(text);
            case PlanUpdateEvent(var planId, var step, var status) ->
                    renderPlanUpdate(step, status);
            case CompletionEvent(var summary, var turns, var toolCalls, var elapsed) ->
                    renderCompletion(summary, turns, toolCalls, elapsed);
            case ErrorEvent(var message, var cause) -> renderError(message);
        }
    }

    /** Renders the welcome banner. */
    public void renderBanner(String model, String projectRoot) {
        out.println();
        out.println(CYAN + "╭─ JOz Agent ─────────────────────────────────────────╮" + RESET);
        out.printf(CYAN + "│" + RESET + " Model: %-15s │  Project: %-14s" + CYAN + " │%n" + RESET,
                truncate(model, 15), truncate(projectRoot, 14));
        out.println(CYAN + "╰──────────────────────────────────────────────────────╯" + RESET);
        out.println();
    }

    /** Renders the user prompt indicator. */
    public void renderPrompt() {
        out.print(BOLD + GREEN + "You: " + RESET);
    }

    private void renderThinking(String thought) {
        out.println();
        out.println(DIM + "💭 " + thought + RESET);
    }

    private void renderToolCall(String name, JsonNode input, boolean needsApproval) {
        out.println();
        if (needsApproval) {
            out.println(YELLOW + "⏳ " + name + RESET + DIM + "(" + summarizeToolInput(name, input) + ")" + RESET);
        } else {
            out.println(BLUE + "🔧 " + name + RESET + DIM + "(" + summarizeToolInput(name, input) + ")" + RESET);
        }
    }

    private void renderToolResult(String name, ToolResult result) {
        switch (result) {
            case ToolResult.ToolSuccess(var output) -> {
                var preview = output.length() > 200
                        ? output.substring(0, 200) + "..."
                        : output;
                out.println(GREEN + "   ✓ " + RESET + DIM + preview.replace("\n", "\n     ") + RESET);
            }
            case ToolResult.ToolError(var error, var kind) -> {
                out.println(RED + "   ✗ [" + kind + "] " + error + RESET);
            }
        }
    }

    private void renderText(String text) {
        out.print(text);
    }

    private void renderPlanUpdate(int step, String status) {
        var icon = "completed".equals(status) ? "✓" : "▸";
        out.println(MAGENTA + "   " + icon + " Step " + (step + 1) + ": " + status + RESET);
    }

    private void renderCompletion(String summary, int turns, int toolCalls, java.time.Duration elapsed) {
        out.println();
        out.printf(GREEN + BOLD + "✅ %s" + RESET + DIM + " (%d turns, %d tool calls, %.1fs)%n" + RESET,
                summary, turns, toolCalls, elapsed.toMillis() / 1000.0);
        out.println();
    }

    private void renderError(String message) {
        out.println(RED + BOLD + "❌ Error: " + message + RESET);
    }

    /** Renders the approval prompt for a tool call. */
    public void renderApprovalPrompt(String description) {
        out.println();
        out.println(YELLOW + "╭─ Tool call requires approval" + RESET);
        for (var line : description.split("\n")) {
            out.println(YELLOW + "│  " + RESET + line);
        }
        out.print(YELLOW + "╰─ " + RESET + "Allow? [" + GREEN + "y" + RESET + "]es / ["
                + RED + "n" + RESET + "]o / [" + GREEN + "a" + RESET + "]lways / ne["
                + RED + "v" + RESET + "]er > ");
    }

    private String summarizeToolInput(String toolName, JsonNode input) {
        return switch (toolName) {
            case "bash_exec" -> "command: " + input.path("command").asText("");
            case "file_read" -> "path: " + input.path("path").asText("");
            case "file_write" -> "path: " + input.path("path").asText("") +
                    ", mode: " + input.path("mode").asText("");
            case "file_search" -> "pattern: " + input.path("pattern").asText("");
            case "code_search" -> "query: " + input.path("query").asText("");
            case "git_operations" -> "op: " + input.path("operation").asText("");
            case "list_directory" -> "path: " + input.path("path").asText(".");
            default -> input.toString();
        };
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(s.length() - max) : s;
    }
}
