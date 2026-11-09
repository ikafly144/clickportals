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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/**
 * Type-safe Configurate root configuration model for ClickPortals (#33).
 */
@ConfigSerializable
public record PluginConfig(
        @Setting("recipe")
        @Comment("エーテライト作成用アイテムのクラフトレシピ設定")
        RecipeSection recipe,

        @Setting("hologram")
        @Comment("エーテライトのクライアントサイド表示 (PacketEvents Display & Interaction) 設定")
        HologramSection hologram
) {

    /**
     * Crafting recipe configuration section.
     */
    @ConfigSerializable
    public record RecipeSection(
            @Setting("enabled")
            @Comment("クラフトレシピを有効にするかどうか")
            boolean enabled,

            @Setting("item-material")
            @Comment("エーテライト作成用アイテムのベース素材 (機能コンポーネントはマスクされます)")
            String itemMaterial,

            @Setting("shape")
            @Comment("3x3 クラフトグリッドの形状 (1〜3行、各行1〜3文字)")
            List<String> shape,

            @Setting("ingredients")
            @Comment("形状内の文字と Bukkit Material 名の対応")
            Map<String, String> ingredients
    ) {
        /**
         * Returns the default crafting recipe section.
         */
        public static RecipeSection defaults() {
            final Map<String, String> defaultIngredients = new LinkedHashMap<>();
            defaultIngredients.put("S", Material.CHISELED_STONE_BRICKS.name());
            defaultIngredients.put("A", Material.AMETHYST_SHARD.name());
            return new RecipeSection(
                    true,
                    Material.COMPASS.name(),
                    List.of("SSS", "SAS", "SSS"),
                    Map.copyOf(defaultIngredients)
            );
        }
    }

    /**
     * Client-side visual and interaction configuration section.
     */
    @ConfigSerializable
    public record HologramSection(
            @Setting("enabled")
            @Comment("PacketEvents を用いたクライアントサイド表示とインタラクト判定を有効にするかどうか")
            boolean enabled,

            @Setting("view-distance")
            @Comment("ホログラムおよびモデルの表示距離 (ブロック単位、最小 4.0)")
            double viewDistance,

            @Setting("display-block")
            @Comment("エーテライトの筐体としてクライアントに表示するブロック状態")
            String displayBlock
    ) {
        /**
         * Returns the default hologram section.
         */
        public static HologramSection defaults() {
            return new HologramSection(true, 32.0, "minecraft:lodestone");
        }
    }

    /**
     * Compact constructor ensuring non-null sections when deserialized by Configurate.
     */
    public PluginConfig {
        if (recipe == null) {
            recipe = RecipeSection.defaults();
        }
        if (hologram == null) {
            hologram = HologramSection.defaults();
        }
    }

    /**
     * Returns the default {@link PluginConfig}.
     */
    public static PluginConfig defaultConfig() {
        return new PluginConfig(RecipeSection.defaults(), HologramSection.defaults());
    }

    /**
     * Validates and normalizes this configuration, falling back to safe defaults on invalid values.
     */
    public PluginConfig validated(final Logger logger) {
        final RecipeSection defaultRecipe = RecipeSection.defaults();
        final HologramSection defaultHologram = HologramSection.defaults();

        final Material resolvedItemMat = parseMaterial(recipe.itemMaterial());
        final String validItemMatName;
        if (resolvedItemMat == null || isAirMaterial(resolvedItemMat)) {
            logger.warn("Unknown or invalid recipe.item-material '{}'; falling back to {}",
                    recipe.itemMaterial(), defaultRecipe.itemMaterial());
            validItemMatName = defaultRecipe.itemMaterial();
        } else {
            validItemMatName = resolvedItemMat.name();
        }

        final List<String> rawShape = recipe.shape() == null ? List.of() : recipe.shape();
        final List<String> shape = new ArrayList<>();
        if (rawShape.isEmpty() || rawShape.size() > 3) {
            shape.addAll(defaultRecipe.shape());
        } else {
            for (final String row : rawShape) {
                if (row != null && !row.isEmpty() && row.length() <= 3) {
                    shape.add(row);
                }
            }
            if (shape.isEmpty()) {
                shape.addAll(defaultRecipe.shape());
            }
        }

        final Map<Character, Material> parsedIngredients = new LinkedHashMap<>();
        final Map<String, String> normalizedIngredientStrings = new LinkedHashMap<>();
        final Map<String, String> rawIngredients = recipe.ingredients() == null ? Map.of() : recipe.ingredients();
        for (final Map.Entry<String, String> entry : rawIngredients.entrySet()) {
            final String key = entry.getKey();
            final String matName = entry.getValue();
            if (key == null || key.length() != 1) {
                logger.warn("Ignoring invalid recipe ingredient key '{}' (must be 1 character)", key);
                continue;
            }
            final Material material = parseMaterial(matName);
            if (material == null || isAirMaterial(material)) {
                logger.warn("Unknown material '{}' for recipe ingredient '{}'", matName, key);
                continue;
            }
            parsedIngredients.put(key.charAt(0), material);
            normalizedIngredientStrings.put(key, material.name());
        }

        if (parsedIngredients.isEmpty() || !isValidRecipeCombination(shape, parsedIngredients)) {
            if (!rawIngredients.isEmpty() || !rawShape.equals(defaultRecipe.shape())) {
                logger.warn("Invalid recipe shape/ingredients combination; falling back to default recipe");
            }
            shape.clear();
            shape.addAll(defaultRecipe.shape());
            normalizedIngredientStrings.clear();
            normalizedIngredientStrings.putAll(defaultRecipe.ingredients());
        }

        final double validViewDistance = Math.max(4.0, hologram.viewDistance());
        final String rawDisplayBlock = hologram.displayBlock();
        final String validDisplayBlock = (rawDisplayBlock == null || rawDisplayBlock.isBlank())
                ? defaultHologram.displayBlock()
                : rawDisplayBlock.strip();

        return new PluginConfig(
                new RecipeSection(
                        recipe.enabled(),
                        validItemMatName,
                        List.copyOf(shape),
                        Map.copyOf(normalizedIngredientStrings)
                ),
                new HologramSection(
                        hologram.enabled(),
                        validViewDistance,
                        validDisplayBlock
                )
        );
    }

    /**
     * Returns whether the crafting recipe is enabled.
     */
    public boolean recipeEnabled() {
        return recipe.enabled();
    }

    /**
     * Returns the base {@link Material} for the Aetheryte creation item.
     */
    public Material itemMaterial() {
        final Material mat = parseMaterial(recipe.itemMaterial());
        return mat != null && !isAirMaterial(mat) ? mat : Material.COMPASS;
    }

    /**
     * Returns the validated 3x3 crafting grid shape rows.
     */
    public List<String> recipeShape() {
        return recipe.shape();
    }

    /**
     * Returns the validated map of crafting ingredient characters to Bukkit {@link Material}s.
     */
    public Map<Character, Material> recipeIngredients() {
        final Map<Character, Material> result = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : recipe.ingredients().entrySet()) {
            final String key = entry.getKey();
            final Material mat = parseMaterial(entry.getValue());
            if (key != null && key.length() == 1 && mat != null) {
                result.put(key.charAt(0), mat);
            }
        }
        return Map.copyOf(result);
    }

    /**
     * Returns whether client-side holograms and visuals are enabled.
     */
    public boolean hologramEnabled() {
        return hologram.enabled();
    }

    /**
     * Returns the view distance in blocks for client-side visuals.
     */
    public double hologramViewDistance() {
        return hologram.viewDistance();
    }

    /**
     * Returns the block state identifier displayed for the Aetheryte body.
     */
    public String displayBlock() {
        return hologram.displayBlock();
    }

    private static boolean isAirMaterial(final Material material) {
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR;
    }

    private static @Nullable Material parseMaterial(@Nullable final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.strip().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        try {
            return Material.valueOf(normalized);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Checks that the shape rows are rectangular and that shape symbols and ingredient keys match.
     */
    static boolean isValidRecipeCombination(final List<String> shape, final Map<Character, Material> ingredients) {
        if (shape.isEmpty() || ingredients.isEmpty()) {
            return false;
        }
        final boolean rectangular = shape.stream().mapToInt(String::length).distinct().count() == 1;
        if (!rectangular) {
            return false;
        }
        final boolean allIngredientKeysInShape = ingredients.keySet().stream()
                .allMatch(key -> shape.stream().anyMatch(row -> row.indexOf(key) >= 0));
        if (!allIngredientKeysInShape) {
            return false;
        }
        for (final String row : shape) {
            for (int i = 0; i < row.length(); i++) {
                final char symbol = row.charAt(i);
                if (symbol != ' ' && !ingredients.containsKey(symbol)) {
                    return false;
                }
            }
        }
        return true;
    }
}
