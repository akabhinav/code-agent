package com.joz.cli.ui;

import java.io.PrintStream;
import java.time.Duration;

/** Bottom status bar showing session info, token usage, and turn count. */
public class StatusBar {

    private final PrintStream out;
    private int termWidth;
    private String model = "";
    private String sessionId = "";
    private int turns;
    private int toolCalls;
    private int inputTokens;
    private int outputTokens;
    private Duration elapsed = Duration.ZERO;

    public StatusBar(PrintStream out, int termWidth) {
        this.out = out;
        this.termWidth = termWidth;
    }

    public StatusBar(int termWidth) {
        this(System.out, termWidth);
    }

    /** Updates and redraws the status bar. */
    public void update(String model, String sessionId, int turns, int toolCalls,
                       int inputTokens, int outputTokens, Duration elapsed) {
        this.model = model;
        this.sessionId = sessionId;
        this.turns = turns;
        this.toolCalls = toolCalls;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.elapsed = elapsed;
    }

    /** Renders the status bar string. */
    public String render() {
        var left = Ansi.fg(75) + Ansi.BOLD + " ⚡ " + model + Ansi.RESET
                + Ansi.DIM + "  " + sessionId + Ansi.RESET;

        var stats = "";
        if (turns > 0) {
            stats = Ansi.fg(114) + "↻" + turns + Ansi.RESET
                    + Ansi.DIM + " │ " + Ansi.RESET
                    + Ansi.fg(176) + "⚙" + toolCalls + Ansi.RESET;
            if (inputTokens > 0) {
                stats += Ansi.DIM + " │ " + Ansi.RESET
                        + Ansi.fg(143) + "↑" + formatTokens(inputTokens)
                        + " ↓" + formatTokens(outputTokens) + Ansi.RESET;
            }
            if (elapsed.toMillis() > 0) {
                stats += Ansi.DIM + " │ " + Ansi.RESET
                        + Ansi.fg(240) + formatDuration(elapsed) + Ansi.RESET;
            }
            stats += " ";
        }

        return Ansi.bg(236) + Ansi.pad(left, termWidth - Ansi.visibleLength(stats)) + stats + Ansi.RESET;
    }

    /** Prints the status bar to the terminal. */
    public void print() {
        out.println(render());
    }

    private String formatTokens(int tokens) {
        if (tokens < 1000) return String.valueOf(tokens);
        if (tokens < 100_000) return "%.1fk".formatted(tokens / 1000.0);
        return "%dk".formatted(tokens / 1000);
    }

    private String formatDuration(Duration d) {
        var secs = d.toMillis() / 1000.0;
        if (secs < 60) return "%.1fs".formatted(secs);
        return "%dm%ds".formatted(d.toMinutesPart(), d.toSecondsPart());
    }
}
