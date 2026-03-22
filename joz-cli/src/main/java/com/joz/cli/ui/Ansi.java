package com.joz.cli.ui;

/** ANSI escape code constants and builder for terminal styling. */
public final class Ansi {

    private Ansi() {}

    // Reset
    public static final String RESET = "\033[0m";

    // Styles
    public static final String BOLD = "\033[1m";
    public static final String DIM = "\033[2m";
    public static final String ITALIC = "\033[3m";
    public static final String UNDERLINE = "\033[4m";
    public static final String STRIKETHROUGH = "\033[9m";

    // Standard colors
    public static final String BLACK = "\033[30m";
    public static final String RED = "\033[31m";
    public static final String GREEN = "\033[32m";
    public static final String YELLOW = "\033[33m";
    public static final String BLUE = "\033[34m";
    public static final String MAGENTA = "\033[35m";
    public static final String CYAN = "\033[36m";
    public static final String WHITE = "\033[37m";
    public static final String GRAY = "\033[90m";

    // Bright colors
    public static final String BRIGHT_RED = "\033[91m";
    public static final String BRIGHT_GREEN = "\033[92m";
    public static final String BRIGHT_YELLOW = "\033[93m";
    public static final String BRIGHT_BLUE = "\033[94m";
    public static final String BRIGHT_MAGENTA = "\033[95m";
    public static final String BRIGHT_CYAN = "\033[96m";
    public static final String BRIGHT_WHITE = "\033[97m";

    // Background colors
    public static final String BG_BLACK = "\033[40m";
    public static final String BG_RED = "\033[41m";
    public static final String BG_GREEN = "\033[42m";
    public static final String BG_YELLOW = "\033[43m";
    public static final String BG_BLUE = "\033[44m";
    public static final String BG_MAGENTA = "\033[45m";
    public static final String BG_CYAN = "\033[46m";
    public static final String BG_WHITE = "\033[47m";
    public static final String BG_GRAY = "\033[100m";

    // 256-color support
    public static String fg(int code) { return "\033[38;5;" + code + "m"; }
    public static String bg(int code) { return "\033[48;5;" + code + "m"; }

    // RGB color support
    public static String rgb(int r, int g, int b) { return "\033[38;2;" + r + ";" + g + ";" + b + "m"; }
    public static String bgRgb(int r, int g, int b) { return "\033[48;2;" + r + ";" + g + ";" + b + "m"; }

    // Cursor control
    public static final String CLEAR_SCREEN = "\033[2J\033[H";
    public static final String CLEAR_LINE = "\033[2K";
    public static final String CURSOR_UP = "\033[1A";
    public static final String CURSOR_HOME = "\033[H";
    public static final String HIDE_CURSOR = "\033[?25l";
    public static final String SHOW_CURSOR = "\033[?25h";
    public static String cursorUp(int n) { return "\033[" + n + "A"; }
    public static String cursorTo(int row, int col) { return "\033[" + row + ";" + col + "H"; }

    /** Strips all ANSI escape codes from a string. */
    public static String strip(String text) {
        return text.replaceAll("\033\\[[0-9;]*[a-zA-Z]", "");
    }

    /** Returns the visible length of a string (excluding ANSI codes). */
    public static int visibleLength(String text) {
        return strip(text).length();
    }

    /** Pads a styled string to a visible width. */
    public static String pad(String text, int width) {
        int visible = visibleLength(text);
        if (visible >= width) return text;
        return text + " ".repeat(width - visible);
    }

    /** Repeats a character. */
    public static String repeat(char c, int count) {
        return String.valueOf(c).repeat(Math.max(0, count));
    }
}
