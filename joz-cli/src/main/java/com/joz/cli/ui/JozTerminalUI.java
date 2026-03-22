package com.joz.cli.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.joz.common.event.AgentEvent;
import com.joz.common.event.AgentEvent.*;
import com.joz.common.model.ToolResult;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/** Rich terminal UI for JOz — Warp Oz-inspired experience. */
@Component
public class JozTerminalUI implements Consumer<AgentEvent> {

    private final PrintStream out;
    private final Spinner spinner;
    private final StatusBar statusBar;
    private int termWidth;
    private int turns;
    private int toolCallCount;
    private Instant startTime;
    private boolean isStreaming;
    private String currentModel = "";
    private String currentSessionId = "";

    public JozTerminalUI() {
        this.out = System.out;
        this.termWidth = detectWidth();
        this.spinner = new Spinner(out);
        this.statusBar = new StatusBar(out, termWidth);
    }

    /** Renders the welcome header. */
    public void renderWelcome(String model, String project, String sessionId, boolean resumed, int msgCount) {
        this.currentModel = model;
        this.currentSessionId = sessionId != null && sessionId.length() >= 8
                ? sessionId.substring(0, 8)
                : (sessionId != null ? sessionId : "");
        this.startTime = Instant.now();

        out.println();
        out.println(Ansi.CYAN + "  ╭─────────────────────────────────────────────────────────╮" + Ansi.RESET);
        out.println(Ansi.CYAN + "  │" + Ansi.RESET
                + Ansi.BOLD + Ansi.BRIGHT_WHITE + "  ⚡ JOz" + Ansi.RESET
                + Ansi.DIM + " — AI Coding Agent" + Ansi.RESET
                + " ".repeat(Math.max(1, 37 - 18))
                + Ansi.CYAN + "│" + Ansi.RESET);

        // Model info
        var modelLine = Ansi.fg(75) + "  Model: " + Ansi.RESET + model;
        out.println(Ansi.CYAN + "  │" + Ansi.RESET
                + Ansi.pad(modelLine, 57)
                + Ansi.CYAN + "│" + Ansi.RESET);

        // Project info
        var shortProject = shortenPath(project);
        var projLine = Ansi.fg(114) + "  Project: " + Ansi.RESET + shortProject;
        out.println(Ansi.CYAN + "  │" + Ansi.RESET
                + Ansi.pad(projLine, 57)
                + Ansi.CYAN + "│" + Ansi.RESET);

        // Session info
        var sessLine = Ansi.fg(176) + "  Session: " + Ansi.RESET + currentSessionId;
        if (resumed) {
            sessLine += Ansi.DIM + " (resumed, " + msgCount + " messages)" + Ansi.RESET;
        }
        out.println(Ansi.CYAN + "  │" + Ansi.RESET
                + Ansi.pad(sessLine, 57)
                + Ansi.CYAN + "│" + Ansi.RESET);

        out.println(Ansi.CYAN + "  ╰─────────────────────────────────────────────────────────╯" + Ansi.RESET);
        out.println();

        // Hint
        out.println(Ansi.DIM + "  Type your prompt below. Commands: /quit /clear /help" + Ansi.RESET);
        out.println();
    }

    /** Renders the input prompt. */
    public void renderPrompt() {
        out.print(Ansi.BOLD + Ansi.BRIGHT_GREEN + "  ❯ " + Ansi.RESET);
    }

    /** Called when the agent starts processing. */
    public void onAgentStart() {
        this.turns = 0;
        this.toolCallCount = 0;
        this.startTime = Instant.now();
        this.isStreaming = false;
        spinner.start("Thinking...");
    }

    /** Called when the agent finishes. */
    public void onAgentEnd() {
        spinner.stop();
        if (isStreaming) {
            out.println();
            isStreaming = false;
        }
    }

    @Override
    public void accept(AgentEvent event) {
        switch (event) {
            case ThinkingEvent(var thought) -> handleThinking(thought);
            case ToolCallEvent(var id, var name, var input, var needsApproval) ->
                    handleToolCall(name, input, needsApproval);
            case ToolResultEvent(var id, var name, var result) ->
                    handleToolResult(name, result);
            case TextChunkEvent(var text) -> handleText(text);
            case PlanUpdateEvent(var planId, var step, var status) ->
                    handlePlanUpdate(step, status);
            case CompletionEvent(var summary, var t, var tc, var elapsed) ->
                    handleCompletion(summary, t, tc, elapsed);
            case ErrorEvent(var message, var cause) -> handleError(message);
        }
    }

    private void handleThinking(String thought) {
        spinner.update(thought);
    }

