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
}
