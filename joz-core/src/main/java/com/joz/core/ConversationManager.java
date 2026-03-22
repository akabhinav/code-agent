package com.joz.core;

import com.joz.common.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Manages conversation history with truncation to fit context windows. */
@Component
public class ConversationManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationManager.class);

    private final List<Message> history = new ArrayList<>();

    /** Adds a message to the history. */
    public void add(Message message) {
        history.add(message);
    }

    /** Returns the full conversation history. */
    public List<Message> history() {
        return List.copyOf(history);
    }

    /** Truncates history to fit within the approximate token limit. */
    public List<Message> truncated(int maxTokens) {
        // Rough estimate: 4 chars per token
        int estimatedTokens = estimateTokens();
        if (estimatedTokens <= maxTokens) {
            return List.copyOf(history);
        }

        // Keep first message (usually the first user prompt) and trim from the middle
        log.info("Truncating conversation from ~{} to ~{} tokens", estimatedTokens, maxTokens);
        var result = new ArrayList<Message>();
        int budget = maxTokens;

        // Always keep the most recent messages
        var reversed = new ArrayList<>(history);
        java.util.Collections.reverse(reversed);

        for (var msg : reversed) {
            int msgTokens = estimateMessageTokens(msg);
            if (budget - msgTokens < 0 && !result.isEmpty()) break;
            budget -= msgTokens;
            result.addFirst(msg);
        }

        return List.copyOf(result);
    }

    /** Clears the conversation history. */
    public void clear() {
        history.clear();
    }

    /** Returns the number of messages. */
    public int size() {
        return history.size();
    }

    private int estimateTokens() {
        return history.stream().mapToInt(this::estimateMessageTokens).sum();
    }

    private int estimateMessageTokens(Message message) {
        var text = switch (message) {
            case Message.UserMessage(var content) -> content;
            case Message.AssistantMessage(var t, var tc) -> t != null ? t : "";
            case Message.SystemMessage(var content) -> content;
            case Message.ToolResultMessage(var id, var n, var content) -> content;
        };
        return text.length() / 4;
    }
}
