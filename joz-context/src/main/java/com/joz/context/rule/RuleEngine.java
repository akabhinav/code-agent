package com.joz.context.rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Loads and merges rules from global and project rules.md files. */
@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);
    private static final String RULES_FILE = "rules.md";

    /** Loads and merges rules from global and project directories. */
    public String loadRules(Path projectRoot, Path globalConfigDir) {
        var sections = new ArrayList<String>();

        loadFile(globalConfigDir.resolve(RULES_FILE))
                .ifPresent(content -> sections.add("## Global Rules\n" + content));

        loadFile(projectRoot.resolve(".joz").resolve(RULES_FILE))
                .ifPresent(content -> sections.add("## Project Rules\n" + content));

        if (sections.isEmpty()) {
            log.debug("No rules files found");
            return "";
        }

        log.info("Loaded rules from {} source(s)", sections.size());
        return String.join("\n\n", sections);
    }

    private Optional<String> loadFile(Path path) {
        if (!Files.exists(path)) return Optional.empty();
        try {
            var content = Files.readString(path).strip();
            return content.isEmpty() ? Optional.empty() : Optional.of(content);
        } catch (IOException e) {
            log.warn("Failed to read rules file {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }
}