    private void handleToolCall(String name, JsonNode input, boolean needsApproval) {
        spinner.stop();
        toolCallCount++;

        if (isStreaming) {
            out.println();
            out.println();
            isStreaming = false;
        }

        // Tool call header
        var icon = needsApproval ? "⏳" : "🔧";
        var color = needsApproval ? Ansi.YELLOW : Ansi.fg(75);

        out.println();
        out.println("  " + color + icon + " " + Ansi.BOLD + name + Ansi.RESET
                + Ansi.DIM + " " + summarizeInput(name, input) + Ansi.RESET);

        if (needsApproval) {
            renderApprovalBox(name, input);
        }
    }

    private void handleToolResult(String name, ToolResult result) {
        switch (result) {
            case ToolResult.ToolSuccess(var output) -> {
                var preview = output.length() > 300
                        ? output.substring(0, 300) + "…"
                        : output;
                var lines = preview.split("\n");
                if (lines.length <= 3) {
                    for (var line : lines) {
                        out.println(Ansi.GREEN + "    ✓ " + Ansi.RESET + Ansi.DIM + line + Ansi.RESET);
                    }
                } else {
                    out.println(Ansi.GREEN + "    ✓ " + Ansi.RESET + Ansi.DIM
                            + lines[0] + Ansi.RESET);
                    out.println(Ansi.DIM + "      (" + (lines.length - 1) + " more lines)" + Ansi.RESET);
                }
            }
            case ToolResult.ToolError(var error, var kind) -> {
                out.println(Ansi.RED + "    ✗ " + Ansi.RESET + Ansi.RED
                        + "[" + kind + "] " + error + Ansi.RESET);
            }
        }

        // Start thinking spinner again for next turn
        spinner.start("Thinking...");
    }

    private void handleText(String text) {
        spinner.stop();

        if (!isStreaming) {
            out.println();
            isStreaming = true;
        }

        // Render markdown for the text
        var rendered = MarkdownRenderer.render(text);
        out.print(rendered);
    }

    private void handlePlanUpdate(int step, String status) {
        spinner.stop();
        var icon = switch (status) {
            case "completed" -> Ansi.GREEN + "  ✓" + Ansi.RESET;
            case "running" -> Ansi.CYAN + "  ▸" + Ansi.RESET;
            default -> Ansi.DIM + "  ○" + Ansi.RESET;
        };
        out.println("  " + icon + " Step " + (step + 1) + ": " + Ansi.DIM + status + Ansi.RESET);
    }

    private void handleCompletion(String summary, int turns, int toolCalls, Duration elapsed) {
        spinner.stop();
        this.turns = turns;
        this.toolCallCount = toolCalls;

        if (isStreaming) {
            out.println();
            isStreaming = false;
        }

        out.println();

        // Completion summary bar
        var left = Ansi.GREEN + Ansi.BOLD + "  ✅ " + summary + Ansi.RESET;
        var stats = Ansi.DIM + turns + " turn" + (turns != 1 ? "s" : "")
                + " · " + toolCalls + " tool call" + (toolCalls != 1 ? "s" : "")
                + " · " + formatDuration(elapsed) + Ansi.RESET;

        out.println(left + "  " + stats);
        out.println();
    }

    private void handleError(String message) {
        spinner.stop();
        if (isStreaming) {
            out.println();
            isStreaming = false;
        }
        out.println();
        out.println(Ansi.RED + Ansi.BOLD + "  ❌ " + message + Ansi.RESET);
        out.println();
    }

    /** Renders the approval prompt box for tool calls. */
    public void renderApprovalBox(String toolName, JsonNode input) {
        out.println();
        out.println(Ansi.YELLOW + "  ╭─ Permission Required ──────────────────────────────╮" + Ansi.RESET);
        out.println(Ansi.YELLOW + "  │" + Ansi.RESET + "  Tool: "
                + Ansi.BOLD + toolName + Ansi.RESET
                + " ".repeat(Math.max(1, 44 - toolName.length()))
                + Ansi.YELLOW + "│" + Ansi.RESET);

        var desc = summarizeInput(toolName, input);
        if (desc.length() > 50) desc = desc.substring(0, 50) + "…";
        out.println(Ansi.YELLOW + "  │" + Ansi.RESET + "  "
                + Ansi.DIM + desc + Ansi.RESET
                + " ".repeat(Math.max(1, 52 - desc.length()))
                + Ansi.YELLOW + "│" + Ansi.RESET);

        out.println(Ansi.YELLOW + "  ╰──────────────────────────────────────────────────────╯" + Ansi.RESET);
        out.print("  " + Ansi.YELLOW + "Allow?" + Ansi.RESET
                + " [" + Ansi.GREEN + "y" + Ansi.RESET + "]es"
                + " [" + Ansi.RED + "n" + Ansi.RESET + "]o"
                + " [" + Ansi.GREEN + "a" + Ansi.RESET + "]lways"
                + " ne[" + Ansi.RED + "v" + Ansi.RESET + "]er > ");
    }

