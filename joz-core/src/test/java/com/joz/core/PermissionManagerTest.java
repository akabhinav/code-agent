package com.joz.core;

import com.joz.common.config.ApprovalMode;
import com.joz.common.config.PermissionConfig;
import com.joz.common.model.ToolCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermissionManagerTest {

    @Test
    void autoApproveReadsWithoutPrompt() {
        var mgr = new PermissionManager();
        mgr.configure(PermissionConfig.defaults());

        assertTrue(mgr.check("file_read", ToolCategory.READ, "test"));
    }

    @Test
    void denyBlocksExecution() {
        var mgr = new PermissionManager();
        mgr.configure(new PermissionConfig(
                ApprovalMode.AUTO_APPROVE,
                ApprovalMode.AUTO_APPROVE,
                ApprovalMode.AUTO_APPROVE,
                ApprovalMode.AUTO_APPROVE,
                ApprovalMode.DENY,
                ApprovalMode.DENY,
                ApprovalMode.DENY));

        assertFalse(mgr.check("bash_exec", ToolCategory.EXECUTE, "test"));
    }

    @Test
    void autoApproveAllOverridesEverything() {
        var mgr = new PermissionManager();
        mgr.configure(new PermissionConfig(
                ApprovalMode.DENY, ApprovalMode.DENY, ApprovalMode.DENY,
                ApprovalMode.DENY, ApprovalMode.DENY, ApprovalMode.DENY, ApprovalMode.DENY));
        mgr.setAutoApproveAll(true);

        assertTrue(mgr.check("bash_exec", ToolCategory.EXECUTE, "test"));
    }

    @Test
    void askFirstPromptsUser() {
        var mgr = new PermissionManager();
        mgr.configure(PermissionConfig.defaults());
        mgr.setApprovalPrompt(x -> true); // auto-approve in test

        assertTrue(mgr.check("bash_exec", ToolCategory.EXECUTE, "echo hello"));
    }
}
