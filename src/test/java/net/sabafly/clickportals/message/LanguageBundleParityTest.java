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
package net.sabafly.clickportals.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.sabafly.clickportals.message.LanguageLoader.TranslationBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class LanguageBundleParityTest {

    @TempDir
    Path tempDir;

    @Test
    void bundledEnglishAndJapaneseYamlContainEveryMessageKeyWithNoExtras() {
        final Set<String> expectedPaths = new HashSet<>();
        final Set<MessageKey> expectedStringKeys = EnumSet.noneOf(MessageKey.class);
        final Set<MessageKey> expectedListKeys = EnumSet.noneOf(MessageKey.class);
        for (final MessageKey key : MessageKey.values()) {
            expectedPaths.add(key.path());
            if (key.isList()) {
                expectedListKeys.add(key);
            } else {
                expectedStringKeys.add(key);
            }
        }

        for (final String code : LanguageLoader.BUNDLED_LOCALES) {
            final CommentedConfigurationNode root = LanguageLoader.loadClasspathNode("/lang/" + code + ".yml", null);
            assertThat(root).as("Classpath bundle /lang/%s.yml", code).isNotNull();

            final List<String> leafPaths = LanguageLoader.collectLeafPaths(Objects.requireNonNull(root));
            assertThat(new HashSet<>(leafPaths))
                    .as("Leaf keys in /lang/%s.yml", code)
                    .isEqualTo(expectedPaths);
        }

        final Map<Locale, TranslationBundle> bundles = LanguageLoader.loadBundledOnly(mock(Logger.class));
        assertThat(bundles).containsKeys(Locale.US, Locale.JAPAN);

        for (final TranslationBundle bundle : bundles.values()) {
            assertThat(bundle.strings().keySet()).isEqualTo(expectedStringKeys);
            assertThat(bundle.lists().keySet()).isEqualTo(expectedListKeys);
        }
    }

    @Test
    void loadDirectoryCopiesBundledFilesWhenMissing() {
        final Path langDir = tempDir.resolve("lang");
        final Map<Locale, TranslationBundle> loaded = LanguageLoader.loadDirectory(langDir, mock(Logger.class));

        assertThat(langDir.resolve("en_US.yml")).exists();
        assertThat(langDir.resolve("ja_JP.yml")).exists();
        assertThat(loaded).containsKeys(Locale.US, Locale.JAPAN);
    }

    @Test
    void startupSyncUpdatesBundledLanguageFilesWithBackupWhileLeavingCustomLocalesUntouched() throws IOException {
        final Path langDir = tempDir.resolve("lang_sync");
        Files.createDirectories(langDir);

        final String oldEnUs = """
                prefix: "<gray>[Custom]</gray> "
                cost:
                  free: "<gold>Custom Free</gold>"
                obsolete:
                  removed-key: "old value"
                """;
        final String customDe = """
                cost:
                  free: "<green>Kostenlos</green>"
                """;
        Files.writeString(langDir.resolve("en_US.yml"), oldEnUs, StandardCharsets.UTF_8);
        Files.writeString(langDir.resolve("de_DE.yml"), customDe, StandardCharsets.UTF_8);

        final Messages messages = Messages.load(langDir, Locale.US, true, mock(Logger.class));

        // User overrides preserved in memory and on disk
        assertThat(messages).isNotNull();
        assertThat(langDir.resolve("en_US.yml.bak")).exists().hasContent(oldEnUs);
        assertThat(langDir.resolve("de_DE.yml.bak")).doesNotExist();
        assertThat(Files.readString(langDir.resolve("de_DE.yml"), StandardCharsets.UTF_8)).isEqualTo(customDe);

        final CommentedConfigurationNode syncedEnNode = YamlConfigurationLoader.builder()
                .path(langDir.resolve("en_US.yml"))
                .build()
                .load();
        assertThat(syncedEnNode.node("prefix").getString()).isEqualTo("<gray>[Custom]</gray> ");
        assertThat(syncedEnNode.node("cost", "free").getString()).isEqualTo("<gold>Custom Free</gold>");
        assertThat(syncedEnNode.node("obsolete").virtual()).isTrue();
        assertThat(new HashSet<>(LanguageLoader.collectLeafPaths(syncedEnNode)))
                .isEqualTo(Arrays.stream(MessageKey.values()).map(MessageKey::path).collect(Collectors.toSet()));

        // Idempotent on subsequent startup loads
        Files.writeString(langDir.resolve("en_US.yml.bak"), "keep", StandardCharsets.UTF_8);
        Messages.load(langDir, Locale.US, true, mock(Logger.class));
        assertThat(Files.readString(langDir.resolve("en_US.yml.bak"), StandardCharsets.UTF_8)).isEqualTo("keep");
    }

    @Test
    void reloadDoesNotModifyOrBackupLanguageFiles() throws IOException {
        final Path langDir = tempDir.resolve("lang_reload");
        Files.createDirectories(langDir);

        final String partialEnUs = """
                cost:
                  free: "<green>Free Override</green>"
                obsolete-key: "keep-on-reload"
                """;
        Files.writeString(langDir.resolve("en_US.yml"), partialEnUs, StandardCharsets.UTF_8);

        final Messages messages = Messages.defaults();
        messages.reload(langDir, Locale.US, true, mock(Logger.class));

        assertThat(langDir.resolve("en_US.yml.bak")).doesNotExist();
        assertThat(Files.readString(langDir.resolve("en_US.yml"), StandardCharsets.UTF_8)).isEqualTo(partialEnUs);
    }

    @Test
    void loadDirectoryWithSyncFalseDoesNotCreateMissingDirectoryOrBundledFiles() {
        final Path missingLangDir = tempDir.resolve("missing_lang");
        final var loaded = LanguageLoader.loadDirectory(missingLangDir, mock(Logger.class), false);

        assertThat(missingLangDir).doesNotExist();
        assertThat(loaded).containsKey(Locale.US);
    }

    @Test
    void syncBundledLanguageFileIsIdempotentWithCustomizedListLength() throws IOException {
        final Path langDir = tempDir.resolve("lang_custom_lore");
        LanguageLoader.loadDirectory(langDir, mock(Logger.class), true);

        final Path enFile = langDir.resolve("en_US.yml");
        final String original = Files.readString(enFile, StandardCharsets.UTF_8);
        final String customized = original.replace(
                """
                  lore:
                    - "<!italic><gray>Right-click a block to place</gray>"
                    - "<!italic><gray>an Aetheryte (teleport point).</gray>"
                """,
                """
                  lore:
                    - "<!italic><gray>Line one</gray>"
                    - "<!italic><gray>Line two</gray>"
                    - "<!italic><gray>Line three</gray>"
                """
        );
        assertThat(customized).isNotEqualTo(original);
        Files.writeString(enFile, customized, StandardCharsets.UTF_8);

        final var loaded = LanguageLoader.loadDirectory(langDir, mock(Logger.class), true);
        final var enBundle = java.util.Objects.requireNonNull(loaded.get(Locale.US));
        assertThat(enBundle.lists().get(MessageKey.ITEM_LORE))
                .containsExactly(
                        "<!italic><gray>Line one</gray>",
                        "<!italic><gray>Line two</gray>",
                        "<!italic><gray>Line three</gray>"
                );
        assertThat(langDir.resolve("en_US.yml.bak")).doesNotExist();
        assertThat(Files.readString(enFile, StandardCharsets.UTF_8)).isEqualTo(customized);

        // Also verify when a schema sync actually runs (extra obsolete key), custom 3-line list is preserved
        // and the subsequent startup load does NOT rewrite or create a new backup.
        final String withObsolete = customized + "\nobsolete-custom-list-test: \"remove\"\n";
        Files.writeString(enFile, withObsolete, StandardCharsets.UTF_8);

        final var afterSync = LanguageLoader.loadDirectory(langDir, mock(Logger.class), true);
        final var syncedEnBundle = java.util.Objects.requireNonNull(afterSync.get(Locale.US));
        assertThat(syncedEnBundle.lists().get(MessageKey.ITEM_LORE))
                .containsExactly(
                        "<!italic><gray>Line one</gray>",
                        "<!italic><gray>Line two</gray>",
                        "<!italic><gray>Line three</gray>"
                );
        assertThat(langDir.resolve("en_US.yml.bak")).exists();

        Files.writeString(langDir.resolve("en_US.yml.bak"), "sentinel", StandardCharsets.UTF_8);
        LanguageLoader.loadDirectory(langDir, mock(Logger.class), true);
        assertThat(Files.readString(langDir.resolve("en_US.yml.bak"), StandardCharsets.UTF_8)).isEqualTo("sentinel");
    }
}
