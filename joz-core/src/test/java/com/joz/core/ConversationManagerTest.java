package com.joz.core;

import com.joz.common.model.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationManagerTest {

    @Test
    void addsAndRetrievesMessages() {
        var mgr = new ConversationManager();
        mgr.add(new Message.UserMessage("hello"));
        mgr.add(new Message.AssistantMessage("hi there", List.of()));

        assertEquals(2, mgr.size());
        assertEquals(2, mgr.history().size());
    }

    @Test
    void truncatesWhenOverBudget() {
        var mgr = new ConversationManager();
        // Each message ~10 chars = ~2.5 tokens
        for (int i = 0; i < 100; i++) {
            mgr.add(new Message.UserMessage("message number " + i + " with some content"));
        }

        var truncated = mgr.truncated(50);
        assertTrue(truncated.size() < 100);
        assertTrue(truncated.size() > 0);
    }

    @Test
    void clearRemovesAllMessages() {
        var mgr = new ConversationManager();
        mgr.add(new Message.UserMessage("test"));
        mgr.clear();
        assertEquals(0, mgr.size());
    }
}
