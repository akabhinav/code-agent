package com.joz.cli.command;

import com.joz.context.skill.SkillLoader;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

/** List and inspect available skills. */
@Command(name = "skill", description = "Manage skills", mixinStandardHelpOptions = true)
@Component
public class SkillCommand implements Runnable {

    @Parameters(index = "0", description = "Subcommand: list | run <name>", defaultValue = "list")
    private String action;

    @Parameters(index = "1", description = "Skill name (for run)", defaultValue = "")
    private String skillName;

    private final SkillLoader skillLoader;

    public SkillCommand(SkillLoader skillLoader) {
        this.skillLoader = skillLoader;
    }

    @Override
    public void run() {
        var projectRoot = Path.of("").toAbsolutePath();
        var globalConfigDir = Path.of(System.getProperty("user.home"), ".joz");

        switch (action) {
            case "list" -> {
                var skills = skillLoader.loadAll(projectRoot, globalConfigDir);
                if (skills.isEmpty()) {
                    System.out.println("No skills found.");
                    System.out.println("Add skills to .joz/skills/<name>/SKILL.md");
                    return;
                }
                System.out.println("Available skills:");
                for (var skill : skills) {
                    System.out.printf("  %-20s %s%n", skill.name(), skill.description());
                }
            }
            case "run" -> {
                if (skillName.isBlank()) {
                    System.err.println("Usage: joz skill run <name>");
                    return;
                }
                System.out.println("Use: joz run --skill " + skillName);
            }
            default -> System.err.println("Unknown action: " + action + ". Use 'list' or 'run'.");
        }
    }
}
