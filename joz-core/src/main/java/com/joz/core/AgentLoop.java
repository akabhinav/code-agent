package com.joz.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.joz.common.config.GitConfig;
import com.joz.common.config.JozConfig;
import com.joz.common.event.AgentEvent;
import com.joz.common.event.AgentEvent.*;
import com.joz.common.model.Message;
import com.joz.common.model.Message.*;
import com.joz.common.model.ToolCall;
import com.joz.common.model.ToolResult;
import com.joz.common.model.ToolResult.*;
import com.joz.context.ContextAssembler;
import com.joz.context.skill.Skill;
import com.joz.llm.ChatRequest;
import com.joz.llm.LLMGateway;
import com.joz.llm.LLMResponse;
import com.joz.tools.ExecutionContext;
import com.joz.tools.Tool;
import com.joz.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The ReAct agent loop — the heart of JOz. */
@Component
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final LLMGateway llmGateway;
    private final ToolRegistry toolRegistry;
    private final ContextAssembler contextAssembler;
    private final PermissionManager permissionManager;
    private final ConversationManager conversationManager;
    private final StreamEmitter emitter;

    public AgentLoop(LLMGateway llmGateway, ToolRegistry toolRegistry,
                     ContextAssembler contextAssembler, PermissionManager permissionManager,
                     ConversationManager conversationManager, StreamEmitter emitter) {
        this.llmGateway = llmGateway;
        this.toolRegistry = toolRegistry;
        this.contextAssembler = contextAssembler;
        this.permissionManager = permissionManager;
        this.conversationManager = conversationManager;
        this.emitter = emitter;
    }

    /** Runs the agent loop for a single user prompt. */
    public void run(RunOptions options) {
        var startTime = Instant.now();
        int turns = 0;
        int totalToolCalls = 0;

        var systemPrompt = contextAssembler.buildSystemPrompt(
                options.projectRoot(), options.globalConfigDir(), options.activeSkill());

        // Add user message to history
        conversationManager.add(new UserMessage(options.prompt()));

        var toolDefs = toolRegistry.all().stream()
                .map(t -> new ChatRequest.ToolDefinition(t.name(), t.description(), t.inputSchema()))
                .toList();

        try {
            while (turns < options.config().agent().maxTurns()) {
                turns++;
                log.debug("Turn {}/{}", turns, options.config().agent().maxTurns());

                var messages = conversationManager.truncated(options.config().agent().contextWindow());

                var request = new ChatRequest(
                        systemPrompt, messages, toolDefs,
                        options.config().llm().model(),
                        options.config().llm().temperature(),
                        16384);

                LLMResponse response;
                try {
                    response = llmGateway.chat(request);
                } catch (Exception e) {
                    emitter.emit(new ErrorEvent("LLM call failed: " + e.getMessage(), e));
                    break;
                }

                // Emit text content
                if (response.text() != null && !response.text().isBlank()) {
                    emitter.emit(new TextChunkEvent(response.text()));
                }

                // If no tool calls, we're done
                if (!response.hasToolCalls()) {
                    conversationManager.add(new AssistantMessage(response.text(), List.of()));
                    break;
                }

                // Process tool calls
                conversationManager.add(new AssistantMessage(response.text(), response.toolCalls()));

                boolean anyFileWritten = false;

                for (var toolCall : response.toolCalls()) {
                    totalToolCalls++;
                    var result = executeToolCall(toolCall, options);

                    // Track file writes for auto-commit
                    if (isFileWriteCall(toolCall) && result instanceof ToolSuccess) {
                        anyFileWritten = true;
                    }

                    // Add tool result to conversation
                    var resultText = switch (result) {
                        case ToolSuccess(var output) -> output;
                        case ToolError(var error, var kind) -> "ERROR [%s]: %s".formatted(kind, error);
                    };
                    conversationManager.add(new ToolResultMessage(toolCall.id(), toolCall.name(), resultText));
                }

                // Auto-commit if configured
                if (anyFileWritten && options.config().git().autoCommit()) {
                    autoCommit(options);
                }

                // Check for max tokens
                if (response.stopReason() == LLMResponse.StopReason.MAX_TOKENS) {
                    emitter.emit(new ErrorEvent("Response truncated — max tokens reached", null));
                }
            }

            var elapsed = Duration.between(startTime, Instant.now());
            var summary = turns >= options.config().agent().maxTurns()
                    ? "Max turns reached"
                    : "Completed";
            emitter.emit(new CompletionEvent(summary, turns, totalToolCalls, elapsed));

        } catch (Exception e) {
            emitter.emit(new ErrorEvent("Agent loop failed: " + e.getMessage(), e));
        }
    }

    private ToolResult executeToolCall(ToolCall toolCall, RunOptions options) {
        var toolOpt = toolRegistry.get(toolCall.name());
        if (toolOpt.isEmpty()) {
            var error = new ToolError("Unknown tool: " + toolCall.name(), ErrorKind.NOT_FOUND);
            emitter.emit(new ToolResultEvent(toolCall.id(), toolCall.name(), error));
            return error;
        }

        var tool = toolOpt.get();
        var inputDesc = summarizeInput(toolCall);

        // Check permissions
        boolean needsApproval = !permissionManager.check(toolCall.name(), tool.category(), inputDesc);
        emitter.emit(new ToolCallEvent(toolCall.id(), toolCall.name(), toolCall.input(), needsApproval));

        if (needsApproval) {
            var denied = new ToolError("Permission denied by user", ErrorKind.PERMISSION_DENIED);
            emitter.emit(new ToolResultEvent(toolCall.id(), toolCall.name(), denied));
            return denied;
        }

        // Execute on virtual thread
        var ctx = new ExecutionContext(options.projectRoot(), options.sessionId(), Map.of());
        try {
            var result = Thread.ofVirtual()
                    .name("tool-" + toolCall.name())
                    .start(() -> {})
                    .join();
            var toolResult = tool.execute(toolCall.input(), ctx);
            emitter.emit(new ToolResultEvent(toolCall.id(), toolCall.name(), toolResult));
            return toolResult;
        } catch (Exception e) {
            var error = new ToolError("Tool execution failed: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
            emitter.emit(new ToolResultEvent(toolCall.id(), toolCall.name(), error));
            return error;
        }
    }

    private void autoCommit(RunOptions options) {
        try {
            var gitTool = toolRegistry.get("git_operations");
            if (gitTool.isEmpty()) return;

            var ctx = new ExecutionContext(options.projectRoot(), options.sessionId(), Map.of());
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

            // git add .
            var addInput = mapper.createObjectNode();
            addInput.put("operation", "add");
            gitTool.get().execute(addInput, ctx);

            // git commit
            var commitInput = mapper.createObjectNode();
            commitInput.put("operation", "commit");
            commitInput.put("message", options.config().git().commitPrefix() + " auto-commit changes");
            var result = gitTool.get().execute(commitInput, ctx);

            if (result instanceof ToolSuccess(var output)) {
                log.info("Auto-committed: {}", output);
            }
        } catch (Exception e) {
            log.warn("Auto-commit failed: {}", e.getMessage());
        }
    }

    private boolean isFileWriteCall(ToolCall toolCall) {
        return "file_write".equals(toolCall.name());
    }

    private String summarizeInput(ToolCall toolCall) {
        var input = toolCall.input();
        return switch (toolCall.name()) {
            case "bash_exec" -> "command: " + input.path("command").asText("");
            case "file_read" -> "path: " + input.path("path").asText("");
            case "file_write" -> "path: " + input.path("path").asText("") + " mode: " + input.path("mode").asText("");
            case "file_search" -> "pattern: " + input.path("pattern").asText("");
            case "code_search" -> "query: " + input.path("query").asText("");
            case "git_operations" -> "operation: " + input.path("operation").asText("");
            case "list_directory" -> "path: " + input.path("path").asText(".");
            default -> toolCall.input().toString();
        };
    }

    /** Options for running the agent loop. */
    public record RunOptions(
            String prompt,
            JozConfig config,
            Path projectRoot,
            Path globalConfigDir,
            String sessionId,
            Optional<Skill> activeSkill) {}
}
