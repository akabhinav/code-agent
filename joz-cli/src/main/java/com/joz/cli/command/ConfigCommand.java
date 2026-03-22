package com.joz.cli.command;

import com.joz.persistence.ConfigStore;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

/** Get and set configuration values. */
@Command(name = "config", description = "Get and set configuration", mixinStandardHelpOptions = true)
@Component
public class ConfigCommand implements Runnable {

    @Parameters(index = "0", description = "Action: get | set | list", defaultValue = "list")
    private String action;

    @Parameters(index = "1", description = "Config key (e.g. llm.model)", defaultValue = "")
    private String key;

    @Parameters(index = "2", description = "Config value (for set)", defaultValue = "")
    private String value;

    @Override
    public void run() {
        var globalConfigDir = Path.of(System.getProperty("user.home"), ".joz");
        var projectRoot = Path.of("").toAbsolutePath();
        var configStore = new ConfigStore(globalConfigDir, projectRoot);
        var config = configStore.load();

        switch (action) {
            case "list" -> {
                System.out.println("Current configuration:");
                System.out.println("  llm.provider:      " + config.llm().provider());
                System.out.println("  llm.model:         " + config.llm().model());
                System.out.println("  llm.base-url:      " + config.llm().baseUrl());
                System.out.println("  llm.temperature:   " + config.llm().temperature());
                System.out.println("  agent.max-turns:   " + config.agent().maxTurns());
                System.out.println("  agent.streaming:   " + config.agent().streaming());
                System.out.println("  git.auto-commit:   " + config.git().autoCommit());
                System.out.println("  git.commit-prefix: " + config.git().commitPrefix());
            }
            case "get" -> {
                if (key.isBlank()) {
                    System.err.println("Usage: joz config get <key>");
                    return;
                }
                var val = resolveKey(config, key);
                System.out.println(val);
            }
            case "set" -> {
                System.out.println("Config set is not yet supported — edit config.yaml directly.");
                System.out.println("  Global: ~/.joz/config.yaml");
                System.out.println("  Project: .joz/config.yaml");
            }
            default -> System.err.println("Unknown action: " + action);
        }
    }

    private String resolveKey(com.joz.common.config.JozConfig config, String key) {
        return switch (key) {
            case "llm.provider" -> config.llm().provider();
            case "llm.model" -> config.llm().model();
            case "llm.base-url" -> config.llm().baseUrl();
            case "llm.temperature" -> String.valueOf(config.llm().temperature());
            case "agent.max-turns" -> String.valueOf(config.agent().maxTurns());
            case "agent.context-window" -> String.valueOf(config.agent().contextWindow());
            case "agent.streaming" -> String.valueOf(config.agent().streaming());
            case "git.auto-commit" -> String.valueOf(config.git().autoCommit());
            case "git.commit-prefix" -> config.git().commitPrefix();
            default -> "Unknown key: " + key;
        };
    }
}
