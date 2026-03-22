package com.joz.context.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Discovers and loads skills from .joz/skills/ directories. */
@Component
public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);
    private static final String SKILL_FILE = "SKILL.md";

    /** Loads all skills from project, cross-tool compat, and global directories. */
    public List<Skill> loadAll(Path projectRoot, Path globalConfigDir) {
        var skills = new LinkedHashMap<String, Skill>();

        // Global skills (lowest priority)
        loadFromDir(globalConfigDir.resolve("skills"), skills);

        // Cross-tool compat
        loadFromDir(projectRoot.resolve(".agents/skills"), skills);

        // Project skills (highest priority)
        loadFromDir(projectRoot.resolve(".joz/skills"), skills);

        log.info("Loaded {} skill(s)", skills.size());
        return List.copyOf(skills.values());
    }

    /** Loads a specific skill by name. */
    public Optional<Skill> load(String name, Path projectRoot, Path globalConfigDir) {
        return loadAll(projectRoot, globalConfigDir).stream()
                .filter(s -> s.name().equals(name))
                .findFirst();
    }

    private void loadFromDir(Path skillsDir, Map<String, Skill> skills) {
        if (!Files.isDirectory(skillsDir)) return;

        try (var dirs = Files.list(skillsDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                var skillFile = dir.resolve(SKILL_FILE);
                if (Files.exists(skillFile)) {
                    try {
                        var skill = parseSkillFile(skillFile, dir.getFileName().toString());
                        skills.put(skill.name(), skill);
                        log.debug("Loaded skill: {}", skill.name());
                    } catch (IOException e) {
                        log.warn("Failed to load skill from {}: {}", skillFile, e.getMessage());
                    }
                }
            });
        } catch (IOException e) {
            log.debug("Could not read skills directory: {}", skillsDir);
        }
    }

    private Skill parseSkillFile(Path file, String dirName) throws IOException {
        var content = Files.readString(file);
        String name = dirName;
        String description = "";
        String instructions = content;

        // Parse YAML frontmatter
        if (content.startsWith("---")) {
            int endIdx = content.indexOf("---", 3);
            if (endIdx > 0) {
                var frontmatter = content.substring(3, endIdx).strip();
                instructions = content.substring(endIdx + 3).strip();

                for (var line : frontmatter.split("\n")) {
                    line = line.strip();
                    if (line.startsWith("name:")) {
                        name = line.substring(5).strip();
                    } else if (line.startsWith("description:")) {
                        description = line.substring(12).strip();
                    }
                }
            }
        }

        return new Skill(name, description, instructions);
    }
}
