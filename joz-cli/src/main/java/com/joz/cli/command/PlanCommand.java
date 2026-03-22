package com.joz.cli.command;

import com.joz.cli.render.TerminalRenderer;
import com.joz.core.PlanExecutor;
import com.joz.core.StreamEmitter;
import com.joz.persistence.ConfigStore;
import com.joz.persistence.SessionStore;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

/** Generate and execute a structured plan. */
@Command(name = "plan", description = "Generate and execute a structured plan", mixinStandardHelpOptions = true)
@Component
public class PlanCommand implements Runnable {

    @Parameters(index = "0", description = "Plan description")
    private String description;

    private final PlanExecutor planExecutor;
    private final StreamEmitter emitter;
    private final TerminalRenderer renderer;

    public PlanCommand(PlanExecutor planExecutor, StreamEmitter emitter, TerminalRenderer renderer) {
        this.planExecutor = planExecutor;
        this.emitter = emitter;
        this.renderer = renderer;
    }

    @Override
    public void run() {
        var projectRoot = Path.of("").toAbsolutePath();
        var globalConfigDir = Path.of(System.getProperty("user.home"), ".joz");

        var configStore = new ConfigStore(globalConfigDir, projectRoot);
        var config = configStore.load();

        var sessionStore = new SessionStore(globalConfigDir.resolve("sessions.db"));
        var sessionId = sessionStore.createSession(projectRoot.toString());

        emitter.subscribe(renderer);

        System.out.println("📋 Creating plan: " + description);
        var plan = planExecutor.createPlan(description, sessionId, config, projectRoot, globalConfigDir);

        System.out.println("\n📋 Plan: " + plan.title());
        for (int i = 0; i < plan.steps().size(); i++) {
            System.out.printf("   %d. %s%n", i + 1, plan.steps().get(i).description());
        }
        System.out.println();

        System.out.print("Execute this plan? [y/n] > ");
        try {
            var response = new java.util.Scanner(System.in).nextLine().strip().toLowerCase();
            if (response.equals("y") || response.equals("yes")) {
                planExecutor.executePlan(plan, config, projectRoot, globalConfigDir);
            } else {
                System.out.println("Plan cancelled.");
            }
        } catch (Exception e) {
            System.out.println("Plan cancelled.");
        }

        sessionStore.close();
    }
}
