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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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
        assertThat(loaded.hologramEnabled()).isTrue();
        assertThat(loaded.hologramViewDistance()).isEqualTo(32.0);
        assertThat(loaded.maxAetherytesPerPlayer()).isEqualTo(-1);
        assertThat(loaded.placementCooldownTicks()).isEqualTo(100);
        assertThat(loaded.maxFavoritesPerPlayer()).isEqualTo(-1);

        final String rawYaml = Files.readString(configFile, StandardCharsets.UTF_8);
        assertThat(rawYaml)
                .contains("ClickPortals Configuration")
                .contains("Language and localization settings")
                .contains("default-locale: en_US")
                .contains("Global per-player Aetheryte placement limit and cooldown settings")
                .contains("max-aetherytes-per-player: -1")
                .contains("placement-cooldown-ticks: 100");
    }

    @Test
    void validCustomConfigIsLoadedAndSavedWithComments() throws IOException {
        final Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, """
                language:
                  default-locale: ja_JP
                  use-client-locale: false
                hologram:
                  enabled: true
                  view-distance: 48.0
                limits:
                  max-aetherytes-per-player: 5
                  placement-cooldown-ticks: 200
                """, StandardCharsets.UTF_8);

        final ConfigManager manager = new ConfigManager(configFile, mock(Logger.class));
        final PluginConfig config = manager.load();

        assertThat(config.defaultLocale()).isEqualTo(Locale.JAPAN);
        assertThat(config.useClientLocale()).isFalse();
        assertThat(config.hologramViewDistance()).isEqualTo(48.0);
        assertThat(config.maxAetherytesPerPlayer()).isEqualTo(5);
        assertThat(config.placementCooldownTicks()).isEqualTo(200);
        assertThat(config.maxFavoritesPerPlayer()).isEqualTo(-1);

        manager.save(config);
        final String savedYaml = Files.readString(configFile, StandardCharsets.UTF_8);
        assertThat(savedYaml).contains("# Maximum total Aetherytes a single player may own across all variations");
    }

    @Test
    void maxFavoritesPerPlayerAcceptsZeroAndPositiveValues() throws IOException {
        final Path zeroFile = tempDir.resolve("config-zero.yml");
        Files.writeString(zeroFile, """
                limits:
                  max-favorites-per-player: 0
                """, StandardCharsets.UTF_8);
        assertThat(new ConfigManager(zeroFile, mock(Logger.class)).load().maxFavoritesPerPlayer()).isEqualTo(0);

        final Path positiveFile = tempDir.resolve("config-positive.yml");
        Files.writeString(positiveFile, """
                limits:
                  max-favorites-per-player: 12
                """, StandardCharsets.UTF_8);
        assertThat(new ConfigManager(positiveFile, mock(Logger.class)).load().maxFavoritesPerPlayer()).isEqualTo(12);
    }

    @Test
    void invalidValuesFallBackToSafeDefaults() throws IOException {
        final Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, """
                language:
                  default-locale: ""
                hologram:
                  enabled: true
                  view-distance: 1.0
                limits:
                  max-aetherytes-per-player: -99
                  placement-cooldown-ticks: -50
                  max-favorites-per-player: -5
                """, StandardCharsets.UTF_8);

        final ConfigManager manager = new ConfigManager(configFile, mock(Logger.class));
        final PluginConfig config = manager.load();

        assertThat(config.defaultLocale()).isEqualTo(Locale.US);
        assertThat(config.hologramViewDistance()).isEqualTo(4.0);
        assertThat(config.maxAetherytesPerPlayer()).isEqualTo(-1);
        assertThat(config.placementCooldownTicks()).isEqualTo(0);
        assertThat(config.maxFavoritesPerPlayer()).isEqualTo(-1);
    }

    @Test
    void minGreaterThanMaxAdjustsMaxToUnlimited() throws IOException {
        final Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, """
                warp:
                  distance:
                    min: 100.0
                    max: 50.0
                """, StandardCharsets.UTF_8);

        final Logger logger = mock(Logger.class);
        final ConfigManager manager = new ConfigManager(configFile, logger);
        final PluginConfig config = manager.load();

        assertThat(config.warpDistanceMin()).isEqualTo(100.0);
        assertThat(config.warpDistanceMax()).isEqualTo(-1.0);
        org.mockito.Mockito.verify(logger).warn(
                org.mockito.ArgumentMatchers.contains("greater than"),
                eq(100.0),
                eq(50.0)
        );
    }

    @Test
    void syntaxErrorInConfigRetainsActiveConfigAndSetsLastLoadSuccessfulFalse() throws IOException {
        final Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, """
                limits:
                  max-aetherytes-per-player: 42
                """, StandardCharsets.UTF_8);

        final Logger logger = mock(Logger.class);
        final ConfigManager manager = new ConfigManager(configFile, logger);
        final PluginConfig initial = manager.load();

        assertThat(manager.lastLoadSuccessful()).isTrue();
        assertThat(initial.maxAetherytesPerPlayer()).isEqualTo(42);

        // Corrupt file with syntax error
        Files.writeString(configFile, "limits: [ invalid yaml syntax {", StandardCharsets.UTF_8);

        final PluginConfig reloaded = manager.load();

        assertThat(manager.lastLoadSuccessful()).isFalse();
        // Retains active config
        assertThat(reloaded.maxAetherytesPerPlayer()).isEqualTo(42);
        // File on disk was NOT overwritten
        assertThat(Files.readString(configFile, StandardCharsets.UTF_8)).contains("limits: [ invalid yaml syntax {");
        org.mockito.Mockito.verify(logger).error(
                org.mockito.ArgumentMatchers.contains("syntax or deserialization error"),
                eq(configFile),
                any(),
                any()
        );
    }

    @Test
    void dimensionsValidationLogsWarningsForInvalidExpressions() throws IOException {
        final Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, """
                dimensions:
                  default-cost:
                    - type: UNKNOWN_COST
                      amount: "10"
                    - type: VAULT
                      amount: "invalid expression ++"
                  amounts:
                    world_bad: -5.0
                  routes:
                    - between: [ "only_one_world" ]
                    - between: [ "world_a", "world_b" ]
                      cost-override:
                        world_b:
                          amount: "invalid syntax ++"
                """, StandardCharsets.UTF_8);

        final Logger logger = mock(Logger.class);
        final ConfigManager manager = new ConfigManager(configFile, logger);
        manager.load();

        org.mockito.Mockito.verify(logger).warn(org.mockito.ArgumentMatchers.contains("Unknown cost type"), eq("UNKNOWN_COST"));
        org.mockito.Mockito.verify(logger).warn(org.mockito.ArgumentMatchers.contains("Invalid cost expression"), eq("invalid expression ++"), any());
        org.mockito.Mockito.verify(logger).warn(org.mockito.ArgumentMatchers.contains("Invalid dimension amount"), eq(-5.0), eq("world_bad"));
        org.mockito.Mockito.verify(logger).warn(org.mockito.ArgumentMatchers.contains("between' must contain at least 2"), any(Object.class));
        org.mockito.Mockito.verify(logger).warn(org.mockito.ArgumentMatchers.contains("Invalid cost-override expression"), eq("invalid syntax ++"), eq("world_b"), any());

        // Sanitized amounts and routes
        assertThat(manager.config().dimensions().defaultCost()).hasSize(2);
        assertThat(manager.config().dimensions().amounts().get("world_bad")).isEqualTo(0.0);
        assertThat(manager.config().dimensions().routes()).hasSize(1);
        assertThat(manager.config().dimensions().routes().get(0).between()).containsExactly("world_a", "world_b");
        final var overrides = java.util.Objects.requireNonNull(manager.config().dimensions().routes().get(0).costOverride());
        final var bOverride = java.util.Objects.requireNonNull(overrides.get("world_b"));
        assertThat(bOverride.amount()).isEqualTo("invalid syntax ++");
    }

    @Test
    void minDistanceBetweenAetherytesAndCrossDimensionLoadedAndValidated() throws IOException {
        final Path configFile = tempDir.resolve("config.yml");
        Files.writeString(configFile, """
                limits:
                  min-distance-between-aetherytes: 25.5
                warp:
                  distance:
                    min: 5.0
                    max: 500.0
                    cross-dimension: false
                """, StandardCharsets.UTF_8);

        final Logger logger = mock(Logger.class);
        final ConfigManager manager = new ConfigManager(configFile, logger);
        final PluginConfig config = manager.load();

        assertThat(config.minDistanceBetweenAetherytes()).isEqualTo(25.5);
        assertThat(config.warpDistanceMin()).isEqualTo(5.0);
        assertThat(config.warpDistanceMax()).isEqualTo(500.0);
        assertThat(config.warpDistanceCrossDimension()).isFalse();

        // Invalid negative or non-finite min-distance-between-aetherytes falls back to 0.0
        Files.writeString(configFile, """
                limits:
                  min-distance-between-aetherytes: -10.0
                warp:
                  distance:
                    cross-dimension: true
                """, StandardCharsets.UTF_8);
        final PluginConfig config2 = manager.load();
        assertThat(config2.minDistanceBetweenAetherytes()).isEqualTo(0.0);
        assertThat(config2.warpDistanceCrossDimension()).isTrue();
    }

    @Test
    void initialDefaultSaveFailurePreservesConfigWithoutOverwriting() throws IOException {
        final Path dir = tempDir.resolve("blocked_save_dir");
        Files.createDirectories(dir);
        final Path configFile = dir.resolve("config.yml");

        // Write a valid initial configuration and load it
        Files.writeString(configFile, """
                limits:
                  max-aetherytes-per-player: 77
                """, StandardCharsets.UTF_8);
        final Logger logger = mock(Logger.class);
        final ConfigManager manager = new ConfigManager(configFile, logger);
        final PluginConfig initial = manager.load();
        assertThat(initial.maxAetherytesPerPlayer()).isEqualTo(77);

        // Delete configFile and replace directory 'dir' with a regular file
        // so that creating or writing to dir/config.yml fails deterministically (NotADirectoryException)
        // regardless of OS permissions or root user status
        Files.delete(configFile);
        Files.deleteIfExists(dir.resolve("config.yml.bak"));
        Files.delete(dir);
        Files.writeString(dir, "not-a-directory", StandardCharsets.UTF_8);

        final PluginConfig afterFailure = manager.load();
        assertThat(manager.lastLoadSuccessful()).isFalse();
        // Retains previous configuration
        assertThat(afterFailure.maxAetherytesPerPlayer()).isEqualTo(77);
    }

    @Test
    void startupLoadSyncsMissingAndObsoleteKeysWithBackupWhilePreservingUserValuesAndDynamicMaps() throws IOException {
        final Path configFile = tempDir.resolve("config.yml");
        final Path backupFile = tempDir.resolve("config.yml.bak");
        final String legacyYaml = """
                language:
                  default-locale: ja_JP
                  use-client-locale: false
                hologram:
                  enabled: true
                  view-distance: 40.0
                  legacy-unused: true
                limits:
                  max-aetherytes-per-player: 8
                  placement-cooldown-ticks: 200
                dimensions:
                  default-cost:
                    - type: vault
                      amount: "$amount"
                      required: true
                  amounts:
                    custom_realm: 42.0
                  routes: []
                obsolete-top-level: "should-be-removed"
                """;
        Files.writeString(configFile, legacyYaml, StandardCharsets.UTF_8);

        final ConfigManager manager = new ConfigManager(configFile, mock(Logger.class));
        final PluginConfig loaded = manager.load();

        assertThat(loaded.defaultLocale()).isEqualTo(Locale.JAPAN);
        assertThat(loaded.useClientLocale()).isFalse();
        assertThat(loaded.hologramViewDistance()).isEqualTo(40.0);
        assertThat(loaded.maxAetherytesPerPlayer()).isEqualTo(8);
        assertThat(loaded.placementCooldownTicks()).isEqualTo(200);
        assertThat(loaded.minDistanceBetweenAetherytes()).isEqualTo(0.0);
        assertThat(loaded.maxFavoritesPerPlayer()).isEqualTo(-1);
        assertThat(loaded.dimensions().amounts()).containsEntry("custom_realm", 42.0);

        // Backup created with original content
        assertThat(backupFile).exists().hasContent(legacyYaml);

        // Updated file has new keys, comments, preserved dynamic world, and removed obsolete keys
        final String syncedYaml = Files.readString(configFile, StandardCharsets.UTF_8);
        assertThat(syncedYaml)
                .contains("min-distance-between-aetherytes: 0.0")
                .contains("max-favorites-per-player: -1")
                .contains("cross-dimension: true")
                .contains("custom_realm: 42.0")
                .contains("default-locale: ja_JP")
                .doesNotContain("legacy-unused")
                .doesNotContain("obsolete-top-level");

        // Second startup load is idempotent and does not overwrite backup or file
        Files.writeString(backupFile, "sentinel-backup", StandardCharsets.UTF_8);
        manager.load();
        assertThat(Files.readString(backupFile, StandardCharsets.UTF_8)).isEqualTo("sentinel-backup");
    }

    @Test
    void reloadDoesNotModifyOrBackupConfigFile() throws IOException {
        final Path configFile = tempDir.resolve("config.yml");
        final String partialYaml = """
                limits:
                  max-aetherytes-per-player: 12
                obsolete-key: true
                """;
        Files.writeString(configFile, partialYaml, StandardCharsets.UTF_8);

        final ConfigManager manager = new ConfigManager(configFile, mock(Logger.class));
        final PluginConfig reloaded = manager.reload();

        assertThat(reloaded.maxAetherytesPerPlayer()).isEqualTo(12);
        assertThat(tempDir.resolve("config.yml.bak")).doesNotExist();
        assertThat(Files.readString(configFile, StandardCharsets.UTF_8)).isEqualTo(partialYaml);
    }

    @Test
    void syncsConfigWhenOnlyMissingKeysExist() throws IOException {
        final Path configFile = tempDir.resolve("config.yml");
        final Path backupFile = tempDir.resolve("config.yml.bak");
        final String partialOnlyYaml = """
                language:
                  default-locale: ja_JP
                limits:
                  max-aetherytes-per-player: 5
                """;
        Files.writeString(configFile, partialOnlyYaml, StandardCharsets.UTF_8);

        final ConfigManager manager = new ConfigManager(configFile, mock(Logger.class));
        final PluginConfig loaded = manager.load();

        assertThat(loaded.defaultLocale()).isEqualTo(Locale.JAPAN);
        assertThat(loaded.maxAetherytesPerPlayer()).isEqualTo(5);
        assertThat(backupFile).exists().hasContent(partialOnlyYaml);
        assertThat(Files.readString(configFile, StandardCharsets.UTF_8))
                .contains("default-locale: ja_JP")
                .contains("max-aetherytes-per-player: 5")
                .contains("min-distance-between-aetherytes: 0.0")
                .contains("max-favorites-per-player: -1")
                .contains("dimensions:");
    }

    @Test
    void preservesLegacyKeysInConfigWhenMigrationFails() throws IOException {
        final Path configDir = tempDir.resolve("migration_fail");
        Files.createDirectories(configDir);
        // Block aetheryte directory creation with a regular file so migration fails
        Files.writeString(configDir.resolve("aetheryte"), "not-a-directory", StandardCharsets.UTF_8);

        final Path configFile = configDir.resolve("config.yml");
        final String legacyYaml = """
                language:
                  default-locale: en_US
                recipe:
                  enabled: false
                hologram:
                  enabled: true
                  view-distance: 32.0
                  display-block: minecraft:emerald_block
                """;
        Files.writeString(configFile, legacyYaml, StandardCharsets.UTF_8);

        final ConfigManager manager = new ConfigManager(configFile, mock(Logger.class));
        final PluginConfig loaded = manager.load();

        assertThat(loaded.defaultLocale()).isEqualTo(Locale.US);
        assertThat(configDir.resolve("config.yml.bak")).doesNotExist();
        assertThat(Files.readString(configFile, StandardCharsets.UTF_8)).isEqualTo(legacyYaml);
    }

    @Test
    void reloadOnMissingDirectoryOrLegacyConfigIsStrictlyReadOnly() throws IOException {
        final Path missingDir = tempDir.resolve("missing_dir");
        final Path missingConfig = missingDir.resolve("config.yml");
        final ConfigManager missingManager = new ConfigManager(missingConfig, mock(Logger.class));

        final PluginConfig missingReload = missingManager.reload();
        assertThat(missingManager.lastLoadSuccessful()).isFalse();
        assertThat(missingReload).isEqualTo(PluginConfig.defaultConfig());
        assertThat(missingDir).doesNotExist();
        assertThat(missingConfig).doesNotExist();

        final Path legacyDir = tempDir.resolve("legacy_reload");
        Files.createDirectories(legacyDir);
        final Path legacyConfig = legacyDir.resolve("config.yml");
        final String legacyYaml = """
                language:
                  default-locale: en_US
                recipe:
                  enabled: false
                hologram:
                  enabled: true
                  view-distance: 32.0
                  display-block: minecraft:diamond_block
                """;
        Files.writeString(legacyConfig, legacyYaml, StandardCharsets.UTF_8);

        final ConfigManager legacyManager = new ConfigManager(legacyConfig, mock(Logger.class));
        legacyManager.reload();

        assertThat(legacyManager.lastLoadSuccessful()).isTrue();
        assertThat(legacyDir.resolve("aetheryte")).doesNotExist();
        assertThat(legacyDir.resolve("config.yml.bak")).doesNotExist();
        assertThat(Files.readString(legacyConfig, StandardCharsets.UTF_8)).isEqualTo(legacyYaml);
    }

    @Test
    void failedLegacyMigrationRemainsPendingAcrossStartupsUntilResolved() throws IOException {
        final Path configDir = tempDir.resolve("pending_migration_retry");
        final Path aetheryteDir = configDir.resolve("aetheryte");
        Files.createDirectories(configDir);

        final Path configFile = configDir.resolve("config.yml");
        final String brokenLegacyYaml = """
                language:
                  default-locale: en_US
                recipe:
                  enabled: true
                  shape:
                    invalid: true
                hologram:
                  enabled: true
                  view-distance: 32.0
                  display-block: minecraft:emerald_block
                """;
        Files.writeString(configFile, brokenLegacyYaml, StandardCharsets.UTF_8);

        // First startup: migration fails -> pendingLegacyMigration is true and no fallback default.yml is created
        final ConfigManager manager1 = new ConfigManager(configFile, mock(Logger.class));
        manager1.load();
        net.sabafly.clickportals.variation.VariationRegistry.load(
                aetheryteDir,
                mock(Logger.class),
                !manager1.pendingLegacyMigration()
        );
        assertThat(manager1.pendingLegacyMigration()).isTrue();
        assertThat(aetheryteDir.resolve("default.yml")).doesNotExist();
        assertThat(configDir.resolve("config.yml.bak")).doesNotExist();
        assertThat(Files.readString(configFile, StandardCharsets.UTF_8)).isEqualTo(brokenLegacyYaml);

        // Second startup (still unfixed): must NOT delete legacy config keys either
        final ConfigManager manager2 = new ConfigManager(configFile, mock(Logger.class));
        manager2.load();
        net.sabafly.clickportals.variation.VariationRegistry.load(
                aetheryteDir,
                mock(Logger.class),
                !manager2.pendingLegacyMigration()
        );
        assertThat(manager2.pendingLegacyMigration()).isTrue();
        assertThat(aetheryteDir.resolve("default.yml")).doesNotExist();
        assertThat(configDir.resolve("config.yml.bak")).doesNotExist();
        assertThat(Files.readString(configFile, StandardCharsets.UTF_8)).isEqualTo(brokenLegacyYaml);

        // After fixing recipe.shape, next startup migrates display-block into default.yml and cleans config.yml
        final String fixedLegacyYaml = """
                language:
                  default-locale: en_US
                recipe:
                  enabled: false
                  shape:
                    - "SSS"
                    - "SAS"
                    - "SSS"
                hologram:
                  enabled: true
                  view-distance: 32.0
                  display-block: minecraft:emerald_block
                """;
        Files.writeString(configFile, fixedLegacyYaml, StandardCharsets.UTF_8);

        final ConfigManager manager3 = new ConfigManager(configFile, mock(Logger.class));
        manager3.load();
        final var registry = net.sabafly.clickportals.variation.VariationRegistry.load(
                aetheryteDir,
                mock(Logger.class),
                !manager3.pendingLegacyMigration()
        );

        assertThat(manager3.pendingLegacyMigration()).isFalse();
        assertThat(aetheryteDir.resolve("default.yml")).exists();
        assertThat(registry.getOrDefault(net.sabafly.clickportals.aetheryte.Aetheryte.DEFAULT_VARIATION).model().block())
                .isEqualTo("minecraft:emerald_block");
        assertThat(configDir.resolve("config.yml.bak")).exists();
        assertThat(Files.readString(configFile, StandardCharsets.UTF_8)).doesNotContain("display-block");
    }
}
