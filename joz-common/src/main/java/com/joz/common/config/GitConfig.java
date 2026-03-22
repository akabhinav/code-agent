package com.joz.common.config;

/** Git integration configuration. */
public record GitConfig(boolean autoCommit, String commitPrefix) {

    /** Default git config — auto-commit enabled with "joz:" prefix. */
    public static GitConfig defaults() {
        return new GitConfig(true, "joz:");
    }
}