    /** Renders help text. */
    public void renderHelp() {
        out.println();
        out.println(Ansi.BOLD + Ansi.BRIGHT_WHITE + "  JOz Commands" + Ansi.RESET);
        out.println(Ansi.DIM + "  " + "─".repeat(40) + Ansi.RESET);
        out.println("  " + Ansi.CYAN + "/quit" + Ansi.RESET + "     Exit JOz");
        out.println("  " + Ansi.CYAN + "/clear" + Ansi.RESET + "    Clear screen");
        out.println("  " + Ansi.CYAN + "/help" + Ansi.RESET + "     Show this help");
        out.println("  " + Ansi.CYAN + "/history" + Ansi.RESET + "  Show conversation history");
        out.println("  " + Ansi.CYAN + "/session" + Ansi.RESET + "  Show current session info");
        out.println();
        out.println(Ansi.DIM + "  Just type naturally to ask JOz anything." + Ansi.RESET);
        out.println(Ansi.DIM + "  JOz can read, write, search files, run commands, and use git." + Ansi.RESET);
        out.println();
    }

    /** Renders conversation history summary. */
    public void renderHistory(java.util.List<com.joz.common.model.Message> messages) {
        out.println();
        out.println(Ansi.BOLD + "  Conversation History" + Ansi.RESET
                + Ansi.DIM + " (" + messages.size() + " messages)" + Ansi.RESET);
        out.println(Ansi.DIM + "  " + "─".repeat(40) + Ansi.RESET);

        for (var msg : messages) {
            switch (msg) {
                case com.joz.common.model.Message.UserMessage u ->
                        out.println("  " + Ansi.GREEN + "You: " + Ansi.RESET
                                + truncate(u.content(), 80));
                case com.joz.common.model.Message.AssistantMessage a -> {
                    if (a.text() != null && !a.text().isBlank()) {
                        out.println("  " + Ansi.CYAN + "JOz: " + Ansi.RESET
                                + truncate(a.text(), 80));
                    }
                    for (var tc : a.toolCalls()) {
                        out.println("  " + Ansi.fg(75) + "  🔧 " + tc.name() + Ansi.RESET);
                    }
                }
                case com.joz.common.model.Message.ToolResultMessage tr ->
                        out.println("  " + Ansi.DIM + "  → " + truncate(tr.content(), 70) + Ansi.RESET);
                case com.joz.common.model.Message.SystemMessage s -> {}
            }
        }
        out.println();
    }

    /** Renders session info. */
    public void renderSessionInfo(String sessionId, String project, int messageCount) {
        out.println();
        out.println(Ansi.BOLD + "  Session Info" + Ansi.RESET);
        out.println(Ansi.DIM + "  " + "─".repeat(40) + Ansi.RESET);
        out.println("  " + Ansi.fg(176) + "ID:       " + Ansi.RESET + sessionId);
        out.println("  " + Ansi.fg(114) + "Project:  " + Ansi.RESET + project);
        out.println("  " + Ansi.fg(75) + "Messages: " + Ansi.RESET + messageCount);
        out.println("  " + Ansi.fg(143) + "Model:    " + Ansi.RESET + currentModel);
        out.println();
    }

    private String summarizeInput(String toolName, JsonNode input) {
        return switch (toolName) {
            case "bash_exec" -> truncate(input.path("command").asText(""), 60);
            case "file_read" -> "path: " + input.path("path").asText("");
            case "file_write" -> input.path("path").asText("") + " [" + input.path("mode").asText("") + "]";
            case "file_search" -> "pattern: " + input.path("pattern").asText("");
            case "code_search" -> "query: " + input.path("query").asText("");
            case "git_operations" -> input.path("operation").asText("");
            case "list_directory" -> "path: " + input.path("path").asText(".");
            default -> "";
        };
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        var oneLine = s.replace("\n", " ").strip();
        return oneLine.length() > max ? oneLine.substring(0, max) + "…" : oneLine;
    }

    private String shortenPath(String path) {
        var home = System.getProperty("user.home");
        if (path.startsWith(home)) return "~" + path.substring(home.length());
        return path;
    }

    private String formatDuration(Duration d) {
        var secs = d.toMillis() / 1000.0;
        if (secs < 60) return "%.1fs".formatted(secs);
        return "%dm%ds".formatted(d.toMinutesPart(), d.toSecondsPart());
    }

    private int detectWidth() {
        try {
            var terminal = TerminalBuilder.builder().system(true).build();
            var width = terminal.getWidth();
            terminal.close();
            return width > 0 ? width : 80;
        } catch (IOException e) {
            return 80;
        }
    }
}
