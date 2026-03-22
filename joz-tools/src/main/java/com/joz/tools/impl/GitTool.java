package com.joz.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.joz.common.model.ToolCategory;
import com.joz.common.model.ToolResult;
import com.joz.common.model.ToolResult.ErrorKind;
import com.joz.common.model.ToolResult.ToolError;
import com.joz.common.model.ToolResult.ToolSuccess;
import com.joz.common.schema.ToolSchemaBuilder;
import com.joz.common.schema.ToolSchemaBuilder.Property;
import com.joz.tools.ExecutionContext;
import com.joz.tools.Tool;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** Perform git operations on the repository using JGit. */
@Component
public class GitTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(GitTool.class);
    private static final int MAX_DIFF_SIZE = 50 * 1024;

    @Override
    public String name() {
        return "git_operations";
    }

    @Override
    public String description() {
        return "Perform git operations: status, diff, log, add, commit, branch, checkout.";
    }

    @Override
    public JsonNode inputSchema() {
        return ToolSchemaBuilder.objectSchema(
                List.of(
                        Property.ofEnum("operation", "Git operation",
                                List.of("status", "diff", "log", "add", "commit", "branch", "checkout")),
                        Property.of("message", "string", "Commit message (for commit)"),
                        Property.of("paths", "string", "Comma-separated paths (for add)"),
                        Property.of("name", "string", "Branch name (for branch/checkout)"),
                        Property.of("create", "string", "Set to 'true' to create new branch"),
                        Property.of("count", "integer", "Number of log entries"),
                        Property.of("staged", "string", "Set to 'true' for staged diff")),
                List.of("operation"));
    }

    @Override
    public ToolResult execute(JsonNode input, ExecutionContext ctx) {
        var operation = input.get("operation").asText();

        try (var repo = openRepo(ctx.projectRoot()); var git = new Git(repo)) {
            return switch (operation) {
                case "status" -> gitStatus(git);
                case "diff" -> gitDiff(git, repo, input);
                case "log" -> gitLog(git, input);
                case "add" -> gitAdd(git, input);
                case "commit" -> gitCommit(git, input);
                case "branch" -> gitBranch(git, input);
                case "checkout" -> gitCheckout(git, input);
                default -> new ToolError("Unknown operation: " + operation, ErrorKind.INVALID_INPUT);
            };
        } catch (IOException e) {
            return new ToolError("Git error: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }

    @Override
    public ToolCategory category() {
        return ToolCategory.GIT;
    }

    private Repository openRepo(java.nio.file.Path root) throws IOException {
        return new FileRepositoryBuilder()
                .setGitDir(root.resolve(".git").toFile())
                .readEnvironment()
                .findGitDir()
                .build();
    }

    private ToolResult gitStatus(Git git) {
        try {
            var status = git.status().call();
            var sb = new StringBuilder();

            if (!status.getModified().isEmpty()) {
                sb.append("Modified:\n");
                status.getModified().forEach(f -> sb.append("  ").append(f).append("\n"));
            }
            if (!status.getAdded().isEmpty()) {
                sb.append("Staged (added):\n");
                status.getAdded().forEach(f -> sb.append("  ").append(f).append("\n"));
            }
            if (!status.getChanged().isEmpty()) {
                sb.append("Staged (changed):\n");
                status.getChanged().forEach(f -> sb.append("  ").append(f).append("\n"));
            }
            if (!status.getRemoved().isEmpty()) {
                sb.append("Staged (removed):\n");
                status.getRemoved().forEach(f -> sb.append("  ").append(f).append("\n"));
            }
            if (!status.getUntracked().isEmpty()) {
                sb.append("Untracked:\n");
                status.getUntracked().forEach(f -> sb.append("  ").append(f).append("\n"));
            }

            return new ToolSuccess(sb.isEmpty() ? "Working tree clean" : sb.toString().strip());
        } catch (GitAPIException e) {
            return new ToolError("Git status failed: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }

    private ToolResult gitDiff(Git git, Repository repo, JsonNode input) {
        try {
            var baos = new ByteArrayOutputStream();
            var df = new DiffFormatter(baos);
            df.setRepository(repo);

            var headId = repo.resolve("HEAD");
            if (headId == null) {
                return new ToolSuccess("No commits yet.");
            }

            try (var revWalk = new RevWalk(repo)) {
                var commit = revWalk.parseCommit(headId);
                var tree = commit.getTree();

                var diffs = git.diff().setOldTree(null).call();
                if (diffs.isEmpty()) {
                    return new ToolSuccess("No changes.");
                }

                for (var diff : diffs) {
                    df.format(diff);
                }
                df.flush();
            }

            var output = baos.toString();
            if (output.length() > MAX_DIFF_SIZE) {
                output = output.substring(0, MAX_DIFF_SIZE) + "\n[truncated — diff too large]";
            }
            return new ToolSuccess(output.isBlank() ? "No changes." : output);
        } catch (Exception e) {
            return new ToolError("Git diff failed: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }

    private ToolResult gitLog(Git git, JsonNode input) {
        try {
            var count = input.has("count") ? input.get("count").asInt() : 10;
            var logCmd = git.log().setMaxCount(count);
            var commits = logCmd.call();

            var sb = new StringBuilder();
            for (var commit : commits) {
                sb.append("%s %s (%s)%n".formatted(
                        commit.abbreviate(7).name(),
                        commit.getShortMessage(),
                        commit.getAuthorIdent().getName()));
            }
            return new ToolSuccess(sb.isEmpty() ? "No commits." : sb.toString().strip());
        } catch (Exception e) {
            return new ToolError("Git log failed: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }

    private ToolResult gitAdd(Git git, JsonNode input) {
        try {
            if (!input.has("paths")) {
                git.add().addFilepattern(".").call();
                return new ToolSuccess("Added all changes to staging area.");
            }
            var paths = input.get("paths").asText().split(",");
            var addCmd = git.add();
            for (var path : paths) {
                addCmd.addFilepattern(path.strip());
            }
            addCmd.call();
            return new ToolSuccess("Added %d path(s) to staging area.".formatted(paths.length));
        } catch (GitAPIException e) {
            return new ToolError("Git add failed: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }

    private ToolResult gitCommit(Git git, JsonNode input) {
        if (!input.has("message")) {
            return new ToolError("'message' is required for commit", ErrorKind.INVALID_INPUT);
        }
        try {
            var result = git.commit()
                    .setMessage(input.get("message").asText())
                    .call();
            return new ToolSuccess("Committed: %s".formatted(result.abbreviate(7).name()));
        } catch (GitAPIException e) {
            return new ToolError("Git commit failed: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }

    private ToolResult gitBranch(Git git, JsonNode input) {
        if (!input.has("name")) {
            return new ToolError("'name' is required for branch", ErrorKind.INVALID_INPUT);
        }
        try {
            var create = input.has("create") && "true".equals(input.get("create").asText());
            if (create) {
                git.branchCreate().setName(input.get("name").asText()).call();
                return new ToolSuccess("Created branch: " + input.get("name").asText());
            }
            var branches = git.branchList().call();
            var sb = new StringBuilder();
            for (var branch : branches) {
                sb.append(branch.getName()).append("\n");
            }
            return new ToolSuccess(sb.toString().strip());
        } catch (GitAPIException e) {
            return new ToolError("Git branch failed: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }

    private ToolResult gitCheckout(Git git, JsonNode input) {
        if (!input.has("name")) {
            return new ToolError("'name' is required for checkout", ErrorKind.INVALID_INPUT);
        }
        try {
            git.checkout().setName(input.get("name").asText()).call();
            return new ToolSuccess("Checked out: " + input.get("name").asText());
        } catch (GitAPIException e) {
            return new ToolError("Git checkout failed: " + e.getMessage(), ErrorKind.EXECUTION_FAILED);
        }
    }
}
