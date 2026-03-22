package com.joz.tools;

import java.nio.file.Path;
import java.util.Map;

/** Context passed to every tool execution. */
public record ExecutionContext(
        Path projectRoot,
        String sessionId,
        Map<String, String> env) {

    public ExecutionContext {
        env = env != null ? Map.copyOf(env) : Map.of();
    }
}
