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

        manager.save(config);
        final String savedYaml = Files.readString(configFile, StandardCharsets.UTF_8);
        assertThat(savedYaml).contains("# Maximum total Aetherytes a single player may own across all variations");
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
                """, StandardCharsets.UTF_8);

        final ConfigManager manager = new ConfigManager(configFile, mock(Logger.class));
        final PluginConfig config = manager.load();

        assertThat(config.defaultLocale()).isEqualTo(Locale.US);
        assertThat(config.hologramViewDistance()).isEqualTo(4.0);
        assertThat(config.maxAetherytesPerPlayer()).isEqualTo(-1);
        assertThat(config.placementCooldownTicks()).isEqualTo(0);
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
        assertThat(manager.config().dimensions().amounts().get("world_bad")).isEqualTo(0.0);
        assertThat(manager.config().dimensions().routes()).hasSize(1);
        assertThat(manager.config().dimensions().routes().get(0).between()).containsExactly("world_a", "world_b");
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
        final Path readOnlyDir = tempDir.resolve("readonly");
        Files.createDirectories(readOnlyDir);
        final Path configFile = readOnlyDir.resolve("config.yml");

        // Write a valid initial configuration and load it
        Files.writeString(configFile, """
                limits:
                  max-aetherytes-per-player: 77
                """, StandardCharsets.UTF_8);
        final Logger logger = mock(Logger.class);
        final ConfigManager manager = new ConfigManager(configFile, logger);
        final PluginConfig initial = manager.load();
        assertThat(initial.maxAetherytesPerPlayer()).isEqualTo(77);

        // Delete the file and make the directory read-only so saving defaultConfig fails
        Files.delete(configFile);
        readOnlyDir.toFile().setWritable(false);
        try {
            final PluginConfig afterFailure = manager.load();
            assertThat(manager.lastLoadSuccessful()).isFalse();
            // Retains previous configuration
            assertThat(afterFailure.maxAetherytesPerPlayer()).isEqualTo(77);
        } finally {
            readOnlyDir.toFile().setWritable(true);
        }
    }
}
