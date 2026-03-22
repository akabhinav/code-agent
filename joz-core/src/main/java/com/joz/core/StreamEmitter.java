package com.joz.core;

import com.joz.common.event.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Publishes AgentEvents to all registered subscribers. */
@Component
public class StreamEmitter {

    private static final Logger log = LoggerFactory.getLogger(StreamEmitter.class);
    private final List<Consumer<AgentEvent>> subscribers = new CopyOnWriteArrayList<>();

    /** Register a subscriber to receive events. */
    public void subscribe(Consumer<AgentEvent> subscriber) {
        subscribers.add(subscriber);
    }

    /** Remove a subscriber. */
    public void unsubscribe(Consumer<AgentEvent> subscriber) {
        subscribers.remove(subscriber);
    }

    /** Emit an event to all subscribers. */
    public void emit(AgentEvent event) {
        for (var subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                log.warn("Subscriber failed to handle event: {}", e.getMessage());
            }
        }
    }
}
