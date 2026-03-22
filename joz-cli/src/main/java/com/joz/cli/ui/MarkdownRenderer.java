package com.joz.cli.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Renders Markdown text to ANSI-styled terminal output. */
public final class MarkdownRenderer {

    private MarkdownRenderer() {}

    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<![*])\\*(.+?)\\*(?![*])");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,3})\\s+(.+)$");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^(\\s*)[-*]\\s+(.+)$");
    private static final Pattern NUMBERED_PATTERN = Pattern.compile("^(\\s*)\\d+\\.\\s+(.+)$");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)");

    /** Renders markdown text to ANSI terminal output. */
    public static String render(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";

        var lines = markdown.split("\n");
        var result = new StringBuilder();
        boolean inCodeBlock = false;
        String codeLanguage = null;
        var codeLines = new ArrayList<String>();

        for (var line : lines) {
            // Code block toggle
            if (line.stripLeading().startsWith("```")) {
                if (inCodeBlock) {
                    // End code block
                    result.append(CodeBlock.render(codeLines, codeLanguage));
                    codeLines.clear();
                    inCodeBlock = false;
                    codeLanguage = null;
                } else {
                    // Start code block
                    inCodeBlock = true;
                    var lang = line.stripLeading().substring(3).strip();
                    codeLanguage = lang.isEmpty() ? null : lang;
                }
                continue;
            }

            if (inCodeBlock) {
                codeLines.add(line);
                continue;
            }

            result.append(renderLine(line)).append("\n");
        }

        // Handle unclosed code blocks
        if (inCodeBlock && !codeLines.isEmpty()) {
            result.append(CodeBlock.render(codeLines, codeLanguage));
        }

        return result.toString();
    }

    private static String renderLine(String line) {
        // Empty lines
        if (line.isBlank()) return "";

        // Headings
        var headingMatch = HEADING_PATTERN.matcher(line);
        if (headingMatch.matches()) {
            var level = headingMatch.group(1).length();
            var text = headingMatch.group(2);
            return switch (level) {
                case 1 -> "\n" + Ansi.BOLD + Ansi.BRIGHT_WHITE + "  " + text + Ansi.RESET + "\n"
                        + Ansi.DIM + "  " + "─".repeat(Math.min(text.length() + 2, 60)) + Ansi.RESET;
                case 2 -> "\n" + Ansi.BOLD + Ansi.CYAN + "  " + text + Ansi.RESET;
                default -> "\n" + Ansi.BOLD + "  " + text + Ansi.RESET;
            };
        }

        // Bullet list
        var bulletMatch = BULLET_PATTERN.matcher(line);
        if (bulletMatch.matches()) {
            var indent = bulletMatch.group(1);
            var text = renderInline(bulletMatch.group(2));
            return indent + Ansi.CYAN + "  •" + Ansi.RESET + " " + text;
        }

        // Numbered list
        var numberedMatch = NUMBERED_PATTERN.matcher(line);
        if (numberedMatch.matches()) {
            var indent = numberedMatch.group(1);
            var text = renderInline(numberedMatch.group(2));
            return indent + Ansi.CYAN + "  " + line.stripLeading().substring(0, line.stripLeading().indexOf('.') + 1) + Ansi.RESET + " " + text;
        }

        // Horizontal rule
        if (line.strip().matches("^-{3,}$|^\\*{3,}$|^_{3,}$")) {
            return Ansi.DIM + "  " + "─".repeat(40) + Ansi.RESET;
        }

        // Regular paragraph
        return "  " + renderInline(line);
    }

    /** Renders inline markdown (bold, italic, code, links). */
    public static String renderInline(String text) {
        // Bold
        text = BOLD_PATTERN.matcher(text).replaceAll(Ansi.BOLD + "$1" + Ansi.RESET);
        // Italic
        text = ITALIC_PATTERN.matcher(text).replaceAll(Ansi.ITALIC + "$1" + Ansi.RESET);
        // Inline code
        text = INLINE_CODE_PATTERN.matcher(text).replaceAll(
                Ansi.bg(236) + Ansi.BRIGHT_CYAN + " $1 " + Ansi.RESET);
        // Links
        text = LINK_PATTERN.matcher(text).replaceAll(
                Ansi.UNDERLINE + Ansi.BLUE + "$1" + Ansi.RESET + Ansi.DIM + " ($2)" + Ansi.RESET);
        return text;
    }
}
