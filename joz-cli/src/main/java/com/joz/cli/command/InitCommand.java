package com.joz.cli.command;

import com.joz.context.codebase.CodebaseIndexer;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Initialize a .joz directory in the current project. */
@Command(name = "init", description = "Initialize JOz in the current project", mixinStandardHelpOptions = true)
@Component
public class InitCommand implements Runnable {

    private final CodebaseIndexer codebaseIndexer;

    public InitCommand(CodebaseIndexer codebaseIndexer) {
        this.codebaseIndexer = codebaseIndexer;
    }

    @Override
    public void run() {
        var projectRoot = Path.of("").toAbsolutePath();
        var jozDir = projectRoot.resolve(".joz");

        if (Files.exists(jozDir)) {
            System.out.println(".joz directory already exists.");
            return;
        }

        try {
            Files.createDirectories(jozDir.resolve("skills"));
            Files.createDirectories(jozDir.resolve("plans"));

            // Default config
            Files.writeString(jozDir.resolve("config.yaml"), """
                    # JOz project configuration
                    # Overrides ~/.joz/config.yaml settings for this project

                    llm:
                      # model: claude-sonnet-4-6-20250514

                    agent:
                      max-turns: 25

                    git:
                      auto-commit: true
                      commit-prefix: "joz:"
                    """);

            // Default rules
            Files.writeString(jozDir.resolve("rules.md"), """
                    # Project Rules for JOz

                    Add project-specific rules here. These will be included in the agent's system prompt.

                    ## Examples
                    - Always use conventional commits
                    - Run tests before committing
                    - Follow the existing code style
                    """);

            System.out.println("✓ Initialized .joz directory");
            System.out.println("  Created:");
            System.out.println("    .joz/config.yaml  — project config");
            System.out.println("    .joz/rules.md     — project rules");
            System.out.println("    .joz/skills/      — custom skills");
            System.out.println("    .joz/plans/       — saved plans");

            // Show detected project info
            var summary = codebaseIndexer.buildSummary(projectRoot);
            System.out.println("\n" + summary.lines().limit(5).reduce("", (a, b) -> a + "\n" + b).strip());

        } catch (IOException e) {
            System.err.println("Failed to initialize: " + e.getMessage());
        }
    }
}
