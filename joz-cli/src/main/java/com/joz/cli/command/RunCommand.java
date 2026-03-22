package com.joz.cli.command;

import com.joz.cli.render.TerminalRenderer;
import com.joz.common.config.ApprovalMode;
import com.joz.common.config.JozConfig;
import com.joz.common.config.LLMConfig;
import com.joz.context.skill.Skill;
import com.joz.context.skill.SkillLoader;
import com.joz.core.AgentLoop;
import com.joz.core.PermissionManager;
import com.joz.core.StreamEmitter;
import com.joz.llm.LLMGateway;
import com.joz.persistence.ConfigStore;
import com.joz.persistence.SessionStore;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.TerminalBuilder;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Optional;

/** Main run command — single-shot or interactive REPL. */
@Command(name = "run", description = "Run the JOz agent", mixinStandardHelpOptions = true)
@Component
public class RunCommand implements Runnable {

    @Option(names = {"--prompt", "-p"}, description = "Single-shot prompt")
    private String prompt;

    @Option(names = {"--model", "-m"}, description = "Override model")
    private String model;

    @Option(names = {"--skill", "-s"}, description = "Active skill name")
    private String skillName;

    @Option(names = {"--max-turns"}, description = "Override max turns")
    private int maxTurns;

    @Option(names = {"--auto-approve"}, description = "Auto-approve all tool calls")
    private boolean autoApprove;

    @Option(names = {"--no-git"}, description = "Disable git auto-commit")
    private boolean noGit;

    @Option(names = {"-C"}, description = "Project directory")
    private Path projectDir;

    @Option(names = {"--local"}, description = "Use local Ollama instead of Claude")
    private boolean useLocal;

    @Option(names = {"--resume", "-r"}, description = "Resume the most recent session for this project")
    private boolean resume;

    @Option(names = {"--session"}, description = "Resume a specific session by ID")
    private String sessionId;

    private final AgentLoop agentLoop;
    private final StreamEmitter emitter;
    private final TerminalRenderer renderer;
    private final PermissionManager permissionManager;
    private final SkillLoader skillLoader;

    public RunCommand(AgentLoop agentLoop, StreamEmitter emitter, TerminalRenderer renderer,
                      PermissionManager permissionManager, SkillLoader skillLoader) {
        this.agentLoop = agentLoop;
        this.emitter = emitter;
        this.renderer = renderer;
        this.permissionManager = permissionManager;
        this.skillLoader = skillLoader;
    }

    @Override
    public void run() {
        var projectRoot = projectDir != null ? projectDir.toAbsolutePath() : Path.of("").toAbsolutePath();
        var globalConfigDir = Path.of(System.getProperty("user.home"), ".joz");

        // Load config
        var configStore = new ConfigStore(globalConfigDir, projectRoot);
        var config = configStore.load();

        // Apply CLI overrides
        config = applyOverrides(config);

        // Setup permissions
        permissionManager.configure(config.permissions());
        permissionManager.setAutoApproveAll(autoApprove);
        setupApprovalPrompt();

        // Subscribe renderer to events
        emitter.subscribe(renderer);

        // Load active skill
        Optional<Skill> activeSkill = Optional.empty();
        if (skillName != null) {
            activeSkill = skillLoader.load(skillName, projectRoot, globalConfigDir);
            if (activeSkill.isEmpty()) {
                System.err.println("Skill not found: " + skillName);
                return;
            }
        }

        // Create or resume session
        var sessionStore = new SessionStore(globalConfigDir.resolve("sessions.db"));
        agentLoop.setSessionStore(sessionStore);

        String activeSessionId;
        boolean resumed = false;

        if (sessionId != null) {
            // Resume specific session
            activeSessionId = sessionId;
            agentLoop.loadHistory(activeSessionId);
            resumed = true;
            var msgCount = sessionStore.getMessageCount(activeSessionId);
            System.out.printf("📂 Resumed session %s (%d messages)%n%n", activeSessionId.substring(0, 8), msgCount);
        } else if (resume) {
            // Resume most recent session for this project
            var lastSession = sessionStore.getLastSession(projectRoot.toString());
            if (lastSession.isPresent()) {
                activeSessionId = lastSession.get().id();
                agentLoop.loadHistory(activeSessionId);
                resumed = true;
                var msgCount = sessionStore.getMessageCount(activeSessionId);
                var title = lastSession.get().title();
                System.out.printf("📂 Resumed session %s%s (%d messages)%n%n",
                        activeSessionId.substring(0, 8),
                        title != null ? " — " + title : "",
                        msgCount);
            } else {
                System.out.println("No previous session found. Starting new session.");
                activeSessionId = sessionStore.createSession(projectRoot.toString());
            }
        } else {
            activeSessionId = sessionStore.createSession(projectRoot.toString());
        }

        renderer.renderBanner(config.llm().model(), projectRoot.toString());

        if (prompt != null) {
            // Single-shot mode
            runSingleShot(config, projectRoot, globalConfigDir, activeSessionId, activeSkill);
        } else {
            // Interactive REPL mode
            runRepl(config, projectRoot, globalConfigDir, activeSessionId, activeSkill);
        }

        sessionStore.close();
    }

