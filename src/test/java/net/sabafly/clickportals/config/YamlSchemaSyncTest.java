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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;

class YamlSchemaSyncTest {

    @TempDir
    Path tempDir;

    @Test
    void hasSchemaDiffDetectsMissingExtraListAndTypeChangesWhileIgnoringScalarEdits() throws IOException {
        final CommentedConfigurationNode target = CommentedConfigurationNode.root();
        target.node("section", "key-a").set("default");
        target.node("section", "key-b").set(42);
        target.node("items").setList(String.class, List.of("one", "two"));

        // Identical structure with different scalar values -> no diff
        final CommentedConfigurationNode sameStructure = CommentedConfigurationNode.root();
        sameStructure.node("section", "key-a").set("user-edited");
        sameStructure.node("section", "key-b").set(999);
        sameStructure.node("items").setList(String.class, List.of("alpha", "beta"));
        assertThat(YamlSchemaSync.hasSchemaDiff(sameStructure, target)).isFalse();

        // Missing key -> diff
        final CommentedConfigurationNode missingKey = CommentedConfigurationNode.root();
        missingKey.node("section", "key-a").set("user-edited");
        missingKey.node("items").setList(String.class, List.of("one", "two"));
        assertThat(YamlSchemaSync.hasSchemaDiff(missingKey, target)).isTrue();

        // Extra / obsolete key -> diff
        final CommentedConfigurationNode extraKey = sameStructure.copy();
        extraKey.node("section", "obsolete-key").set("old");
        assertThat(YamlSchemaSync.hasSchemaDiff(extraKey, target)).isTrue();

        // List size mismatch -> diff
        final CommentedConfigurationNode listDiff = sameStructure.copy();
        listDiff.node("items").setList(String.class, List.of("only-one"));
        assertThat(YamlSchemaSync.hasSchemaDiff(listDiff, target)).isTrue();

        // Type mismatch (map vs scalar) -> diff
        final CommentedConfigurationNode typeMismatch = sameStructure.copy();
        typeMismatch.node("section", "key-a", "nested").set("value");
        assertThat(YamlSchemaSync.hasSchemaDiff(typeMismatch, target)).isTrue();
    }

    @Test
    void renderInlineCommentsHandlesQuotedKeysAndColonsInValues() throws IOException {
        final CommentedConfigurationNode root = CommentedConfigurationNode.root();
        root.node("recipe").comment("Crafting recipe");
        root.node("recipe", "ingredients").comment("Ingredient map");
        root.node("recipe", "ingredients", "S").comment("Stone brick");
        root.node("url").comment("Reference link");
        root.node("url").set("https://example.com:8080/path");

        final String dumped = """
                recipe:
                  ingredients:
                    'S': STONE
                url: https://example.com:8080/path
                """;

        final String rendered = YamlSchemaSync.renderInlineComments(dumped, root, 2);
        assertThat(rendered)
                .contains("# Crafting recipe\nrecipe:")
                .contains("  # Ingredient map\n  ingredients:")
                .contains("    # Stone brick\n    'S': STONE")
                .contains("# Reference link\nurl: https://example.com:8080/path");
    }

    @Test
    void backupFileCreatesAndUpdatesDotBakCopy() throws IOException {
        final Path file = tempDir.resolve("config.yml");
        Files.writeString(file, "version: 1\n", StandardCharsets.UTF_8);

        assertThat(YamlSchemaSync.backupFile(file, mock(Logger.class))).isTrue();
        final Path backup = tempDir.resolve("config.yml.bak");
        assertThat(backup).exists().hasContent("version: 1\n");

        Files.writeString(file, "version: 2\n", StandardCharsets.UTF_8);
        assertThat(YamlSchemaSync.backupFile(file, mock(Logger.class))).isTrue();
        assertThat(backup).hasContent("version: 2\n");
    }

    @Test
    void addTopLevelSectionSpacingAddsBlankLinesBetweenSectionsOnly() {
        final String raw = """
                # Header
                prefix: "p"
                item:
                  name: "n"
                error:
                  bad: "b"
                """;

        final String spaced = YamlSchemaSync.addTopLevelSectionSpacing(raw);
        assertThat(spaced).contains("""
                # Header
                prefix: "p"

                item:
                  name: "n"

                error:
                  bad: "b"
                """);
    }
}
