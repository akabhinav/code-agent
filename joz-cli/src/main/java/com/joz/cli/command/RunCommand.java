package com.joz.cli.command;

import com.joz.cli.ui.Ansi;
import com.joz.cli.ui.JozTerminalUI;
import com.joz.common.config.ApprovalMode;
import com.joz.common.config.JozConfig;
import com.joz.common.config.LLMConfig;
import com.joz.context.skill.Skill;
import com.joz.context.skill.SkillLoader;
import com.joz.core.AgentLoop;
import com.joz.core.ConversationManager;
import com.joz.core.PermissionManager;
import com.joz.core.StreamEmitter;
import com.joz.persistence.ConfigStore;
import com.joz.persistence.SessionStore;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.TerminalBuilder;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Optional;

/** Main run command — single-shot or interactive REPL with rich UI. */
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
    private final JozTerminalUI ui;
    private final PermissionManager permissionManager;
    private final SkillLoader skillLoader;
    private final ConversationManager conversationManager;

    public RunCommand(AgentLoop agentLoop, StreamEmitter emitter, JozTerminalUI ui,
                      PermissionManager permissionManager, SkillLoader skillLoader,
                      ConversationManager conversationManager) {
        this.agentLoop = agentLoop;
        this.emitter = emitter;
        this.ui = ui;
        this.permissionManager = permissionManager;
        this.skillLoader = skillLoader;
        this.conversationManager = conversationManager;
    }

    @Override
    public void run() {
        var projectRoot = projectDir != null ? projectDir.toAbsolutePath() : Path.of("").toAbsolutePath();
        var globalConfigDir = Path.of(System.getProperty("user.home"), ".joz");

        // Load config
        var configStore = new ConfigStore(globalConfigDir, projectRoot);
        var config = configStore.load();
        config = applyOverrides(config);

        // Setup permissions
        permissionManager.configure(config.permissions());
        permissionManager.setAutoApproveAll(autoApprove);
        setupApprovalPrompt();

        // Subscribe UI to agent events
        emitter.subscribe(ui);

        // Load active skill
        Optional<Skill> activeSkill = Optional.empty();
        if (skillName != null) {
            activeSkill = skillLoader.load(skillName, projectRoot, globalConfigDir);
            if (activeSkill.isEmpty()) {
                System.err.println(Ansi.RED + "Skill not found: " + skillName + Ansi.RESET);
                return;
            }
        }

        // Create or resume session
        var sessionStore = new SessionStore(globalConfigDir.resolve("sessions.db"));
        agentLoop.setSessionStore(sessionStore);

        String activeSessionId;
        boolean resumed = false;
        int resumedMsgCount = 0;

        if (sessionId != null) {
            activeSessionId = resolveSessionId(sessionStore, sessionId);
            if (activeSessionId == null) {
                System.err.println(Ansi.RED + "Session not found: " + sessionId + Ansi.RESET);
                sessionStore.close();
                return;
            }
            agentLoop.loadHistory(activeSessionId);
            resumed = true;
            resumedMsgCount = sessionStore.getMessageCount(activeSessionId);
        } else if (resume) {
            var lastSession = sessionStore.getLastSession(projectRoot.toString());
            if (lastSession.isPresent()) {
                activeSessionId = lastSession.get().id();
                agentLoop.loadHistory(activeSessionId);
                resumed = true;
                resumedMsgCount = sessionStore.getMessageCount(activeSessionId);
            } else {
                activeSessionId = sessionStore.createSession(projectRoot.toString());
            }
        } else {
            activeSessionId = sessionStore.createSession(projectRoot.toString());
        }

        // Render welcome
        ui.renderWelcome(config.llm().model(), projectRoot.toString(),
                activeSessionId, resumed, resumedMsgCount);

        if (prompt != null) {
            runSingleShot(config, projectRoot, globalConfigDir, activeSessionId, activeSkill);
        } else {
            runRepl(config, projectRoot, globalConfigDir, activeSessionId, activeSkill, sessionStore);
        }

        sessionStore.close();
    }

    private void runSingleShot(JozConfig config, Path projectRoot, Path globalConfigDir,
                                String sessionId, Optional<Skill> activeSkill) {
        ui.onAgentStart();
        var options = new AgentLoop.RunOptions(
                prompt, config, projectRoot, globalConfigDir, sessionId, activeSkill);
        agentLoop.run(options);
        ui.onAgentEnd();
    }

    private void runRepl(JozConfig config, Path projectRoot, Path globalConfigDir,
                          String sessionId, Optional<Skill> activeSkill, SessionStore sessionStore) {
        try {
            var terminal = TerminalBuilder.builder().system(true).build();
            var lineReader = LineReaderBuilder.builder().terminal(terminal).build();

            while (true) {
                ui.renderPrompt();
                String line;
                try {
                    line = lineReader.readLine();
                } catch (Exception e) {
                    break;
                }

                if (line == null || line.strip().equalsIgnoreCase("/quit")
                        || line.strip().equalsIgnoreCase("/exit")) {
                    System.out.println();
                    System.out.println(Ansi.DIM + "  Goodbye! Session saved: "
                            + sessionId.substring(0, 8) + Ansi.RESET);
                    System.out.println();
                    break;
                }

                if (line.isBlank()) continue;

                var cmd = line.strip().toLowerCase();

                if (cmd.equals("/clear")) {
                    System.out.print(Ansi.CLEAR_SCREEN);
                    ui.renderWelcome(config.llm().model(), projectRoot.toString(),
                            sessionId, false, 0);
                    continue;
                }

                if (cmd.equals("/help")) {
                    ui.renderHelp();
                    continue;
                }

                if (cmd.equals("/history")) {
                    ui.renderHistory(conversationManager.history());
                    continue;
                }

                if (cmd.equals("/session")) {
                    ui.renderSessionInfo(sessionId, projectRoot.toString(),
                            sessionStore.getMessageCount(sessionId));
                    continue;
                }

                // Normal prompt — run agent
                ui.onAgentStart();
                var options = new AgentLoop.RunOptions(
                        line.strip(), config, projectRoot, globalConfigDir, sessionId, activeSkill);
                agentLoop.run(options);
                ui.onAgentEnd();
            }
        } catch (Exception e) {
            System.err.println(Ansi.RED + "REPL error: " + e.getMessage() + Ansi.RESET);
        }
    }

    private void setupApprovalPrompt() {
        permissionManager.setApprovalPrompt(description -> {
            try {
                var terminal = TerminalBuilder.builder().system(true).build();
                var reader = LineReaderBuilder.builder().terminal(terminal).build();
                var response = reader.readLine().strip().toLowerCase();
                return switch (response) {
                    case "y", "yes", "" -> true;
                    case "a", "always" -> {
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

    private String resolveSessionId(SessionStore store, String shortId) {
        var sessions = store.listSessions(100);
        for (var session : sessions) {
            if (session.id().startsWith(shortId)) {
                return session.id();
            }
        }
        return null;
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
