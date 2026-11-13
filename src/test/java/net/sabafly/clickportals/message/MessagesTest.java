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
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

class MessagesTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @TempDir
    Path tempDir;

    @Test
    void switchesBetweenEnglishAndJapaneseBasedOnClientLocale() {
        final Messages messages = Messages.defaults(Locale.US, true);

        final Player enPlayer = mock(Player.class);
        when(enPlayer.locale()).thenReturn(Locale.US);

        final Player jaPlayer = mock(Player.class);
        when(jaPlayer.locale()).thenReturn(Locale.JAPAN);

        assertThat(PLAIN.serialize(messages.freeCostLabel(enPlayer))).isEqualTo("Free");
        assertThat(PLAIN.serialize(messages.freeCostLabel(jaPlayer))).isEqualTo("無料");

        assertThat(PLAIN.serialize(messages.get(enPlayer, MessageKey.ERROR_NO_PERMISSION)))
                .isEqualTo("[ClickPortals] You do not have permission to perform this action.");
        assertThat(PLAIN.serialize(messages.get(jaPlayer, MessageKey.ERROR_NO_PERMISSION)))
                .isEqualTo("[ClickPortals] この操作を行う権限がありません。");
    }

    @Test
    void fallsBackThroughLanguageOnlyToDefaultLocaleAndEnUs() {
        final Messages messages = Messages.defaults(Locale.JAPAN, true);

        // ja_CA is not explicitly registered, should match ja_JP via language "ja"
        assertThat(PLAIN.serialize(messages.get(Locale.of("ja", "CA"), MessageKey.COST_FREE)))
                .isEqualTo("無料");

        // fr_FR is not registered, should fall back to configured default locale (ja_JP)
        assertThat(PLAIN.serialize(messages.get(Locale.FRANCE, MessageKey.COST_FREE)))
                .isEqualTo("無料");

        // When useClientLocale is false, even an en_US player gets the configured default locale (ja_JP)
        final Messages fixedLocaleMessages = Messages.defaults(Locale.JAPAN, false);
        final Player enPlayer = mock(Player.class);
        when(enPlayer.locale()).thenReturn(Locale.US);
        assertThat(PLAIN.serialize(fixedLocaleMessages.freeCostLabel(enPlayer))).isEqualTo("無料");

        // Console always uses defaultLocale
        final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        assertThat(PLAIN.serialize(messages.freeCostLabel(console))).isEqualTo("無料");
    }

    @Test
    void userOverridesMergeWithBundledDefaultsAndPreserveMissingKeys() throws IOException {
        final Path langDir = tempDir.resolve("lang");
        Files.createDirectories(langDir);
        Files.writeString(langDir.resolve("ja_JP.yml"), """
                cost:
                  free: "<gold>タダ！</gold>"
                """, StandardCharsets.UTF_8);
        Files.writeString(langDir.resolve("de_DE.yml"), """
                cost:
                  free: "<green>Kostenlos</green>"
                """, StandardCharsets.UTF_8);

        final Messages messages = Messages.load(langDir, Locale.US, true, mock(Logger.class));

        // Overridden key in ja_JP
        assertThat(PLAIN.serialize(messages.get(Locale.JAPAN, MessageKey.COST_FREE))).isEqualTo("タダ！");
        // Missing key in user ja_JP.yml is filled from bundled ja_JP.yml
        assertThat(PLAIN.serialize(messages.get(Locale.JAPAN, MessageKey.DIALOG_BUTTON_CANCEL))).isEqualTo("キャンセル");

        // Custom locale de_DE uses its override and falls back to en_US for unspecified keys
        assertThat(PLAIN.serialize(messages.get(Locale.GERMANY, MessageKey.COST_FREE))).isEqualTo("Kostenlos");
        assertThat(PLAIN.serialize(messages.get(Locale.GERMANY, MessageKey.DIALOG_BUTTON_CANCEL))).isEqualTo("Cancel");
    }

    @Test
    void unparsedPlaceholdersPreventMiniMessageTagInjection() {
        final Messages messages = Messages.defaults(Locale.US, true);
        final Aetheryte malicious = new Aetheryte(
                UUID.randomUUID(),
                "<red><bold>Injected</bold></red>",
                UUID.randomUUID(),
                "world",
                0,
                64,
                0,
                UUID.randomUUID(),
                1000L
        );

        final Component rendered = messages.hologramTitle(Locale.US, malicious.name());
        assertThat(PLAIN.serialize(rendered)).isEqualTo("✦ <red><bold>Injected</bold></red> ✦");
    }

    @Test
    void itemLoreReturnsListInDefaultLocale() {
        final Messages jaMessages = Messages.defaults(Locale.JAPAN, true);
        final List<Component> lore = jaMessages.aetheryteItemLore();
        assertThat(lore).hasSize(2);
        assertThat(PLAIN.serialize(lore.getFirst())).contains("ブロックに向かって右クリックして");
    }

    @Test
    void languageOnlyFallbackIsDeterministicByLocaleStringOrder() throws IOException {
        final Path langDir = tempDir.resolve("lang-order");
        Files.createDirectories(langDir);
        Files.writeString(langDir.resolve("en_GB.yml"), """
                cost:
                  free: "<green>Free (UK)</green>"
                """, StandardCharsets.UTF_8);
        Files.writeString(langDir.resolve("en_US.yml"), """
                cost:
                  free: "<green>Free (US)</green>"
                """, StandardCharsets.UTF_8);

        final Messages messages = Messages.load(langDir, Locale.JAPAN, true, mock(Logger.class));

        // Requesting en_AU (unregistered region of "en") must deterministically choose en_GB ("en_GB" < "en_US")
        assertThat(PLAIN.serialize(messages.get(Locale.of("en", "AU"), MessageKey.COST_FREE)))
                .isEqualTo("Free (UK)");
    }

    @Test
    void arbitraryUnregisteredLocalesResolveFallbackWithoutGrowingStateMaps() throws Exception {
        final Messages messages = Messages.defaults(Locale.JAPAN, true);
        final java.lang.reflect.Field stateField = Messages.class.getDeclaredField("state");
        stateField.setAccessible(true);
        final Object state = stateField.get(messages);
        final java.util.Map<?, ?> cachedBundles = (java.util.Map<?, ?>) state.getClass().getDeclaredMethod("cachedBundles").invoke(state);
        final int initialBundleCount = cachedBundles.size();

        for (int i = 0; i < 500; i++) {
            final Locale unknownLang = Locale.of("q" + (char) ('a' + (i % 20)), String.format(Locale.ROOT, "%03d", i));
            assertThat(PLAIN.serialize(messages.get(unknownLang, MessageKey.COST_FREE))).isEqualTo("無料");
            final Locale unknownJaCountry = Locale.of("ja", String.format(Locale.ROOT, "%03d", i));
            assertThat(PLAIN.serialize(messages.get(unknownJaCountry, MessageKey.COST_FREE))).isEqualTo("無料");
        }

        // State maps must remain strictly bounded to the registered locales
        for (final java.lang.reflect.RecordComponent component : state.getClass().getRecordComponents()) {
            final Object value = component.getAccessor().invoke(state);
            if (value instanceof java.util.Map<?, ?> map) {
                assertThat(map.size()).isLessThanOrEqualTo(initialBundleCount);
            }
        }
    }

    @Test
    void warpMessagesIncludeOwnerInBodyLabelAndTooltip() {
        final Messages messages = Messages.defaults(Locale.JAPAN, true);
        final Player viewer = mock(Player.class);
        when(viewer.locale()).thenReturn(Locale.JAPAN);

        final UUID worldId = UUID.randomUUID();
        final Aetheryte current = new Aetheryte(UUID.randomUUID(), "現在地", worldId, "world", 0, 64, 0, UUID.randomUUID(), 100L, "default", "Alice");
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "目的地", worldId, "world", 30, 64, 40, UUID.randomUUID(), 200L, "default", "Bob");

        assertThat(PLAIN.serialize(messages.warpDialogHeader(viewer, current, "Alice")))
                .contains("現在地")
                .contains("Alice")
                .contains("world (0, 64, 0)");
        assertThat(PLAIN.serialize(messages.warpRootBody(viewer, current, "Alice")))
                .doesNotContain("現在地");
        assertThat(PLAIN.serialize(messages.warpDialogBody(viewer, current, "Alice", 0)))
                .doesNotContain("現在地");
        assertThat(PLAIN.serialize(messages.warpDialogBody(viewer, current, "Alice", 1)))
                .doesNotContain("現在地");
        assertThat(PLAIN.serialize(messages.get(viewer, MessageKey.DIALOG_WARP_BODY_NO_ROUTE)))
                .doesNotContain("現在地");
        assertThat(PLAIN.serialize(messages.warpDestinationLabel(viewer, dest, "Bob", messages.freeCostLabel(viewer))))
                .contains("目的地")
                .contains("Bob");
        assertThat(PLAIN.serialize(messages.warpDestinationTooltip(viewer, current, dest, "Bob", messages.freeCostLabel(viewer), true, null)))
                .contains("Bob")
                .contains("world");
    }
}
