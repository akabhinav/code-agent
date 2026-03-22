package com.joz.cli.command;

import com.joz.persistence.SessionStore;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** List and manage past sessions. */
@Command(name = "sessions", description = "List and manage past sessions", mixinStandardHelpOptions = true)
@Component
public class SessionCommand implements Runnable {

    @Parameters(index = "0", description = "Action: list | show <id>", defaultValue = "list")
    private String action;

    @Parameters(index = "1", description = "Session ID (for show)", defaultValue = "")
    private String sessionId;

    @Option(names = {"-n", "--limit"}, description = "Max sessions to show", defaultValue = "20")
    private int limit;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @Override
    public void run() {
        var globalConfigDir = Path.of(System.getProperty("user.home"), ".joz");
        var dbPath = globalConfigDir.resolve("sessions.db");

        if (!java.nio.file.Files.exists(dbPath)) {
            System.out.println("No sessions found. Run 'joz run' first.");
            return;
        }

        try (var sessionStore = new SessionStore(dbPath)) {
            switch (action) {
                case "list" -> listSessions(sessionStore);
                case "show" -> showSession(sessionStore);
                default -> System.err.println("Unknown action: " + action + ". Use 'list' or 'show <id>'.");
            }
        }
    }

    private void listSessions(SessionStore store) {
        var sessions = store.listSessions(limit);
        if (sessions.isEmpty()) {
            System.out.println("No sessions found.");
            return;
        }

        System.out.println("Recent sessions:");
        System.out.printf("  %-10s %-18s %-6s %s%n", "ID", "Last active", "Msgs", "Project");
        System.out.println("  " + "─".repeat(70));

        for (var session : sessions) {
            var shortId = session.id().substring(0, 8);
            var time = formatTime(session.updatedAt());
            var msgCount = store.getMessageCount(session.id());
            var project = shortenPath(session.projectRoot());
            var title = session.title() != null ? " — " + session.title() : "";

            System.out.printf("  %-10s %-18s %-6d %s%s%n", shortId, time, msgCount, project, title);
        }

        System.out.println();
        System.out.println("Resume a session:");
        System.out.println("  joz run --resume              (most recent for current project)");
        System.out.println("  joz run --session <id>        (specific session)");
    }

    private void showSession(SessionStore store) {
        if (sessionId.isBlank()) {
            System.err.println("Usage: joz sessions show <session-id>");
            return;
        }

        // Support short IDs — find the matching full ID
        var fullId = resolveSessionId(store, sessionId);
        if (fullId == null) {
            System.err.println("Session not found: " + sessionId);
            return;
        }

        var messages = store.getMessages(fullId);
        if (messages.isEmpty()) {
            System.out.println("Session " + sessionId + " has no messages.");
            return;
        }

        System.out.printf("Session %s (%d messages):%n%n", fullId.substring(0, 8), messages.size());

        for (var message : messages) {
            switch (message) {
                case com.joz.common.model.Message.UserMessage u ->
                        System.out.println("  \033[32mYou:\033[0m " + truncate(u.content(), 120));
                case com.joz.common.model.Message.AssistantMessage a -> {
                    if (a.text() != null && !a.text().isBlank()) {
                        System.out.println("  \033[36mJOz:\033[0m " + truncate(a.text(), 120));
                    }
                    for (var tc : a.toolCalls()) {
                        System.out.println("  \033[34m  🔧 " + tc.name() + "\033[0m");
                    }
                }
                case com.joz.common.model.Message.ToolResultMessage tr ->
                        System.out.println("  \033[2m  → " + truncate(tr.content(), 100) + "\033[0m");
                case com.joz.common.model.Message.SystemMessage s -> {}
            }
        }
        System.out.println();
    }

    private String resolveSessionId(SessionStore store, String shortId) {
        var sessions = store.listSessions(100);
        for (var session : sessions) {
            if (session.id().startsWith(shortId)) {
                return session.id();
            }
        }
        return null;
    }

    private String formatTime(String isoTimestamp) {
        try {
            return TIME_FMT.format(Instant.parse(isoTimestamp));
        } catch (Exception e) {
            return isoTimestamp;
        }
    }

    private String shortenPath(String path) {
        var home = System.getProperty("user.home");
        if (path.startsWith(home)) {
            return "~" + path.substring(home.length());
        }
        return path;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        var oneLine = s.replace("\n", " ").strip();
        return oneLine.length() > max ? oneLine.substring(0, max) + "..." : oneLine;
    }
}
