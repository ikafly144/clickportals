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
    private boolean lastLoadSuccessful = true;
    private boolean pendingLegacyMigration;

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
     * Loads the configuration from disk (creating the default file if missing), validates it,
     * synchronizes schema changes back to disk with a {@code .bak} backup when needed,
     * and logs warnings or errors for invalid settings.
     */
    public PluginConfig load() {
        return load(true);
    }

    /**
     * Reloads the configuration from disk without modifying or backing up existing files.
     */
    public PluginConfig reload() {
        return load(false);
    }

    /**
     * Loads the configuration from disk (creating the default file if missing when {@code syncSchema} is {@code true}),
     * optionally migrating legacy settings and synchronizing missing or obsolete schema keys back to disk.
     */
    public PluginConfig load(final boolean syncSchema) {
        try {
            final Path parent = configFile.getParent();
            if (!Files.exists(configFile)) {
                if (!syncSchema) {
                    logger.error("Configuration file {} does not exist; retaining active configuration.", configFile);
                    lastLoadSuccessful = false;
                    if (currentConfig == null) {
                        currentConfig = PluginConfig.defaultConfig();
                    }
                    return currentConfig;
                }
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                final PluginConfig defaultConfig = PluginConfig.defaultConfig();
                save(defaultConfig);
                currentConfig = defaultConfig;
                lastLoadSuccessful = true;
                pendingLegacyMigration = false;
                return currentConfig;
            }

            final CommentedConfigurationNode root = loader.load();
            boolean migrationSucceeded = true;
            if (syncSchema) {
                pendingLegacyMigration = false;
                if (root.hasChild("recipe") || root.node("hologram").hasChild("display-block")) {
                    logger.info("Notice: Legacy 'recipe' and/or 'hologram.display-block' settings in config.yml "
                            + "have moved to plugins/ClickPortals/aetheryte/default.yml in v0.3.");
                    if (parent != null) {
                        migrationSucceeded = net.sabafly.clickportals.variation.VariationLoader.migrateLegacyConfig(
                                root,
                                parent.resolve("aetheryte"),
                                logger
                        );
                    }
                    pendingLegacyMigration = !migrationSucceeded;
                }
            }
            final PluginConfig parsed = root.copy().get(PluginConfig.class);
            if (parsed == null) {
                logger.error("Failed to parse configuration from {}; file appears empty or malformed. Retaining active configuration.", configFile);
                lastLoadSuccessful = false;
                if (currentConfig == null) {
                    currentConfig = PluginConfig.defaultConfig();
                }
                return currentConfig;
            }
            final PluginConfig validated = parsed.validated(logger);
            if (syncSchema) {
                if (!migrationSucceeded) {
                    logger.warn("Skipping schema sync overwrite of {} because legacy migration failed; legacy settings are preserved.", configFile);
                } else {
                    final CommentedConfigurationNode targetRoot = loader.createNode();
                    targetRoot.set(PluginConfig.class, validated);
                    if (YamlSchemaSync.hasSchemaDiff(root, targetRoot) && YamlSchemaSync.backupFile(configFile, logger)) {
                        try {
                            writeNodeToDisk(targetRoot, validated);
                        } catch (final ConfigurateException e) {
                            logger.warn("Failed to write updated configuration to {}: {}", configFile, e.getMessage(), e);
                        }
                    }
                }
            }
            currentConfig = validated;
            lastLoadSuccessful = true;
            return currentConfig;
        } catch (final Exception e) {
            logger.error("Failed to load config from {}: syntax or deserialization error; retaining active configuration: {}",
                    configFile, e.getMessage(), e);
            lastLoadSuccessful = false;
            if (currentConfig == null) {
                currentConfig = PluginConfig.defaultConfig();
            }
            return currentConfig;
        }
    }

    /**
     * Returns whether the most recent {@link #load()} operation completed without syntax or deserialization errors.
     */
    public boolean lastLoadSuccessful() {
        return lastLoadSuccessful;
    }

    /**
     * Returns whether legacy config migration to {@code aetheryte/default.yml} failed during the last startup load
     * and remains pending for retry on the next startup.
     */
    public boolean pendingLegacyMigration() {
        return pendingLegacyMigration;
    }

    /**
     * Saves the given {@link PluginConfig} to disk while preserving {@code @Comment} metadata and headers.
     */
    public void save(final PluginConfig config) throws ConfigurateException {
        final PluginConfig validated = config.validated(logger);
        final CommentedConfigurationNode root = loader.createNode();
        root.set(PluginConfig.class, validated);
        writeNodeToDisk(root, validated);
    }

    private void writeNodeToDisk(
            final CommentedConfigurationNode root,
            final PluginConfig validated
    ) throws ConfigurateException {
        final StringWriter stringWriter = new StringWriter();
        final YamlConfigurationLoader stringLoader = YamlConfigurationLoader.builder()
                .sink(() -> new java.io.BufferedWriter(stringWriter))
                .indent(INDENT_SPACES)
                .nodeStyle(NodeStyle.BLOCK)
                .defaultOptions(opts -> opts.header(HEADER).shouldCopyDefaults(true))
                .build();
        stringLoader.save(root);

        final String commentedYaml = YamlSchemaSync.renderInlineComments(stringWriter.toString(), root, INDENT_SPACES);
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
}
