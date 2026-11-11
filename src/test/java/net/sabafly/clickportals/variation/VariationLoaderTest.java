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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

class VariationLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesDefaultYmlAndLoadsAdditionalVariationsWhileSkippingInvalidFileNames() throws IOException {
        final Path aetheryteDir = tempDir.resolve("aetheryte");
        Files.createDirectories(aetheryteDir);

        Files.writeString(aetheryteDir.resolve("tall.yml"), """
                display-name: "<gold>Tall Aetheryte</gold>"
                item:
                  material: ECHO_SHARD
                model:
                  block: minecraft:lodestone
                  name-height: 3.35
                hitbox:
                  width: 1.5
                  height: 3.0
                teleport:
                  min-radius: 1
                  max-radius: 2
                  face-aetheryte: true
                placement:
                  clearance:
                    radius: 1
                    height: 3
                limit:
                  max-per-player: 1
                  cooldown-ticks: 60
                """, StandardCharsets.UTF_8);

        // Invalid filename with spaces/symbols should be ignored
        Files.writeString(aetheryteDir.resolve("Invalid Name!.yml"), "display-name: Broken\n", StandardCharsets.UTF_8);

        final VariationRegistry registry = VariationRegistry.load(aetheryteDir, mock(Logger.class));

        assertThat(aetheryteDir.resolve("default.yml")).exists();
        assertThat(registry.ids()).containsExactlyInAnyOrder(Aetheryte.DEFAULT_VARIATION, "tall");

        final AetheryteVariation tall = registry.getOrDefault("tall");
        assertThat(tall.displayName()).isEqualTo("<gold>Tall Aetheryte</gold>");
        assertThat(tall.item().bukkitMaterial()).isEqualTo(Material.ECHO_SHARD);
        assertThat(tall.recipe().enabled()).isFalse();
        assertThat(tall.model().block()).isEqualTo("minecraft:lodestone");
        assertThat(tall.model().nameHeight()).isEqualTo(3.35);
        assertThat(tall.hitbox().width()).isEqualTo(1.5f);
        assertThat(tall.hitbox().height()).isEqualTo(3.0f);
        assertThat(tall.teleport().minRadius()).isEqualTo(1);
        assertThat(tall.teleport().maxRadius()).isEqualTo(2);
        assertThat(tall.placement().clearance().radius()).isEqualTo(1);
        assertThat(tall.placement().clearance().height()).isEqualTo(3);
        assertThat(tall.limit().maxPerPlayer()).isEqualTo(1);
        assertThat(tall.limit().cooldownTicks()).isEqualTo(60);

        // Unknown variation falls back to default
        final AetheryteVariation fallback = registry.getOrDefault("nonexistent_type");
        assertThat(fallback).isEqualTo(registry.getOrDefault(Aetheryte.DEFAULT_VARIATION));
        assertThat(registry.find("nonexistent_type")).isEmpty();
    }

    @Test
    void migratesLegacyRecipeAndDisplayBlockFromConfigYml() throws IOException {
        final Path configDir = tempDir.resolve("legacy_plugin_dir");
        Files.createDirectories(configDir);
        final Path configFile = configDir.resolve("config.yml");
        Files.writeString(configFile, """
                recipe:
                  enabled: true
                  shape:
                    - " G "
                    - "GAG"
                    - " G "
                  ingredients:
                    G: GOLD_INGOT
                    A: AMETHYST_SHARD
                hologram:
                  enabled: true
                  display-block: "minecraft:gold_block"
                """, StandardCharsets.UTF_8);

        final net.sabafly.clickportals.config.ConfigManager configManager =
                new net.sabafly.clickportals.config.ConfigManager(configFile, mock(Logger.class));
        configManager.load();

        final Path defaultFile = configDir.resolve("aetheryte").resolve("default.yml");
        assertThat(defaultFile).exists();

        final VariationRegistry registry = VariationRegistry.load(configDir.resolve("aetheryte"), mock(Logger.class));
        final AetheryteVariation defaultVar = registry.getOrDefault(Aetheryte.DEFAULT_VARIATION);
        assertThat(defaultVar.model().block()).isEqualTo("minecraft:gold_block");
        assertThat(defaultVar.recipe().enabled()).isTrue();
        assertThat(defaultVar.recipe().shape()).containsExactly(" G ", "GAG", " G ");
        assertThat(defaultVar.recipe().bukkitIngredients()).containsEntry('G', Material.GOLD_INGOT);
    }
}
