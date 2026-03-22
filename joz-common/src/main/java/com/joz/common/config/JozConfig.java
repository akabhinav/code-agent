package com.joz.common.config;

/** Root configuration record — fully immutable tree. */
public record JozConfig(
        LLMConfig llm,
        LLMConfig local,
        AgentConfig agent,
        PermissionConfig permissions,
        GitConfig git) {

    /** Returns a config with defaults filled in for any null fields. */
    public static JozConfig defaults() {
        return new JozConfig(
                LLMConfig.defaults(),
                LLMConfig.localDefaults(),
                AgentConfig.defaults(),
                PermissionConfig.defaults(),
                GitConfig.defaults());
    }

    /** Merges this config with an overlay — overlay values win when non-null. */
    public JozConfig merge(JozConfig overlay) {
        if (overlay == null) return this;
        return new JozConfig(
                llm != null ? llm.merge(overlay.llm) : overlay.llm,
                local != null ? local.merge(overlay.local) : overlay.local,
                agent != null ? agent.merge(overlay.agent) : overlay.agent,
                overlay.permissions != null ? overlay.permissions : permissions,
                overlay.git != null ? overlay.git : git);
    }
}