    private void runSingleShot(JozConfig config, Path projectRoot, Path globalConfigDir,
                                String sessionId, Optional<Skill> activeSkill) {
        var options = new AgentLoop.RunOptions(
                prompt, config, projectRoot, globalConfigDir, sessionId, activeSkill);
        agentLoop.run(options);
    }

    private void runRepl(JozConfig config, Path projectRoot, Path globalConfigDir,
                          String sessionId, Optional<Skill> activeSkill) {
        try {
            var terminal = TerminalBuilder.builder().system(true).build();
            var lineReader = LineReaderBuilder.builder().terminal(terminal).build();

            while (true) {
                renderer.renderPrompt();
                String line;
                try {
                    line = lineReader.readLine();
                } catch (Exception e) {
                    break;
                }

                if (line == null || line.strip().equalsIgnoreCase("/quit") ||
                    line.strip().equalsIgnoreCase("/exit")) {
                    System.out.println("Goodbye!");
                    break;
                }

                if (line.isBlank()) continue;

                if (line.strip().equalsIgnoreCase("/clear")) {
                    System.out.print("\033[2J\033[H");
                    renderer.renderBanner(config.llm().model(), projectRoot.toString());
                    continue;
                }

                var options = new AgentLoop.RunOptions(
                        line.strip(), config, projectRoot, globalConfigDir, sessionId, activeSkill);
                agentLoop.run(options);
            }
        } catch (Exception e) {
            System.err.println("REPL error: " + e.getMessage());
        }
    }

    private void setupApprovalPrompt() {
        permissionManager.setApprovalPrompt(description -> {
            renderer.renderApprovalPrompt(description);
            try {
                var terminal = TerminalBuilder.builder().system(true).build();
                var reader = LineReaderBuilder.builder().terminal(terminal).build();
                var response = reader.readLine().strip().toLowerCase();
                return switch (response) {
                    case "y", "yes", "" -> true;
                    case "a", "always" -> {
                        // Extract tool name from description
                        var toolName = description.split("\n")[0].replace("Tool: ", "");
                        permissionManager.setToolOverride(toolName, ApprovalMode.AUTO_APPROVE);
                        yield true;
                    }
                    case "v", "never" -> {
                        var toolName = description.split("\n")[0].replace("Tool: ", "");
                        permissionManager.setToolOverride(toolName, ApprovalMode.DENY);
                        yield false;
                    }
                    default -> false;
                };
            } catch (Exception e) {
                return false;
            }
        });
    }

    private JozConfig applyOverrides(JozConfig config) {
        var llm = config.llm();
        if (model != null) {
            llm = new LLMConfig(llm.provider(), model, llm.apiKey(), llm.baseUrl(), llm.temperature());
        }
        if (useLocal && config.local() != null) {
            llm = config.local();
            if (model != null) {
                llm = new LLMConfig(llm.provider(), model, llm.apiKey(), llm.baseUrl(), llm.temperature());
            }
        }

        var agent = config.agent();
        if (maxTurns > 0) {
            agent = new com.joz.common.config.AgentConfig(
                    maxTurns, agent.contextWindow(), agent.streaming(), agent.showThinking());
        }

        var git = config.git();
        if (noGit) {
            git = new com.joz.common.config.GitConfig(false, git.commitPrefix());
        }

        return new JozConfig(llm, config.local(), agent, config.permissions(), git);
    }
}
