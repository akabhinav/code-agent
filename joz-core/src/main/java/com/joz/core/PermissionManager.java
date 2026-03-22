package com.joz.core;

import com.joz.common.config.ApprovalMode;
import com.joz.common.config.PermissionConfig;
import com.joz.common.model.ToolCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Manages tool execution permissions — ask, auto-approve, or deny. */
@Component
public class PermissionManager {

    private static final Logger log = LoggerFactory.getLogger(PermissionManager.class);

    private PermissionConfig config;
    private final Set<String> approvedTools = ConcurrentHashMap.newKeySet();
    private final Map<String, ApprovalMode> toolOverrides = new ConcurrentHashMap<>();
    private Function<String, Boolean> approvalPrompt;
    private boolean autoApproveAll;

    public PermissionManager() {
        this.config = PermissionConfig.defaults();
        this.approvalPrompt = x -> false;
    }

    /** Sets the permission config. */
    public void configure(PermissionConfig config) {
        this.config = config;
    }

    /** Sets the callback used to prompt the user for approval. */
    public void setApprovalPrompt(Function<String, Boolean> prompt) {
        this.approvalPrompt = prompt;
    }

    /** Enables auto-approve for all tools (--auto-approve flag). */
    public void setAutoApproveAll(boolean auto) {
        this.autoApproveAll = auto;
    }

    /** Checks if a tool call is permitted. Returns true if approved. */
    public boolean check(String toolName, ToolCategory category, String description) {
        if (autoApproveAll) return true;

        // Check tool-specific overrides
        if (toolOverrides.containsKey(toolName)) {
            return switch (toolOverrides.get(toolName)) {
                case AUTO_APPROVE -> true;
                case DENY -> false;
                case ASK_FIRST, ASK_ALWAYS -> promptUser(toolName, description);
            };
        }

        var mode = getModeForCategory(category);
        return switch (mode) {
            case AUTO_APPROVE -> true;
            case DENY -> {
                log.info("Tool {} denied by policy", toolName);
                yield false;
            }
            case ASK_FIRST -> {
                if (approvedTools.contains(toolName)) {
                    yield true;
                }
                boolean approved = promptUser(toolName, description);
                if (approved) approvedTools.add(toolName);
                yield approved;
            }
            case ASK_ALWAYS -> promptUser(toolName, description);
        };
    }

    /** Records a user's permanent decision for a tool (always/never). */
    public void setToolOverride(String toolName, ApprovalMode mode) {
        toolOverrides.put(toolName, mode);
    }

    private ApprovalMode getModeForCategory(ToolCategory category) {
        return switch (category) {
            case READ -> config.fileRead();
            case WRITE -> config.fileWrite();
            case EXECUTE -> config.bashExec();
            case GIT -> config.gitOps();
        };
    }

    private boolean promptUser(String toolName, String description) {
        return approvalPrompt.apply(
                "Tool: %s\n%s".formatted(toolName, description));
    }
}
