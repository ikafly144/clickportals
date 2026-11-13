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

    @Test
    void preservesExplicitNullVersusEmptyListInWarpCost() throws IOException {
        final Path aetheryteDir = tempDir.resolve("warp_cost_variations");
        Files.createDirectories(aetheryteDir);

        // Inherit global cost (explicit null)
        Files.writeString(aetheryteDir.resolve("inherit.yml"), """
                warp:
                  cost: null
                """, StandardCharsets.UTF_8);

        // Explicitly free (empty list)
        Files.writeString(aetheryteDir.resolve("free.yml"), """
                warp:
                  cost: []
                """, StandardCharsets.UTF_8);

        // Custom override
        Files.writeString(aetheryteDir.resolve("custom.yml"), """
                warp:
                  cost:
                    - type: VAULT
                      amount: "100"
                """, StandardCharsets.UTF_8);

        final VariationRegistry registry = VariationRegistry.load(aetheryteDir, mock(Logger.class));

        final AetheryteVariation inheritVar = registry.getOrDefault("inherit");
        assertThat(inheritVar.warp()).isNotNull();
        assertThat(inheritVar.warp().cost()).isNull();

        final AetheryteVariation freeVar = registry.getOrDefault("free");
        assertThat(freeVar.warp()).isNotNull();
        assertThat(freeVar.warp().cost()).isEmpty();

        final AetheryteVariation customVar = registry.getOrDefault("custom");
        assertThat(customVar.warp()).isNotNull();
        assertThat(customVar.warp().cost()).hasSize(1);
        assertThat(java.util.Objects.requireNonNull(customVar.warp().cost()).get(0).amount()).isEqualTo("100");

        // default.yml generated automatically should also have null cost (inherits global config)
        final AetheryteVariation defaultVar = registry.getOrDefault(Aetheryte.DEFAULT_VARIATION);
        assertThat(defaultVar.warp()).isNotNull();
        assertThat(defaultVar.warp().cost()).isNull();

        // Sync should preserve null cost in inherit.yml and [] in free.yml
        assertThat(Files.readString(aetheryteDir.resolve("inherit.yml"), StandardCharsets.UTF_8))
                .contains("cost: null")
                .contains("cross-dimension: null");
        assertThat(Files.readString(aetheryteDir.resolve("free.yml"), StandardCharsets.UTF_8))
                .contains("cost: []");
    }

    @Test
    void startupSyncUpdatesDefaultAndCustomVariationsWithBackupAndIsIdempotent() throws IOException {
        final Path aetheryteDir = tempDir.resolve("sync_variations");
        Files.createDirectories(aetheryteDir);

        final String oldDefault = """
                display-name: "<aqua>Custom Default</aqua>"
                legacy-field: "remove-me"
                """;
        final String oldCustom = """
                display-name: "<gold>Grand</gold>"
                item:
                  material: ECHO_SHARD
                recipe:
                  enabled: true
                  shape:
                    - "GGG"
                    - "GEG"
                    - "GGG"
                  ingredients:
                    G: GOLD_BLOCK
                    E: ECHO_SHARD
                old-extra: 123
                """;
        Files.writeString(aetheryteDir.resolve("default.yml"), oldDefault, StandardCharsets.UTF_8);
        Files.writeString(aetheryteDir.resolve("grand.yml"), oldCustom, StandardCharsets.UTF_8);

        final VariationRegistry registry = VariationRegistry.load(aetheryteDir, mock(Logger.class));

        // Both files backed up and synced
        assertThat(aetheryteDir.resolve("default.yml.bak")).exists().hasContent(oldDefault);
        assertThat(aetheryteDir.resolve("grand.yml.bak")).exists().hasContent(oldCustom);

        final AetheryteVariation def = registry.getOrDefault(Aetheryte.DEFAULT_VARIATION);
        assertThat(def.displayName()).isEqualTo("<aqua>Custom Default</aqua>");
        assertThat(def.recipe().enabled()).isTrue();
        assertThat(def.placement().minDistance()).isEqualTo(-1.0);
        assertThat(def.bettermodel().enabled()).isFalse();

        final AetheryteVariation grand = registry.getOrDefault("grand");
        assertThat(grand.displayName()).isEqualTo("<gold>Grand</gold>");
        assertThat(grand.recipe().bukkitIngredients()).containsEntry('G', Material.GOLD_BLOCK).containsEntry('E', Material.ECHO_SHARD);

        final String syncedDefault = Files.readString(aetheryteDir.resolve("default.yml"), StandardCharsets.UTF_8);
        assertThat(syncedDefault)
                .contains("min-distance: -1.0")
                .contains("cross-dimension: null")
                .contains("cost: null")
                .contains("bettermodel:")
                .doesNotContain("legacy-field");

        final String syncedGrand = Files.readString(aetheryteDir.resolve("grand.yml"), StandardCharsets.UTF_8);
        assertThat(syncedGrand)
                .contains("G: GOLD_BLOCK")
                .contains("E: ECHO_SHARD")
                .contains("min-distance: -1.0")
                .doesNotContain("old-extra");

        // Second load must be idempotent (no backup rewrite)
        Files.writeString(aetheryteDir.resolve("default.yml.bak"), "keep", StandardCharsets.UTF_8);
        Files.writeString(aetheryteDir.resolve("grand.yml.bak"), "keep", StandardCharsets.UTF_8);
        VariationRegistry.load(aetheryteDir, mock(Logger.class));
        assertThat(Files.readString(aetheryteDir.resolve("default.yml.bak"), StandardCharsets.UTF_8)).isEqualTo("keep");
        assertThat(Files.readString(aetheryteDir.resolve("grand.yml.bak"), StandardCharsets.UTF_8)).isEqualTo("keep");
    }

    @Test
    void reloadDoesNotModifyVariationFilesAndSyntaxErrorsArePreserved() throws IOException {
        final Path aetheryteDir = tempDir.resolve("reload_variations");
        Files.createDirectories(aetheryteDir);

        final String partialCustom = """
                display-name: "<green>Minimal</green>"
                obsolete-key: true
                """;
        final String brokenYaml = "display-name: [broken";
        Files.writeString(aetheryteDir.resolve("minimal.yml"), partialCustom, StandardCharsets.UTF_8);
        Files.writeString(aetheryteDir.resolve("broken.yml"), brokenYaml, StandardCharsets.UTF_8);

        final VariationRegistry registry = new VariationRegistry(java.util.Map.of(), mock(Logger.class));
        registry.reload(aetheryteDir, mock(Logger.class));

        assertThat(registry.getOrDefault("minimal").displayName()).isEqualTo("<green>Minimal</green>");
        assertThat(aetheryteDir.resolve("minimal.yml.bak")).doesNotExist();
        assertThat(Files.readString(aetheryteDir.resolve("minimal.yml"), StandardCharsets.UTF_8)).isEqualTo(partialCustom);

        // Even on startup sync, broken YAML must not be overwritten
        VariationLoader.loadDirectory(aetheryteDir, mock(Logger.class), true);
        assertThat(aetheryteDir.resolve("broken.yml.bak")).doesNotExist();
        assertThat(Files.readString(aetheryteDir.resolve("broken.yml"), StandardCharsets.UTF_8)).isEqualTo(brokenYaml);
    }

    @Test
    void loadDirectoryWithSyncFalseDoesNotCreateMissingDirectoryOrDefaultFile() {
        final Path missingDir = tempDir.resolve("missing_aetheryte");
        final var loaded = VariationLoader.loadDirectory(missingDir, mock(Logger.class), false);

        assertThat(missingDir).doesNotExist();
        assertThat(loaded).containsKey(Aetheryte.DEFAULT_VARIATION);
    }

    @Test
    void migrateLegacyConfigReturnsSuccessOrFailureStatus() throws IOException {
        final org.spongepowered.configurate.CommentedConfigurationNode legacyRoot =
                org.spongepowered.configurate.CommentedConfigurationNode.root();
        legacyRoot.node("recipe", "enabled").set(false);
        legacyRoot.node("hologram", "display-block").set("minecraft:diamond_block");

        final Path blockedDir = tempDir.resolve("blocked_aetheryte");
        Files.writeString(blockedDir, "not-a-directory", StandardCharsets.UTF_8);
        assertThat(VariationLoader.migrateLegacyConfig(legacyRoot, blockedDir, mock(Logger.class))).isFalse();

        final Path validDir = tempDir.resolve("migrated_aetheryte");
        assertThat(VariationLoader.migrateLegacyConfig(legacyRoot, validDir, mock(Logger.class))).isTrue();
        assertThat(validDir.resolve("default.yml")).exists();
        // Already migrated on subsequent invocation -> also returns true
        assertThat(VariationLoader.migrateLegacyConfig(legacyRoot, validDir, mock(Logger.class))).isTrue();
    }

    @Test
    void migrateLegacyConfigFailureDoesNotCreateFallbackDefaultFile() throws IOException {
        final Path writableDir = tempDir.resolve("writable_failed_migration");
        Files.createDirectories(writableDir);

        final org.spongepowered.configurate.CommentedConfigurationNode badLegacyRoot =
                org.spongepowered.configurate.CommentedConfigurationNode.root();
        // Non-list map under recipe.shape triggers SerializationException in getList(String.class, ...)
        badLegacyRoot.node("recipe", "shape", "invalid").set(true);
        badLegacyRoot.node("hologram", "display-block").set("minecraft:emerald_block");

        assertThat(VariationLoader.migrateLegacyConfig(badLegacyRoot, writableDir, mock(Logger.class))).isFalse();
        assertThat(writableDir.resolve("default.yml")).doesNotExist();
    }
}
