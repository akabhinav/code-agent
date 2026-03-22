package com.joz.cli.ui;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Renders code blocks with syntax highlighting hints and line numbers. */
public final class CodeBlock {

    private CodeBlock() {}

    private static final String CODE_BG = Ansi.bg(235);
    private static final String LINE_NUM_COLOR = Ansi.fg(240);
    private static final String BORDER_COLOR = Ansi.fg(240);

    // Simple keyword sets per language for basic highlighting
    private static final Map<String, String[]> KEYWORDS = Map.of(
            "java", new String[]{"public", "private", "protected", "class", "interface", "record",
                    "sealed", "permits", "extends", "implements", "import", "package", "return",
                    "if", "else", "for", "while", "switch", "case", "new", "var", "void",
                    "static", "final", "abstract", "try", "catch", "throw", "throws"},
            "python", new String[]{"def", "class", "import", "from", "return", "if", "else", "elif",
                    "for", "while", "try", "except", "with", "as", "yield", "lambda", "pass"},
            "javascript", new String[]{"function", "const", "let", "var", "return", "if", "else",
                    "for", "while", "class", "import", "export", "async", "await", "new", "this"},
            "bash", new String[]{"if", "then", "else", "fi", "for", "do", "done", "while",
                    "case", "esac", "function", "return", "export", "local", "echo"}
    );

    // Aliases
    private static final Map<String, String> LANG_ALIASES = Map.of(
            "js", "javascript", "ts", "javascript", "typescript", "javascript",
            "py", "python", "sh", "bash", "shell", "bash", "zsh", "bash",
            "kt", "java", "kotlin", "java", "scala", "java"
    );

    /** Renders a code block with optional language highlighting. */
    public static String render(List<String> lines, String language) {
        if (lines.isEmpty()) return "";

        var sb = new StringBuilder();
        var lang = language != null ? language.toLowerCase() : "";
        lang = LANG_ALIASES.getOrDefault(lang, lang);

        int maxLineNum = lines.size();
        int numWidth = String.valueOf(maxLineNum).length();

        // Top border with language label
        var langLabel = language != null && !language.isEmpty()
                ? " " + language + " "
                : "";
        sb.append("  ").append(BORDER_COLOR).append("╭─")
                .append(Ansi.RESET).append(Ansi.DIM).append(Ansi.ITALIC).append(langLabel).append(Ansi.RESET)
                .append(BORDER_COLOR).append("─".repeat(Math.max(1, 50 - langLabel.length())))
                .append("╮").append(Ansi.RESET).append("\n");

        // Code lines
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var highlighted = highlightLine(line, lang);
            var lineNum = String.format("%" + numWidth + "d", i + 1);

            sb.append("  ").append(BORDER_COLOR).append("│").append(Ansi.RESET)
                    .append(CODE_BG)
                    .append(LINE_NUM_COLOR).append(" ").append(lineNum).append(Ansi.RESET)
                    .append(CODE_BG).append(Ansi.fg(245)).append(" │ ").append(Ansi.RESET)
                    .append(CODE_BG).append(highlighted)
                    .append(Ansi.RESET).append("\n");
        }

        // Bottom border
        sb.append("  ").append(BORDER_COLOR).append("╰").append("─".repeat(52)).append("╯").append(Ansi.RESET).append("\n");

        return sb.toString();
    }

    private static String highlightLine(String line, String lang) {
        if (lang.isEmpty() || !KEYWORDS.containsKey(lang)) {
            return highlightGeneric(line);
        }

        var result = line;

        // Strings (simple — single and double quotes)
        result = Pattern.compile("(\"[^\"]*\"|'[^']*')").matcher(result)
                .replaceAll(Ansi.fg(143) + "$1" + Ansi.RESET + CODE_BG);

        // Comments
        result = Pattern.compile("(//.*$|#.*$)").matcher(result)
                .replaceAll(Ansi.fg(240) + Ansi.ITALIC + "$1" + Ansi.RESET + CODE_BG);

        // Numbers
        result = Pattern.compile("\\b(\\d+\\.?\\d*)\\b").matcher(result)
                .replaceAll(Ansi.fg(176) + "$1" + Ansi.RESET + CODE_BG);

        // Keywords
        var keywords = KEYWORDS.get(lang);
        for (var kw : keywords) {
            result = Pattern.compile("\\b(" + kw + ")\\b").matcher(result)
                    .replaceAll(Ansi.fg(204) + Ansi.BOLD + "$1" + Ansi.RESET + CODE_BG);
        }

        // Annotations / decorators
        result = Pattern.compile("(@\\w+)").matcher(result)
                .replaceAll(Ansi.fg(114) + "$1" + Ansi.RESET + CODE_BG);

        return result;
    }

    private static String highlightGeneric(String line) {
        var result = line;
        // Strings
        result = Pattern.compile("(\"[^\"]*\"|'[^']*')").matcher(result)
                .replaceAll(Ansi.fg(143) + "$1" + Ansi.RESET + CODE_BG);
        // Comments
        result = Pattern.compile("(//.*$|#.*$)").matcher(result)
                .replaceAll(Ansi.fg(240) + Ansi.ITALIC + "$1" + Ansi.RESET + CODE_BG);
        return result;
    }
}
