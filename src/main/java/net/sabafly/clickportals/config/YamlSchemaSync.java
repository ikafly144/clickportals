/*
 * ClickPortals
 * Copyright (C) 2026 ikafly144
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.sabafly.clickportals.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Shared helpers for startup YAML schema synchronization, backup creation, and {@code @Comment} rendering (#60).
 */
public final class YamlSchemaSync {

    public static final int DEFAULT_INDENT_SPACES = 2;

    private YamlSchemaSync() {
    }

    /**
     * Copies {@code targetFile} to {@code <filename>.bak} in the same directory before modifying it.
     *
     * @return {@code true} if the backup succeeded, {@code false} if an error occurred (caller should abort overwrite)
     */
    public static boolean backupFile(final Path targetFile, final Logger logger) {
        final Path fileName = targetFile.getFileName();
        if (fileName == null) {
            logger.warn("Cannot create backup for path without filename: {}", targetFile);
            return false;
        }
        final Path backupFile = targetFile.resolveSibling(fileName + ".bak");
        try {
            Files.copy(targetFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Created backup {} before updating {} to latest schema", backupFile, targetFile);
            return true;
        } catch (final IOException e) {
            logger.warn("Failed to create backup {} for {}; skipping automatic schema update", backupFile, targetFile, e);
            return false;
        }
    }

    /**
     * Checks whether {@code existing} differs structurally from {@code target} (missing keys, extra/unknown keys,
     * list size changes, or map/list/scalar type mismatches). Scalar value differences are preserved and ignored.
     */
    public static boolean hasSchemaDiff(final ConfigurationNode existing, final ConfigurationNode target) {
        if (existing.virtual() != target.virtual()) {
            return true;
        }
        if (existing.virtual()) {
            return false;
        }
        if (existing.isMap() && target.isMap()) {
            final Map<Object, ? extends ConfigurationNode> existingMap = existing.childrenMap();
            final Map<Object, ? extends ConfigurationNode> targetMap = target.childrenMap();
            if (!existingMap.keySet().equals(targetMap.keySet())) {
                return true;
            }
            for (final Map.Entry<Object, ? extends ConfigurationNode> entry : targetMap.entrySet()) {
                final ConfigurationNode existingChild = existingMap.get(entry.getKey());
                if (existingChild == null || hasSchemaDiff(existingChild, entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (existing.isList() && target.isList()) {
            final List<? extends ConfigurationNode> existingList = existing.childrenList();
            final List<? extends ConfigurationNode> targetList = target.childrenList();
            if (existingList.size() != targetList.size()) {
                return true;
            }
            for (int i = 0; i < targetList.size(); i++) {
                if (hasSchemaDiff(existingList.get(i), targetList.get(i))) {
                    return true;
                }
            }
            return false;
        }
        return existing.isMap() != target.isMap() || existing.isList() != target.isList();
    }

    /**
     * Injects {@code @Comment} lines from the {@link CommentedConfigurationNode} tree into the dumped YAML text.
     */
    public static String renderInlineComments(
            final String dumpedYaml,
            final CommentedConfigurationNode root,
            final int indentSpaces
    ) {
        final List<String> outputLines = new ArrayList<>();
        final String[] lines = dumpedYaml.split("\r?\n", -1);
        final List<CommentedConfigurationNode> nodeStack = new ArrayList<>();

        for (final String line : lines) {
            final String trimmed = line.stripLeading();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("-")) {
                final String key = extractYamlKey(trimmed);
                if (key != null) {
                    final int indent = line.length() - trimmed.length();
                    final int depth = indent / indentSpaces;

                    while (nodeStack.size() > depth) {
                        nodeStack.removeLast();
                    }
                    final CommentedConfigurationNode parentNode = nodeStack.isEmpty() ? root : nodeStack.getLast();
                    final CommentedConfigurationNode currentNode = findChildNode(parentNode, key);
                    if (currentNode != null) {
                        final String comment = currentNode.comment();
                        if (comment != null && !comment.isBlank()) {
                            final String indentPrefix = " ".repeat(indent);
                            if (depth == 0 && !outputLines.isEmpty() && !outputLines.getLast().isBlank()) {
                                outputLines.add("");
                            }
                            for (final String commentLine : comment.split("\r?\n", -1)) {
                                outputLines.add(indentPrefix + "# " + commentLine);
                            }
                        }
                        if (nodeStack.size() == depth) {
                            nodeStack.add(currentNode);
                        }
                    }
                }
            }
            outputLines.add(line);
        }
        return String.join("\n", outputLines);
    }

    /**
     * Inserts a blank line before top-level YAML sections (when not immediately preceded by a blank line or comment)
     * to keep generated translation files readable.
     */
    public static String addTopLevelSectionSpacing(final String dumpedYaml) {
        final String[] lines = dumpedYaml.split("\r?\n", -1);
        final List<String> output = new ArrayList<>(lines.length + 16);
        for (final String line : lines) {
            if (!line.isEmpty()
                    && !Character.isWhitespace(line.charAt(0))
                    && !line.startsWith("#")
                    && !line.startsWith("-")
                    && !output.isEmpty()) {
                final String prev = output.getLast();
                if (!prev.isBlank() && !prev.startsWith("#")) {
                    output.add("");
                }
            }
            output.add(line);
        }
        return String.join("\n", output);
    }

    private static @Nullable CommentedConfigurationNode findChildNode(
            final CommentedConfigurationNode parentNode,
            final String key
    ) {
        final Map<Object, ? extends CommentedConfigurationNode> children = parentNode.childrenMap();
        final CommentedConfigurationNode direct = children.get(key);
        if (direct != null) {
            return direct;
        }
        for (final Map.Entry<Object, ? extends CommentedConfigurationNode> entry : children.entrySet()) {
            if (key.equals(String.valueOf(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static @Nullable String extractYamlKey(final String trimmedLine) {
        if (trimmedLine.isEmpty()) {
            return null;
        }
        final char first = trimmedLine.charAt(0);
        if (first == '\'' || first == '"') {
            int idx = 1;
            while (idx < trimmedLine.length()) {
                final char ch = trimmedLine.charAt(idx);
                if (first == '"' && ch == '\\' && idx + 1 < trimmedLine.length()) {
                    idx += 2;
                    continue;
                }
                if (first == '\'' && ch == '\'' && idx + 1 < trimmedLine.length() && trimmedLine.charAt(idx + 1) == '\'') {
                    idx += 2;
                    continue;
                }
                if (ch == first) {
                    if (idx + 1 < trimmedLine.length() && trimmedLine.charAt(idx + 1) == ':' && isColonSeparator(trimmedLine, idx + 1)) {
                        return trimmedLine.substring(1, idx);
                    }
                    return null;
                }
                idx++;
            }
            return null;
        }
        final int colonIndex = trimmedLine.indexOf(':');
        if (colonIndex > 0 && isColonSeparator(trimmedLine, colonIndex)) {
            return trimmedLine.substring(0, colonIndex).strip();
        }
        return null;
    }

    private static boolean isColonSeparator(final String line, final int colonIndex) {
        return colonIndex == line.length() - 1 || Character.isWhitespace(line.charAt(colonIndex + 1));
    }
}
