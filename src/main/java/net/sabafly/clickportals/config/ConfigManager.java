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
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Manages loading, validating, and saving {@link PluginConfig} using SpongePowered Configurate YAML (#33).
 */
public final class ConfigManager {

    private static final String HEADER = "ClickPortals Configuration";
    private static final int INDENT_SPACES = 2;

    private final Path configFile;
    private final Logger logger;
    private final YamlConfigurationLoader loader;
    private PluginConfig currentConfig;

    /**
     * Creates a new {@link ConfigManager} for the given configuration file path.
     */
    public ConfigManager(final Path configFile, final Logger logger) {
        this.configFile = configFile;
        this.logger = logger;
        this.loader = YamlConfigurationLoader.builder()
                .path(configFile)
                .indent(INDENT_SPACES)
                .nodeStyle(NodeStyle.BLOCK)
                .defaultOptions(opts -> opts.header(HEADER).shouldCopyDefaults(true))
                .build();
        this.currentConfig = PluginConfig.defaultConfig();
    }

    /**
     * Loads the configuration from disk (creating the default file if missing), validates it, and saves back
     * with comments preserved.
     */
    public PluginConfig load() {
        try {
            final Path parent = configFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(configFile)) {
                currentConfig = PluginConfig.defaultConfig();
                save(currentConfig);
                return currentConfig;
            }

            final CommentedConfigurationNode root = loader.load();
            if (root.hasChild("recipe") || root.node("hologram").hasChild("display-block")) {
                logger.info("Notice: Legacy 'recipe' and/or 'hologram.display-block' settings in config.yml "
                        + "have moved to plugins/ClickPortals/aetheryte/default.yml in v0.3.");
                if (parent != null) {
                    net.sabafly.clickportals.variation.VariationLoader.migrateLegacyConfig(
                            root,
                            parent.resolve("aetheryte"),
                            logger
                    );
                }
            }
            final PluginConfig parsed = root.get(PluginConfig.class);
            final PluginConfig validated = (parsed == null ? PluginConfig.defaultConfig() : parsed).validated(logger);
            currentConfig = validated;
            save(validated);
            return currentConfig;
        } catch (final IOException e) {
            logger.warn("Failed to load config from {}; using default configuration", configFile, e);
            currentConfig = PluginConfig.defaultConfig();
            return currentConfig;
        }
    }

    /**
     * Saves the given {@link PluginConfig} to disk while preserving {@code @Comment} metadata and headers.
     */
    public void save(final PluginConfig config) throws ConfigurateException {
        final PluginConfig validated = config.validated(logger);
        final CommentedConfigurationNode root = loader.createNode();
        root.set(PluginConfig.class, validated);

        final StringWriter stringWriter = new StringWriter();
        final YamlConfigurationLoader stringLoader = YamlConfigurationLoader.builder()
                .sink(() -> new java.io.BufferedWriter(stringWriter))
                .indent(INDENT_SPACES)
                .nodeStyle(NodeStyle.BLOCK)
                .defaultOptions(opts -> opts.header(HEADER).shouldCopyDefaults(true))
                .build();
        stringLoader.save(root);

        final String commentedYaml = renderInlineComments(stringWriter.toString(), root);
        try {
            final Path parent = configFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(configFile, commentedYaml, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new ConfigurateException(root, e);
        }
        this.currentConfig = validated;
    }

    /**
     * Returns a newly populated {@link CommentedConfigurationNode} for the current configuration,
     * allowing callers to inspect {@code @Comment} metadata directly on nodes.
     */
    public CommentedConfigurationNode toCommentedNode() throws ConfigurateException {
        final CommentedConfigurationNode root = loader.createNode();
        root.set(PluginConfig.class, currentConfig);
        return root;
    }

    /**
     * Returns the most recently loaded {@link PluginConfig}.
     */
    public PluginConfig config() {
        return currentConfig;
    }

    /**
     * Injects {@code @Comment} lines from the {@link CommentedConfigurationNode} tree into the dumped YAML text.
     */
    private static String renderInlineComments(final String dumpedYaml, final CommentedConfigurationNode root) {
        final List<String> outputLines = new ArrayList<>();
        final String[] lines = dumpedYaml.split("\r?\n", -1);
        final List<CommentedConfigurationNode> nodeStack = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            final String trimmed = line.stripLeading();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("-")) {
                final int colonIndex = trimmed.indexOf(':');
                if (colonIndex > 0) {
                    final int indent = line.length() - trimmed.length();
                    final int depth = indent / INDENT_SPACES;
                    final String key = trimmed.substring(0, colonIndex).strip();

                    while (nodeStack.size() > depth) {
                        nodeStack.removeLast();
                    }
                    final CommentedConfigurationNode parentNode = nodeStack.isEmpty() ? root : nodeStack.getLast();
                    final Map<Object, ? extends CommentedConfigurationNode> children = parentNode.childrenMap();
                    final CommentedConfigurationNode currentNode = children.get(key);
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
}
