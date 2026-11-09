package net.sabafly.clickportals.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.slf4j.Logger;

/**
 * Configuration model for ClickPortals.
 */
public record PluginConfig(
        boolean recipeEnabled,
        List<String> recipeShape,
        Map<Character, Material> recipeIngredients,
        boolean hologramEnabled,
        double hologramViewDistance
) {

    public static PluginConfig defaultConfig() {
        final Map<Character, Material> defaultIngredients = new LinkedHashMap<>();
        defaultIngredients.put('S', Material.CHISELED_STONE_BRICKS);
        defaultIngredients.put('A', Material.AMETHYST_SHARD);
        return new PluginConfig(
                true,
                List.of("SSS", "SAS", "SSS"),
                Map.copyOf(defaultIngredients),
                true,
                32.0
        );
    }

    public static PluginConfig fromFileConfiguration(final FileConfiguration config, final Logger logger) {
        final PluginConfig defaults = defaultConfig();
        final boolean recipeEnabled = config.getBoolean("recipe.enabled", defaults.recipeEnabled());

        final List<String> rawShape = config.getStringList("recipe.shape");
        final List<String> shape = new ArrayList<>();
        if (rawShape.isEmpty() || rawShape.size() > 3) {
            shape.addAll(defaults.recipeShape());
        } else {
            for (final String row : rawShape) {
                if (row != null && !row.isEmpty() && row.length() <= 3) {
                    shape.add(row);
                }
            }
            if (shape.isEmpty()) {
                shape.addAll(defaults.recipeShape());
            }
        }

        final Map<Character, Material> ingredients = new LinkedHashMap<>();
        final ConfigurationSection ingredientsSection = config.getConfigurationSection("recipe.ingredients");
        if (ingredientsSection != null) {
            for (final String key : ingredientsSection.getKeys(false)) {
                if (key.length() != 1) {
                    logger.warn("Ignoring invalid recipe ingredient key '{}' (must be 1 character)", key);
                    continue;
                }
                final String matName = ingredientsSection.getString(key);
                if (matName == null) {
                    continue;
                }
                final Material material = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
                if (material == null) {
                    logger.warn("Unknown material '{}' for recipe ingredient '{}'", matName, key);
                    continue;
                }
                ingredients.put(key.charAt(0), material);
            }
        }
        if (ingredients.isEmpty()) {
            ingredients.putAll(defaults.recipeIngredients());
        }

        final boolean hologramEnabled = config.getBoolean("hologram.enabled", defaults.hologramEnabled());
        final double hologramViewDistance = Math.max(4.0, config.getDouble("hologram.view-distance", defaults.hologramViewDistance()));

        return new PluginConfig(
                recipeEnabled,
                List.copyOf(shape),
                Map.copyOf(ingredients),
                hologramEnabled,
                hologramViewDistance
        );
    }
}
