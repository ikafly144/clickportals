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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.config.YamlSchemaSync;
import net.sabafly.clickportals.variation.AetheryteVariation.HitboxSection;
import net.sabafly.clickportals.variation.AetheryteVariation.ItemSection;
import net.sabafly.clickportals.variation.AetheryteVariation.LimitSection;
import net.sabafly.clickportals.variation.AetheryteVariation.ModelSection;
import net.sabafly.clickportals.variation.AetheryteVariation.PlacementSection;
import net.sabafly.clickportals.variation.AetheryteVariation.RecipeSection;
import net.sabafly.clickportals.variation.AetheryteVariation.TeleportSection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Loads Aetheryte variation YAML files from {@code <dataDir>/aetheryte/} (#37).
 */
public final class VariationLoader {

    private static final String DEFAULT_RESOURCE_PATH = "/aetheryte/default.yml";
    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-z0-9_-]+$");
    private static final String NULL_SENTINEL = "__CLICKPORTALS_NULL__";

    private VariationLoader() {
    }

    /**
     * Checks whether the given string is a valid variation identifier ({@code [a-z0-9_-]+}).
     */
    public static boolean isValidVariationId(final String id) {
        return VALID_ID_PATTERN.matcher(id).matches();
    }

    /**
     * Ensures {@code default.yml} exists in {@code aetheryteDir}, synchronizes existing variation files to the
     * latest schema (with {@code .bak} backups), and loads all {@code *.yml} variation definitions.
     * Always guarantees that {@link Aetheryte#DEFAULT_VARIATION} is present in the returned map.
     */
    public static Map<String, AetheryteVariation> loadDirectory(final Path aetheryteDir, final Logger logger) {
        return loadDirectory(aetheryteDir, logger, true);
    }

    /**
     * Ensures {@code default.yml} exists in {@code aetheryteDir} and loads all {@code *.yml} variation definitions,
     * optionally synchronizing schema updates back to disk when {@code syncSchema} is {@code true}.
     */
    public static Map<String, AetheryteVariation> loadDirectory(
            final Path aetheryteDir,
            final Logger logger,
            final boolean syncSchema
    ) {
        final Map<String, AetheryteVariation> loaded = new LinkedHashMap<>();

        try {
            if (syncSchema) {
                if (!Files.exists(aetheryteDir)) {
                    Files.createDirectories(aetheryteDir);
                }
                final Path defaultFile = aetheryteDir.resolve(Aetheryte.DEFAULT_VARIATION + ".yml");
                if (!Files.exists(defaultFile)) {
                    copyDefaultResource(defaultFile, logger);
                }
            } else if (!Files.isDirectory(aetheryteDir)) {
                loaded.putIfAbsent(Aetheryte.DEFAULT_VARIATION, AetheryteVariation.defaultVariation());
                return Map.copyOf(loaded);
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
                        final CommentedConfigurationNode root = loaderBuilder(file)
                                .build()
                                .load();
                        final AetheryteVariation parsed = root.get(AetheryteVariation.class);
                        final AetheryteVariation effectiveParsed;
                        if (parsed == null) {
                            effectiveParsed = Aetheryte.DEFAULT_VARIATION.equals(rawId)
                                    ? AetheryteVariation.defaultVariation()
                                    : new AetheryteVariation(null, null, null, null, null, null, null, null);
                        } else if (Aetheryte.DEFAULT_VARIATION.equals(rawId) && root.node("recipe").virtual()) {
                            effectiveParsed = new AetheryteVariation(
                                    parsed.displayName(),
                                    parsed.item(),
                                    RecipeSection.defaults(),
                                    parsed.model(),
                                    parsed.hitbox(),
                                    parsed.teleport(),
                                    parsed.placement(),
                                    parsed.limit(),
                                    parsed.warp(),
                                    parsed.bettermodel()
                            );
                        } else {
                            effectiveParsed = parsed;
                        }
                        final AetheryteVariation validated = effectiveParsed.validated(rawId, logger);
                        if (syncSchema) {
                            syncVariationFile(file, rawId, root, validated, logger);
                        }
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
     *
     * @return {@code true} if no migration was needed or migration succeeded; {@code false} if writing failed
     */
    public static boolean migrateLegacyConfig(
            final CommentedConfigurationNode configRoot,
            final Path aetheryteDir,
            final Logger logger
    ) {
        final Path defaultFile = aetheryteDir.resolve(Aetheryte.DEFAULT_VARIATION + ".yml");
        if (Files.exists(defaultFile)) {
            return true;
        }
        final boolean hasLegacyRecipe = configRoot.hasChild("recipe");
        final boolean hasLegacyDisplay = configRoot.node("hologram").hasChild("display-block");
        if (!hasLegacyRecipe && !hasLegacyDisplay) {
            return true;
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

            writeVariation(defaultFile, Aetheryte.DEFAULT_VARIATION, migrated);
            logger.info("Migrated legacy recipe and hologram display-block from config.yml into {}", defaultFile);
            return true;
        } catch (final Exception e) {
            logger.warn("Failed to migrate legacy settings from config.yml to {}; copying default template", defaultFile, e);
            copyDefaultResource(defaultFile, logger);
            return false;
        }
    }

    private static void syncVariationFile(
            final Path file,
            final String variationId,
            final CommentedConfigurationNode existingRoot,
            final AetheryteVariation validated,
            final Logger logger
    ) {
        try {
            final CommentedConfigurationNode targetRoot = createVariationNode(validated, null);
            if (YamlSchemaSync.hasSchemaDiff(existingRoot, targetRoot) && YamlSchemaSync.backupFile(file, logger)) {
                writeVariation(file, variationId, validated);
            }
        } catch (final IOException e) {
            logger.warn("Failed to synchronize variation file {}: {}", file, e.getMessage(), e);
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
            writeVariation(targetFile, Aetheryte.DEFAULT_VARIATION, AetheryteVariation.defaultVariation());
        } catch (final IOException e) {
            logger.warn("Failed to write fallback default variation file to {}", targetFile, e);
        }
    }

    private static void writeVariation(
            final Path targetFile,
            final String variationId,
            final AetheryteVariation variation
    ) throws IOException {
        final String header = variationHeader(variationId);
        final CommentedConfigurationNode dumpRoot = createVariationNode(variation, header);
        if (variation.warp().distance().crossDimension() == null) {
            final CommentedConfigurationNode crossDimNode = dumpRoot.node("warp", "distance", "cross-dimension");
            crossDimNode.set(NULL_SENTINEL);
            crossDimNode.comment("Whether distance limits (min/max) apply to cross-dimension warps (in variations: null to inherit global)");
        }
        if (variation.warp().cost() == null) {
            final CommentedConfigurationNode warpCostNode = dumpRoot.node("warp", "cost");
            warpCostNode.set(NULL_SENTINEL);
            warpCostNode.comment("Optional cost overrides for warps originating from this variation (null to inherit global, [] for free) (#14)");
        }

        final StringWriter stringWriter = new StringWriter();
        final YamlConfigurationLoader stringLoader = YamlConfigurationLoader.builder()
                .sink(() -> new BufferedWriter(stringWriter))
                .indent(YamlSchemaSync.DEFAULT_INDENT_SPACES)
                .nodeStyle(NodeStyle.BLOCK)
                .defaultOptions(opts -> applyVariationOptions(opts, header))
                .build();
        stringLoader.save(dumpRoot);

        final String commentedYaml = YamlSchemaSync.renderInlineComments(
                        stringWriter.toString(),
                        dumpRoot,
                        YamlSchemaSync.DEFAULT_INDENT_SPACES
                )
                .replace(": '" + NULL_SENTINEL + "'", ": null")
                .replace(": \"" + NULL_SENTINEL + "\"", ": null")
                .replace(": " + NULL_SENTINEL, ": null");

        final Path parent = targetFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.writeString(targetFile, commentedYaml, StandardCharsets.UTF_8);
    }

    private static CommentedConfigurationNode createVariationNode(
            final AetheryteVariation variation,
            final @Nullable String header
    ) throws SerializationException {
        final YamlConfigurationLoader nodeLoader = YamlConfigurationLoader.builder()
                .indent(YamlSchemaSync.DEFAULT_INDENT_SPACES)
                .nodeStyle(NodeStyle.BLOCK)
                .defaultOptions(opts -> applyVariationOptions(opts, header))
                .build();
        final CommentedConfigurationNode root = nodeLoader.createNode();
        root.set(AetheryteVariation.class, variation);
        return root;
    }

    private static String variationHeader(final String variationId) {
        if (Aetheryte.DEFAULT_VARIATION.equals(variationId)) {
            return "ClickPortals Aetheryte Variation (default)\n"
                    + "Place additional <type>.yml files in this directory to create custom Aetheryte variations.";
        }
        return "ClickPortals Aetheryte Variation (" + variationId + ")";
    }

    private static ConfigurationOptions applyVariationOptions(
            final ConfigurationOptions opts,
            final @Nullable String header
    ) {
        final ConfigurationOptions withSerializers = opts.serializers(s -> s.register(
                AetheryteVariation.WarpOverrideSection.class,
                AetheryteVariation.WarpOverrideSectionSerializer.INSTANCE
        ));
        return header != null ? withSerializers.header(header) : withSerializers;
    }

    private static YamlConfigurationLoader.Builder loaderBuilder(final Path path) {
        return YamlConfigurationLoader.builder()
                .path(path)
                .indent(YamlSchemaSync.DEFAULT_INDENT_SPACES)
                .nodeStyle(NodeStyle.BLOCK)
                .defaultOptions(opts -> applyVariationOptions(opts, null));
    }
}
