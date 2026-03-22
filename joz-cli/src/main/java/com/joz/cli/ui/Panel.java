package com.joz.cli.ui;

import java.util.ArrayList;
import java.util.List;

/** Renders bordered panels with titles for terminal display. */
public final class Panel {

    private Panel() {}

    // Box-drawing characters (Unicode)
    private static final String TL = "╭"; // top-left
    private static final String TR = "╮"; // top-right
    private static final String BL = "╰"; // bottom-left
    private static final String BR = "╯"; // bottom-right
    private static final String H = "─";   // horizontal
    private static final String V = "│";   // vertical

    /** Renders a bordered panel with a title and content lines. */
    public static String render(String title, List<String> lines, int width, String borderColor) {
        var sb = new StringBuilder();
        int innerWidth = width - 4; // 2 for border + 2 for padding

        // Top border
        var titlePart = title != null && !title.isEmpty()
                ? " " + title + " "
                : "";
        int titleLen = Ansi.visibleLength(titlePart);
        int lineLen = Math.max(0, width - 2 - titleLen);
        sb.append(borderColor).append(TL).append(H.repeat(1))
                .append(Ansi.RESET).append(Ansi.BOLD).append(titlePart).append(Ansi.RESET)
                .append(borderColor).append(H.repeat(Math.max(0, lineLen - 1))).append(TR)
                .append(Ansi.RESET).append("\n");

        // Content lines
        for (var line : lines) {
            var stripped = Ansi.strip(line);
            // Wrap long lines
            if (stripped.length() > innerWidth) {
                line = line.substring(0, Math.min(line.length(), innerWidth + (line.length() - stripped.length())));
            }
            sb.append(borderColor).append(V).append(Ansi.RESET)
                    .append(" ").append(Ansi.pad(line, innerWidth)).append(" ")
                    .append(borderColor).append(V).append(Ansi.RESET).append("\n");
        }

        // Bottom border
        sb.append(borderColor).append(BL).append(H.repeat(width - 2)).append(BR).append(Ansi.RESET).append("\n");

        return sb.toString();
    }

    /** Renders a simple horizontal divider. */
    public static String divider(int width, String color) {
        return color + H.repeat(width) + Ansi.RESET;
    }

    /** Renders a labeled divider like: ── Tool Call ────────── */
    public static String labeledDivider(String label, int width, String color) {
        var labelPart = " " + label + " ";
        int remaining = width - labelPart.length() - 3;
        return color + H.repeat(2) + Ansi.RESET + Ansi.BOLD + labelPart + Ansi.RESET
                + color + H.repeat(Math.max(1, remaining)) + Ansi.RESET;
    }
}
