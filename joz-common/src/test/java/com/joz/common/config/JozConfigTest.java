package com.joz.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JozConfigTest {

    @Test
    void defaultsProducesValidConfig() {
        var config = JozConfig.defaults();
        assertNotNull(config.llm());
        assertNotNull(config.agent());
        assertNotNull(config.permissions());
        assertNotNull(config.git());
        assertEquals("anthropic", config.llm().provider());
        assertEquals(25, config.agent().maxTurns());
    }

    @Test
    void mergeOverlayWins() {
        var base = JozConfig.defaults();
        var overlay = new JozConfig(
                new LLMConfig(null, "claude-haiku-4-5-20251001", null, null, 0.0),
                null,
                new AgentConfig(50, 0, true, true),
                null,
                null);
        var merged = base.merge(overlay);
        assertEquals("claude-haiku-4-5-20251001", merged.llm().model());
        assertEquals("anthropic", merged.llm().provider()); // preserved from base
        assertEquals(50, merged.agent().maxTurns());
    }

    @Test
    void mergeWithNullReturnsThis() {
        var config = JozConfig.defaults();
        assertSame(config, config.merge(null));
    }
}
