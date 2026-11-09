package net.sabafly.clickportals.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class PluginConfigTest {

    @Test
    void validCustomRecipeIsLoaded() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("recipe.enabled", true);
        yaml.set("recipe.shape", List.of(" G ", "GAG", " G "));
        yaml.set("recipe.ingredients.G", "GOLD_INGOT");
        yaml.set("recipe.ingredients.A", "AMETHYST_SHARD");

        final PluginConfig config = PluginConfig.fromFileConfiguration(yaml, mock(Logger.class));

        assertThat(config.recipeShape()).containsExactly(" G ", "GAG", " G ");
        assertThat(config.recipeIngredients()).containsExactlyEntriesOf(Map.of(
                'G', Material.GOLD_INGOT,
                'A', Material.AMETHYST_SHARD
        ));
    }

    @Test
    void mismatchedShapeAndIngredientsFallsBackToDefaults() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("recipe.shape", List.of("XXX", "XXX", "XXX"));
        yaml.set("recipe.ingredients.X", "NOT_A_VALID_MATERIAL");

        final PluginConfig config = PluginConfig.fromFileConfiguration(yaml, mock(Logger.class));

        assertThat(config.recipeShape()).isEqualTo(PluginConfig.defaultConfig().recipeShape());
        assertThat(config.recipeIngredients()).isEqualTo(PluginConfig.defaultConfig().recipeIngredients());
    }

    @Test
    void nonRectangularShapeFallsBackToDefaults() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("recipe.shape", List.of("SS", "SAS", "S"));
        yaml.set("recipe.ingredients.S", "CHISELED_STONE_BRICKS");
        yaml.set("recipe.ingredients.A", "AMETHYST_SHARD");

        final PluginConfig config = PluginConfig.fromFileConfiguration(yaml, mock(Logger.class));

        assertThat(config.recipeShape()).isEqualTo(PluginConfig.defaultConfig().recipeShape());
        assertThat(config.recipeIngredients()).isEqualTo(PluginConfig.defaultConfig().recipeIngredients());
    }
}
