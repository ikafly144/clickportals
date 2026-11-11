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

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.sabafly.clickportals.message.LanguageLoader.TranslationBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;

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
}
