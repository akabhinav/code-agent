package com.joz.context.codebase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Builds a project summary: language detection, file tree, key files. */
@Component
public class CodebaseIndexer {

    private static final Logger log = LoggerFactory.getLogger(CodebaseIndexer.class);
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "__pycache__", ".joz",
            ".gradle", ".idea", ".vscode", "dist", ".next", "vendor");
    private static final int MAX_TREE_DEPTH = 3;
    private static final int MAX_TREE_ENTRIES = 100;

    /** Builds a summary of the project for the system prompt. */
    public String buildSummary(Path projectRoot) {
        var sb = new StringBuilder();
        sb.append("Project root: ").append(projectRoot).append("\n");

        // Detect project type
        var projectType = detectProjectType(projectRoot);
        if (!projectType.isEmpty()) {
            sb.append("Project type: ").append(String.join(", ", projectType.values())).append("\n");
        }

        // File tree
        sb.append("\nFile tree:\n");
        sb.append(buildTree(projectRoot));

        return sb.toString();
    }

    private Map<String, String> detectProjectType(Path root) {
        var types = new LinkedHashMap<String, String>();
        if (Files.exists(root.resolve("pom.xml"))) types.put("build", "Maven (Java)");
        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts")))
            types.put("build", "Gradle (Java/Kotlin)");
        if (Files.exists(root.resolve("package.json"))) types.put("runtime", "Node.js");
        if (Files.exists(root.resolve("requirements.txt")) || Files.exists(root.resolve("pyproject.toml")))
            types.put("runtime", "Python");
        if (Files.exists(root.resolve("go.mod"))) types.put("runtime", "Go");
        if (Files.exists(root.resolve("Cargo.toml"))) types.put("runtime", "Rust");
        if (Files.exists(root.resolve("Gemfile"))) types.put("runtime", "Ruby");
        return types;
    }

    private String buildTree(Path root) {
        var sb = new StringBuilder();
        int[] count = {0};
        try {
            appendTree(root, root, "", 0, sb, count);
        } catch (IOException e) {
            log.warn("Failed to build file tree: {}", e.getMessage());
            sb.append("[error reading directory]\n");
        }
        return sb.toString();
    }

    private void appendTree(Path root, Path dir, String indent, int depth,
                            StringBuilder sb, int[] count) throws IOException {
        if (depth > MAX_TREE_DEPTH || count[0] > MAX_TREE_ENTRIES) return;

        try (Stream<Path> entries = Files.list(dir).sorted()) {
            for (var entry : entries.toList()) {
                if (count[0] >= MAX_TREE_ENTRIES) {
                    sb.append(indent).append("... (truncated)\n");
                    return;
                }

                var name = entry.getFileName().toString();
                if (SKIP_DIRS.contains(name)) continue;

                count[0]++;
                if (Files.isDirectory(entry)) {
                    sb.append(indent).append(name).append("/\n");
                    appendTree(root, entry, indent + "  ", depth + 1, sb, count);
                } else {
                    sb.append(indent).append(name).append("\n");
                }
            }
        }
    }
}
