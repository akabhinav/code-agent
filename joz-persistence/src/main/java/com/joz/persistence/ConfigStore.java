package com.joz.persistence;

import com.joz.common.config.*;
import com.joz.common.exception.JozException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Loads and merges YAML configuration from global and project sources. */
public class ConfigStore {

    private static final Logger log = LoggerFactory.getLogger(ConfigStore.class);
    private static final String CONFIG_FILE = "config.yaml";

    private final Path globalConfigDir;
    private final Path projectRoot;

    public ConfigStore(Path globalConfigDir, Path projectRoot) {
        this.globalConfigDir = globalConfigDir;
        this.projectRoot = projectRoot;
    }

    /** Loads merged config: defaults → global → project. */
    public JozConfig load() {
        var config = JozConfig.defaults();
        var globalConfig = loadFrom(globalConfigDir.resolve(CONFIG_FILE));
        if (globalConfig.isPresent()) {
            config = config.merge(globalConfig.get());
        }
        var projectConfig = loadFrom(projectRoot.resolve(".joz").resolve(CONFIG_FILE));
        if (projectConfig.isPresent()) {
            config = config.merge(projectConfig.get());
        }
        return resolveEnvVars(config);
    }

    /** Loads config from a YAML file, returning empty if file doesn't exist. */
    @SuppressWarnings("unchecked")
    private Optional<JozConfig> loadFrom(Path path) {
        if (!Files.exists(path)) {
            log.debug("Config file not found: {}", path);
            return Optional.empty();
        }
        try (var reader = Files.newBufferedReader(path)) {
            var yaml = new Yaml();
            Map<String, Object> data = yaml.load(reader);
            if (data == null) return Optional.empty();
            return Optional.of(parseConfig(data));
        } catch (IOException e) {
            throw new JozException.ConfigException("Failed to read config: " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private JozConfig parseConfig(Map<String, Object> data) {
        var llm = parseLLMConfig((Map<String, Object>) data.get("llm"));
        var local = parseLLMConfig((Map<String, Object>) data.get("local"));
        var agent = parseAgentConfig((Map<String, Object>) data.get("agent"));
        var permissions = parsePermissionConfig((Map<String, Object>) data.get("permissions"));
        var git = parseGitConfig((Map<String, Object>) data.get("git"));
        return new JozConfig(llm, local, agent, permissions, git);
    }

    private LLMConfig parseLLMConfig(Map<String, Object> data) {
        if (data == null) return null;
        return new LLMConfig(
                (String) data.get("provider"),
                (String) data.get("model"),
                (String) data.get("api-key"),
                (String) data.get("base-url"),
                data.containsKey("temperature") ? ((Number) data.get("temperature")).doubleValue() : 0.0);
    }

    private AgentConfig parseAgentConfig(Map<String, Object> data) {
        if (data == null) return null;
        return new AgentConfig(
                data.containsKey("max-turns") ? ((Number) data.get("max-turns")).intValue() : 0,
                data.containsKey("context-window") ? ((Number) data.get("context-window")).intValue() : 0,
                data.containsKey("streaming") ? (Boolean) data.get("streaming") : true,
                data.containsKey("show-thinking") ? (Boolean) data.get("show-thinking") : true);
    }

    @SuppressWarnings("unchecked")
    private PermissionConfig parsePermissionConfig(Map<String, Object> data) {
        if (data == null) return null;
        return new PermissionConfig(
                parseApprovalMode((String) data.get("file-read")),
                parseApprovalMode((String) data.get("list-directory")),
                parseApprovalMode((String) data.get("code-search")),
                parseApprovalMode((String) data.get("file-search")),
                parseApprovalMode((String) data.get("file-write")),
                parseApprovalMode((String) data.get("bash-exec")),
                parseApprovalMode((String) data.get("git-ops")));
    }

    private ApprovalMode parseApprovalMode(String value) {
        if (value == null) return ApprovalMode.ASK_FIRST;
        return switch (value.toLowerCase().replace("-", "_")) {
            case "auto_approve", "auto" -> ApprovalMode.AUTO_APPROVE;
            case "ask_first" -> ApprovalMode.ASK_FIRST;
            case "ask_always" -> ApprovalMode.ASK_ALWAYS;
            case "deny" -> ApprovalMode.DENY;
            default -> ApprovalMode.ASK_FIRST;
        };
    }

    private GitConfig parseGitConfig(Map<String, Object> data) {
        if (data == null) return null;
        return new GitConfig(
                data.containsKey("auto-commit") ? (Boolean) data.get("auto-commit") : true,
                (String) data.getOrDefault("commit-prefix", "joz:"));
    }

    /** Resolves ${ENV_VAR} references in config values. */
    private JozConfig resolveEnvVars(JozConfig config) {
        var llm = config.llm();
        if (llm != null && llm.apiKey() != null && llm.apiKey().startsWith("${")) {
            var envVar = llm.apiKey().substring(2, llm.apiKey().length() - 1);
            var resolved = System.getenv(envVar);
            llm = new LLMConfig(llm.provider(), llm.model(), resolved, llm.baseUrl(), llm.temperature());
            config = new JozConfig(llm, config.local(), config.agent(), config.permissions(), config.git());
        }
        return config;
    }
}
