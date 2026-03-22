package com.joz.common.config;

/** Agent behavior configuration. */
public record AgentConfig(
        int maxTurns,
        int contextWindow,
        boolean streaming,
        boolean showThinking) {

    /** Sensible defaults for the agent loop. */
    public static AgentConfig defaults() {
        return new AgentConfig(25, 128_000, true, true);
    }

    /** Merges with overlay — overlay values win when different from defaults. */
    public AgentConfig merge(AgentConfig overlay) {
        if (overlay == null) return this;
        return new AgentConfig(
                overlay.maxTurns > 0 ? overlay.maxTurns : maxTurns,
                overlay.contextWindow > 0 ? overlay.contextWindow : contextWindow,
                overlay.streaming,
                overlay.showThinking);
    }
}
