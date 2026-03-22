package com.joz.common.model;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void userMessageHoldsContent() {
        var msg = new Message.UserMessage("hello");
        assertEquals("hello", msg.content());
    }

    @Test
    void assistantMessageToolCallsDefaultToEmptyList() {
        var msg = new Message.AssistantMessage("response", null);
        assertNotNull(msg.toolCalls());
        assertTrue(msg.toolCalls().isEmpty());
    }

    @Test
    void exhaustiveSwitchOnMessage() {
        Message msg = new Message.SystemMessage("you are helpful");
        var role = switch (msg) {
            case Message.UserMessage u -> "user";
            case Message.AssistantMessage a -> "assistant";
            case Message.SystemMessage s -> "system";
            case Message.ToolResultMessage tr -> "tool_result";
        };
        assertEquals("system", role);
    }

    @Test
    void toolResultMessagePreservesFields() {
        var msg = new Message.ToolResultMessage("id-1", "bash_exec", "output");
        assertEquals("id-1", msg.toolUseId());
        assertEquals("bash_exec", msg.toolName());
        assertEquals("output", msg.content());
    }
}
