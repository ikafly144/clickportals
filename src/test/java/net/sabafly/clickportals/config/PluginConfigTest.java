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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

class PluginConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultConfigIsCreatedAndPreservesEnglishComments() throws IOException {
        final Path configFile = tempDir.resolve("config.yml");
        final ConfigManager manager = new ConfigManager(configFile, mock(Logger.class));

        final PluginConfig loaded = manager.load();

        assertThat(configFile).exists();
        assertThat(loaded).isEqualTo(PluginConfig.defaultConfig());
        assertThat(loaded.defaultLocale()).isEqualTo(Locale.US);
        assertThat(loaded.useClientLocale()).isTrue();
        assertThat(loaded.itemMaterial()).isEqualTo(Material.COMPASS);
        assertThat(loaded.displayBlock()).isEqualTo("minecraft:lodestone");

        final String rawYaml = Files.readString(configFile, StandardCharsets.UTF_8);
        assertThat(rawYaml)
                .contains("ClickPortals Configuration")
                .contains("Language and localization settings")
                .contains("default-locale: en_US")
                .contains("Crafting recipe settings for the Aetheryte creation item")
                .contains("item-material: COMPASS")
                .contains("display-block: minecraft:lodestone");
    }

    @Test
    void validCustomConfigIsLoadedAndSavedWithComments() throws IOException {
        final Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, """
                language:
                  default-locale: ja_JP
                  use-client-locale: false
                recipe:
                  enabled: true
                  item-material: ECHO_SHARD
                  shape:
                    - " G "
                    - "GAG"
                    - " G "
                  ingredients:
                    G: GOLD_INGOT
                    A: AMETHYST_SHARD
                hologram:
                  enabled: true
                  view-distance: 48.0
                  display-block: minecraft:Lodestone
                """, StandardCharsets.UTF_8);

        final ConfigManager manager = new ConfigManager(configFile, mock(Logger.class));
        final PluginConfig config = manager.load();

        assertThat(config.defaultLocale()).isEqualTo(Locale.JAPAN);
        assertThat(config.useClientLocale()).isFalse();
        assertThat(config.itemMaterial()).isEqualTo(Material.ECHO_SHARD);
        assertThat(config.recipeShape()).containsExactly(" G ", "GAG", " G ");
        assertThat(config.recipeIngredients()).containsExactlyEntriesOf(Map.of(
                'G', Material.GOLD_INGOT,
                'A', Material.AMETHYST_SHARD
        ));
        assertThat(config.hologramViewDistance()).isEqualTo(48.0);

        final String savedYaml = Files.readString(configFile, StandardCharsets.UTF_8);
        assertThat(savedYaml).contains("# Whether the crafting recipe is enabled");
    }

    @Test
    void mismatchedShapeAndIngredientsFallsBackToDefaults() throws IOException {
        final Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, """
                language:
                  default-locale: ""
                recipe:
                  enabled: true
                  item-material: INVALID_MATERIAL_NAME
                  shape:
                    - "XXX"
                    - "XXX"
                    - "XXX"
                  ingredients:
                    X: NOT_A_VALID_MATERIAL
                hologram:
                  enabled: true
                  view-distance: 1.0
                """, StandardCharsets.UTF_8);

        final ConfigManager manager = new ConfigManager(configFile, mock(Logger.class));
        final PluginConfig config = manager.load();

        assertThat(config.defaultLocale()).isEqualTo(Locale.US);
        assertThat(config.itemMaterial()).isEqualTo(Material.COMPASS);
        assertThat(config.recipeShape()).isEqualTo(PluginConfig.defaultConfig().recipeShape());
        assertThat(config.recipeIngredients()).isEqualTo(PluginConfig.defaultConfig().recipeIngredients());
        assertThat(config.hologramViewDistance()).isEqualTo(4.0);
    }

    @Test
    void nonRectangularShapeFallsBackToDefaults() {
        final PluginConfig raw = new PluginConfig(
                new PluginConfig.RecipeSection(
                        true,
                        "COMPASS",
                        List.of("SS", "SAS", "S"),
                        Map.of("S", "CHISELED_STONE_BRICKS", "A", "AMETHYST_SHARD")
                ),
                PluginConfig.HologramSection.defaults()
        );

        final PluginConfig validated = raw.validated(mock(Logger.class));

        assertThat(validated.recipeShape()).isEqualTo(PluginConfig.defaultConfig().recipeShape());
        assertThat(validated.recipeIngredients()).isEqualTo(PluginConfig.defaultConfig().recipeIngredients());
    }
}
