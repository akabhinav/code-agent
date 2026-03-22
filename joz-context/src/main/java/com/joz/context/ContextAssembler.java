package com.joz.context;

import com.joz.common.model.Message;
import com.joz.context.codebase.CodebaseIndexer;
import com.joz.context.rule.RuleEngine;
import com.joz.context.skill.Skill;
import com.joz.context.skill.SkillLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds the full system prompt from rules, skills, and codebase context. */
@Component
public class ContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ContextAssembler.class);

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are JOz, an expert AI coding agent. You operate in a terminal environment \
            with full access to the filesystem and shell. You help developers write, debug, \
            test, and deploy code.

            ## Your capabilities
            You have access to these tools:
            - bash_exec: Run shell commands
            - file_read: Read file contents
            - file_write: Create or modify files (create, overwrite, or patch/str_replace)
            - file_search: Search file contents with regex
            - code_search: Find code symbols (functions, classes, imports)
            - git_operations: Git status, diff, log, add, commit, branch, checkout
            - list_directory: List directory tree

            ## How you work
            1. ALWAYS read files and understand the codebase before making changes
            2. Use file_search and code_search to find relevant code
            3. Make precise, minimal changes using file_write in patch mode
            4. Verify your changes by running tests or builds via bash_exec
            5. Commit your changes via git_operations if auto-commit is enabled

            ## Rules
            - Never modify files outside the project root
            - Always show your reasoning before making changes
            - When unsure, ask the user for clarification
            - Prefer small, incremental changes over large rewrites
            - Run tests after making changes when possible

            %s

            ## Project context
            %s

            %s
            """;

    private final SkillLoader skillLoader;
    private final RuleEngine ruleEngine;
    private final CodebaseIndexer codebaseIndexer;

    public ContextAssembler(SkillLoader skillLoader, RuleEngine ruleEngine, CodebaseIndexer codebaseIndexer) {
        this.skillLoader = skillLoader;
        this.ruleEngine = ruleEngine;
        this.codebaseIndexer = codebaseIndexer;
    }

    /** Builds the complete system prompt. */
    public String buildSystemPrompt(Path projectRoot, Path globalConfigDir, Optional<Skill> activeSkill) {
        var rules = ruleEngine.loadRules(projectRoot, globalConfigDir);
        var codebaseSummary = codebaseIndexer.buildSummary(projectRoot);
        var skillSection = activeSkill
                .map(s -> "## Active Skill: %s\n%s".formatted(s.name(), s.instructions()))
                .orElse("");

        return SYSTEM_PROMPT_TEMPLATE.formatted(rules, codebaseSummary, skillSection).strip();
    }

    /** Builds the initial message list with system prompt and user message. */
    public List<Message> buildMessages(String systemPrompt, String userPrompt, List<Message> history) {
        var messages = new ArrayList<>(history);
        messages.add(new Message.UserMessage(userPrompt));
        return List.copyOf(messages);
    }
}
