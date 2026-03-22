package com.joz.common.config;

/** Permission configuration for tool categories. */
public record PermissionConfig(
        ApprovalMode fileRead,
        ApprovalMode listDirectory,
        ApprovalMode codeSearch,
        ApprovalMode fileSearch,
        ApprovalMode fileWrite,
        ApprovalMode bashExec,
        ApprovalMode gitOps) {

    /** Default permissions — reads auto-approve, writes/exec ask first. */
    public static PermissionConfig defaults() {
        return new PermissionConfig(
                ApprovalMode.AUTO_APPROVE,
                ApprovalMode.AUTO_APPROVE,
                ApprovalMode.AUTO_APPROVE,
                ApprovalMode.AUTO_APPROVE,
                ApprovalMode.ASK_FIRST,
                ApprovalMode.ASK_FIRST,
                ApprovalMode.ASK_FIRST);
    }
}
