package com.joz.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Discovers and holds all available tools. */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<Tool> toolBeans) {
        for (var tool : toolBeans) {
            tools.put(tool.name(), tool);
            log.info("Registered tool: {} [{}]", tool.name(), tool.category());
        }
    }

    /** Get a tool by name. */
    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /** Returns all registered tools. */
    public List<Tool> all() {
        return List.copyOf(tools.values());
    }

    /** Returns the number of registered tools. */
    public int count() {
        return tools.size();
    }
}
