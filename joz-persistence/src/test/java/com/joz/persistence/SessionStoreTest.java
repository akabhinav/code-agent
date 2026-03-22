package com.joz.persistence;

import com.joz.common.model.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {

    @TempDir
    Path tempDir;

    private SessionStore store;

    @BeforeEach
    void setup() {
        store = new SessionStore(tempDir.resolve("test-sessions.db"));
    }

    @AfterEach
    void cleanup() {
        store.close();
    }

    @Test
    void createSessionReturnsId() {
        var id = store.createSession("/home/user/project");
        assertNotNull(id);
        assertFalse(id.isBlank());
    }

    @Test
    void addAndRetrieveMessages() {
        var sessionId = store.createSession("/home/user/project");

        store.addMessage(sessionId, new Message.UserMessage("hello"));
        store.addMessage(sessionId, new Message.AssistantMessage("hi there", List.of()));
        store.addMessage(sessionId, new Message.UserMessage("what is 2+2?"));

        var messages = store.getMessages(sessionId);
        assertEquals(3, messages.size());
        assertInstanceOf(Message.UserMessage.class, messages.get(0));
        assertInstanceOf(Message.AssistantMessage.class, messages.get(1));
        assertEquals("hello", ((Message.UserMessage) messages.get(0)).content());
    }

    @Test
    void getMessageCount() {
        var sessionId = store.createSession("/home/user/project");
        assertEquals(0, store.getMessageCount(sessionId));

        store.addMessage(sessionId, new Message.UserMessage("one"));
        store.addMessage(sessionId, new Message.UserMessage("two"));

        assertEquals(2, store.getMessageCount(sessionId));
    }

    @Test
    void getLastSessionFindsCorrectProject() {
        store.createSession("/home/user/project-a");
        var idB = store.createSession("/home/user/project-b");
        store.addMessage(idB, new Message.UserMessage("in project B"));

        var last = store.getLastSession("/home/user/project-b");
        assertTrue(last.isPresent());
        assertEquals(idB, last.get().id());
    }

    @Test
    void getLastSessionReturnsEmptyForUnknownProject() {
        var last = store.getLastSession("/nonexistent");
        assertTrue(last.isEmpty());
    }

    @Test
    void listSessionsOrderedByRecent() {
        var id1 = store.createSession("/home/user/project");
        store.addMessage(id1, new Message.UserMessage("first session"));

        var id2 = store.createSession("/home/user/project");
        store.addMessage(id2, new Message.UserMessage("second session"));

        var sessions = store.listSessions(10);
        assertEquals(2, sessions.size());
        // Most recent first
        assertEquals(id2, sessions.get(0).id());
    }

    @Test
    void setTitleUpdatesSession() {
        var id = store.createSession("/home/user/project");
        store.setTitle(id, "Fix NullPointerException");

        var sessions = store.listSessions(10);
        assertEquals("Fix NullPointerException", sessions.get(0).title());
    }

    @Test
    void messagesPreserveToolResults() {
        var sessionId = store.createSession("/project");
        store.addMessage(sessionId, new Message.ToolResultMessage("tc-1", "bash_exec", "Exit code: 0\nHello"));

        var messages = store.getMessages(sessionId);
        assertEquals(1, messages.size());
        assertInstanceOf(Message.ToolResultMessage.class, messages.get(0));
        var tr = (Message.ToolResultMessage) messages.get(0);
        assertEquals("tc-1", tr.toolUseId());
        assertEquals("bash_exec", tr.toolName());
        assertTrue(tr.content().contains("Hello"));
    }
}
