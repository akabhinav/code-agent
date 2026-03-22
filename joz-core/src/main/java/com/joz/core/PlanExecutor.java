package com.joz.core;

import com.joz.common.event.AgentEvent;
import com.joz.common.config.JozConfig;
import com.joz.context.skill.Skill;
import com.joz.persistence.PlanStore;
import com.joz.persistence.PlanStore.Plan;
import com.joz.persistence.PlanStore.PlanStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Executes structured plans step by step. */
@Component
public class PlanExecutor {

    private static final Logger log = LoggerFactory.getLogger(PlanExecutor.class);

    private final AgentLoop agentLoop;
    private final StreamEmitter emitter;

    public PlanExecutor(AgentLoop agentLoop, StreamEmitter emitter) {
        this.agentLoop = agentLoop;
        this.emitter = emitter;
    }

    /** Creates a plan from a description by asking the LLM. */
    public Plan createPlan(String description, String sessionId, JozConfig config,
                           Path projectRoot, Path globalConfigDir) {
        var planPrompt = """
                Create a detailed step-by-step plan for: %s

                List each step as a numbered item. Each step should be a specific, actionable task.
                Only list the steps, nothing else.
                """.formatted(description);

        // Run the agent to get the plan steps
        var options = new AgentLoop.RunOptions(
                planPrompt, config, projectRoot, globalConfigDir,
                sessionId, Optional.empty());

        // Capture the response text
        var capturedText = new StringBuilder();
        emitter.subscribe(event -> {
            if (event instanceof AgentEvent.TextChunkEvent(var text)) {
                capturedText.append(text);
            }
        });

        agentLoop.run(options);

        // Parse steps from response
        var steps = parseSteps(capturedText.toString());
        return new Plan(
                UUID.randomUUID().toString(),
                sessionId,
                description,
                steps,
                "pending");
    }

    /** Executes a plan step by step. */
    public void executePlan(Plan plan, JozConfig config, Path projectRoot,
                            Path globalConfigDir) {
        var updatedSteps = new ArrayList<>(plan.steps());

        for (int i = 0; i < updatedSteps.size(); i++) {
            var step = updatedSteps.get(i);
            emitter.emit(new AgentEvent.PlanUpdateEvent(plan.id(), i, "running"));

            var options = new AgentLoop.RunOptions(
                    step.description(), config, projectRoot, globalConfigDir,
                    plan.sessionId(), Optional.empty());

            agentLoop.run(options);

            updatedSteps.set(i, new PlanStep(step.description(), "completed", "Done"));
            emitter.emit(new AgentEvent.PlanUpdateEvent(plan.id(), i, "completed"));
        }
    }

    private List<PlanStep> parseSteps(String text) {
        var steps = new ArrayList<PlanStep>();
        for (var line : text.split("\n")) {
            line = line.strip();
            if (line.matches("^\\d+\\..*")) {
                var desc = line.replaceFirst("^\\d+\\.\\s*", "");
                steps.add(new PlanStep(desc, "pending", null));
            }
        }
        return steps.isEmpty()
                ? List.of(new PlanStep(text.strip(), "pending", null))
                : steps;
    }
}
