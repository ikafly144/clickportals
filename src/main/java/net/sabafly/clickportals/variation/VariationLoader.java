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
package net.sabafly.clickportals.variation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.variation.AetheryteVariation.HitboxSection;
import net.sabafly.clickportals.variation.AetheryteVariation.ItemSection;
import net.sabafly.clickportals.variation.AetheryteVariation.LimitSection;
import net.sabafly.clickportals.variation.AetheryteVariation.ModelSection;
import net.sabafly.clickportals.variation.AetheryteVariation.PlacementSection;
import net.sabafly.clickportals.variation.AetheryteVariation.RecipeSection;
import net.sabafly.clickportals.variation.AetheryteVariation.TeleportSection;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Loads Aetheryte variation YAML files from {@code <dataDir>/aetheryte/} (#37).
 */
public final class VariationLoader {

    private static final String DEFAULT_RESOURCE_PATH = "/aetheryte/default.yml";
    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-z0-9_-]+$");

    private VariationLoader() {
    }

    /**
     * Checks whether the given string is a valid variation identifier ({@code [a-z0-9_-]+}).
     */
    public static boolean isValidVariationId(final String id) {
        return VALID_ID_PATTERN.matcher(id).matches();
    }

    /**
     * Ensures {@code default.yml} exists in {@code aetheryteDir} and loads all {@code *.yml} variation definitions.
     * Always guarantees that {@link Aetheryte#DEFAULT_VARIATION} is present in the returned map.
     */
    public static Map<String, AetheryteVariation> loadDirectory(final Path aetheryteDir, final Logger logger) {
        final Map<String, AetheryteVariation> loaded = new LinkedHashMap<>();

        try {
            if (!Files.exists(aetheryteDir)) {
                Files.createDirectories(aetheryteDir);
            }
            final Path defaultFile = aetheryteDir.resolve(Aetheryte.DEFAULT_VARIATION + ".yml");
            if (!Files.exists(defaultFile)) {
                copyDefaultResource(defaultFile, logger);
            }

            try (final Stream<Path> stream = Files.list(aetheryteDir)) {
                final List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            final String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return lower.endsWith(".yml") || lower.endsWith(".yaml");
                        })
                        .sorted()
                        .toList();

                for (final Path file : files) {
                    final String fileName = file.getFileName().toString();
                    final int dotIndex = fileName.lastIndexOf('.');
                    final String rawId = (dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName)
                            .toLowerCase(Locale.ROOT);
                    if (!isValidVariationId(rawId)) {
                        logger.warn("Ignoring variation file '{}' with invalid identifier '{}' (allowed: [a-z0-9_-]+)",
                                fileName, rawId);
                        continue;
                    }

                    try {
                        final CommentedConfigurationNode root = YamlConfigurationLoader.builder()
                                .path(file)
                                .indent(2)
                                .nodeStyle(NodeStyle.BLOCK)
                                .build()
                                .load();
                        final AetheryteVariation parsed = root.get(AetheryteVariation.class);
                        final AetheryteVariation validated = (parsed == null
                                ? AetheryteVariation.defaultVariation()
                                : parsed).validated(rawId, logger);
                        loaded.put(rawId, validated);
                    } catch (final IOException e) {
                        logger.warn("Failed to load variation file {}; skipping", file, e);
                    }
                }
            }
        } catch (final IOException e) {
            logger.warn("Failed to access variation directory {}; using built-in default variation", aetheryteDir, e);
        }

        loaded.putIfAbsent(Aetheryte.DEFAULT_VARIATION, AetheryteVariation.defaultVariation());
        return Map.copyOf(loaded);
    }

    /**
     * Migrates legacy v0.2 {@code recipe} and {@code hologram.display-block} settings from {@code config.yml}'s root node
     * to {@code <dataDir>/aetheryte/default.yml} if {@code default.yml} does not exist yet (#37).
     */
    public static void migrateLegacyConfig(
            final CommentedConfigurationNode configRoot,
            final Path aetheryteDir,
            final Logger logger
    ) {
        final Path defaultFile = aetheryteDir.resolve(Aetheryte.DEFAULT_VARIATION + ".yml");
        if (Files.exists(defaultFile)) {
            return;
        }
        final boolean hasLegacyRecipe = configRoot.hasChild("recipe");
        final boolean hasLegacyDisplay = configRoot.node("hologram").hasChild("display-block");
        if (!hasLegacyRecipe && !hasLegacyDisplay) {
            return;
        }

        try {
            if (!Files.exists(aetheryteDir)) {
                Files.createDirectories(aetheryteDir);
            }
            final RecipeSection recipe;
            if (hasLegacyRecipe) {
                final boolean enabled = configRoot.node("recipe", "enabled").getBoolean(true);
                final List<String> shape = configRoot.node("recipe", "shape")
                        .getList(String.class, List.of("SSS", "SAS", "SSS"));
                final Map<String, String> ingredients = new LinkedHashMap<>();
                for (final Map.Entry<Object, ? extends CommentedConfigurationNode> entry
                        : configRoot.node("recipe", "ingredients").childrenMap().entrySet()) {
                    final String val = entry.getValue().getString();
                    if (val != null && !val.isBlank()) {
                        ingredients.put(String.valueOf(entry.getKey()), val);
                    }
                }
                recipe = new RecipeSection(
                        enabled,
                        shape,
                        ingredients.isEmpty() ? RecipeSection.defaults().ingredients() : Map.copyOf(ingredients)
                );
            } else {
                recipe = RecipeSection.defaults();
            }

            final ModelSection model;
            if (hasLegacyDisplay) {
                final String block = configRoot.node("hologram", "display-block").getString("minecraft:lodestone");
                model = new ModelSection(block, List.of(1.0, 1.0, 1.0), List.of(0.0, 0.0, 0.0), 1.35);
            } else {
                model = ModelSection.defaults();
            }

            final AetheryteVariation migrated = new AetheryteVariation(
                    "<aqua>Aetheryte</aqua>",
                    ItemSection.defaults(),
                    recipe,
                    model,
                    HitboxSection.defaults(),
                    TeleportSection.defaults(),
                    PlacementSection.defaults(),
                    LimitSection.defaults()
            );

            final YamlConfigurationLoader defaultLoader = YamlConfigurationLoader.builder()
                    .path(defaultFile)
                    .indent(2)
                    .nodeStyle(NodeStyle.BLOCK)
                    .build();
            final CommentedConfigurationNode defaultRoot = defaultLoader.createNode();
            defaultRoot.set(AetheryteVariation.class, migrated);
            defaultLoader.save(defaultRoot);
            logger.info("Migrated legacy recipe and hologram display-block from config.yml into {}", defaultFile);
        } catch (final Exception e) {
            logger.warn("Failed to migrate legacy settings from config.yml to {}; copying default template", defaultFile, e);
            copyDefaultResource(defaultFile, logger);
        }
    }

    private static void copyDefaultResource(final Path targetFile, final Logger logger) {
        try (final InputStream in = VariationLoader.class.getResourceAsStream(DEFAULT_RESOURCE_PATH)) {
            if (in != null) {
                Files.copy(in, targetFile);
                return;
            }
        } catch (final IOException e) {
            logger.warn("Failed to copy bundled {} to {}; generating from model", DEFAULT_RESOURCE_PATH, targetFile, e);
        }

        try {
            final YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(targetFile)
                    .indent(2)
                    .nodeStyle(NodeStyle.BLOCK)
                    .build();
            final CommentedConfigurationNode root = loader.createNode();
            root.set(AetheryteVariation.class, AetheryteVariation.defaultVariation());
            loader.save(root);
        } catch (final IOException e) {
            logger.warn("Failed to write fallback default variation file to {}", targetFile, e);
        }
    }
}
