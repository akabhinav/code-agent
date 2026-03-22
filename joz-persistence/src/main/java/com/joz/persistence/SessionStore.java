package com.joz.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.joz.common.exception.JozException;
import com.joz.common.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite-backed storage for sessions and messages. */
public class SessionStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final Connection connection;

    public SessionStore(java.nio.file.Path dbPath) {
        try {
            java.nio.file.Files.createDirectories(dbPath.getParent());
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initSchema();
        } catch (Exception e) {
            throw new JozException.SessionException("Failed to open session database", e);
        }
    }

    private void initSchema() throws SQLException {
        try (var stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sessions (
                        id TEXT PRIMARY KEY,
                        project_root TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        title TEXT
                    )""");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        session_id TEXT NOT NULL REFERENCES sessions(id),
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        tool_calls TEXT,
                        created_at TEXT NOT NULL
                    )""");
        }
    }

    /** Creates a new session and returns its ID. */
    public String createSession(String projectRoot) {
        var id = java.util.UUID.randomUUID().toString();
        var now = Instant.now().toString();
        try (var stmt = connection.prepareStatement(
                "INSERT INTO sessions (id, project_root, created_at, updated_at) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, id);
            stmt.setString(2, projectRoot);
            stmt.setString(3, now);
            stmt.setString(4, now);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new JozException.SessionException("Failed to create session", e);
        }
        return id;
    }

    /** Appends a message to a session. */
    public void addMessage(String sessionId, Message message) {
        var role = switch (message) {
            case Message.UserMessage u -> "user";
            case Message.AssistantMessage a -> "assistant";
            case Message.SystemMessage s -> "system";
            case Message.ToolResultMessage tr -> "tool_result";
        };
        try {
            var contentJson = mapper.writeValueAsString(message);
            try (var stmt = connection.prepareStatement(
                    "INSERT INTO messages (session_id, role, content, created_at) VALUES (?, ?, ?, ?)")) {
                stmt.setString(1, sessionId);
                stmt.setString(2, role);
                stmt.setString(3, contentJson);
                stmt.setString(4, Instant.now().toString());
                stmt.executeUpdate();
            }
            touchSession(sessionId);
        } catch (JsonProcessingException | SQLException e) {
            throw new JozException.SessionException("Failed to save message", e);
        }
    }

    /** Loads all messages for a session. */
    public List<Message> getMessages(String sessionId) {
        var messages = new ArrayList<Message>();
        try (var stmt = connection.prepareStatement(
                "SELECT content FROM messages WHERE session_id = ? ORDER BY id")) {
            stmt.setString(1, sessionId);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                messages.add(mapper.readValue(rs.getString("content"), Message.class));
            }
        } catch (Exception e) {
            throw new JozException.SessionException("Failed to load messages", e);
        }
        return messages;
    }

    /** Updates the session title. */
    public void setTitle(String sessionId, String title) {
        try (var stmt = connection.prepareStatement(
                "UPDATE sessions SET title = ?, updated_at = ? WHERE id = ?")) {
            stmt.setString(1, title);
            stmt.setString(2, Instant.now().toString());
            stmt.setString(3, sessionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new JozException.SessionException("Failed to update session title", e);
        }
    }

    /** Returns the most recent session for a project, if any. */
    public Optional<SessionSummary> getLastSession(String projectRoot) {
        try (var stmt = connection.prepareStatement(
                "SELECT id, project_root, title, created_at, updated_at FROM sessions WHERE project_root = ? ORDER BY updated_at DESC LIMIT 1")) {
            stmt.setString(1, projectRoot);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new SessionSummary(
                        rs.getString("id"),
                        rs.getString("project_root"),
                        rs.getString("title"),
                        rs.getString("created_at"),
                        rs.getString("updated_at")));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new JozException.SessionException("Failed to get last session", e);
        }
    }

    /** Returns the message count for a session. */
    public int getMessageCount(String sessionId) {
        try (var stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM messages WHERE session_id = ?")) {
            stmt.setString(1, sessionId);
            var rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new JozException.SessionException("Failed to count messages", e);
        }
    }

    /** Lists recent sessions. */
    public List<SessionSummary> listSessions(int limit) {
        var sessions = new ArrayList<SessionSummary>();
        try (var stmt = connection.prepareStatement(
                "SELECT id, project_root, title, created_at, updated_at FROM sessions ORDER BY updated_at DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                sessions.add(new SessionSummary(
                        rs.getString("id"),
                        rs.getString("project_root"),
                        rs.getString("title"),
                        rs.getString("created_at"),
                        rs.getString("updated_at")));
            }
        } catch (SQLException e) {
            throw new JozException.SessionException("Failed to list sessions", e);
        }
        return sessions;
    }

    private void touchSession(String sessionId) throws SQLException {
        try (var stmt = connection.prepareStatement(
                "UPDATE sessions SET updated_at = ? WHERE id = ?")) {
            stmt.setString(1, Instant.now().toString());
            stmt.setString(2, sessionId);
            stmt.executeUpdate();
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.warn("Failed to close database connection", e);
        }
    }

    /** Summary record for session listing. */
    public record SessionSummary(String id, String projectRoot, String title,
                                  String createdAt, String updatedAt) {}
}
