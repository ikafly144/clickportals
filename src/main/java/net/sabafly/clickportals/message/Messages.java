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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.message.LanguageLoader.TranslationBundle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Internationalized Adventure {@link Component} service backed by YAML + MiniMessage bundles (#13, #18, #19).
 */
public final class Messages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private record State(
            Map<Locale, TranslationBundle> bundles,
            Locale defaultLocale,
            boolean useClientLocale
    ) {
    }

    private volatile State state;

    /**
     * Creates a new {@link Messages} service with the given translation bundles and locale settings.
     */
    public Messages(
            final Map<Locale, TranslationBundle> bundles,
            final Locale defaultLocale,
            final boolean useClientLocale
    ) {
        this.state = new State(
                Map.copyOf(bundles),
                LanguageLoader.normalizeLocale(defaultLocale),
                useClientLocale
        );
    }

    /**
     * Creates a {@link Messages} instance loaded from the built-in classpath bundles with {@code en_US} as default.
     */
    public static Messages defaults() {
        return defaults(Locale.US, true);
    }

    /**
     * Creates a {@link Messages} instance loaded from the built-in classpath bundles with the given settings.
     */
    public static Messages defaults(final Locale defaultLocale, final boolean useClientLocale) {
        return new Messages(LanguageLoader.loadBundledOnly(null), defaultLocale, useClientLocale);
    }

    /**
     * Loads translations from {@code langDir} (copying bundled files if missing) and creates a {@link Messages} instance.
     */
    public static Messages load(
            final Path langDir,
            final Locale defaultLocale,
            final boolean useClientLocale,
            final Logger logger
    ) {
        final Map<Locale, TranslationBundle> bundles = LanguageLoader.loadDirectory(langDir, logger);
        return new Messages(bundles, defaultLocale, useClientLocale);
    }

    /**
     * Reloads translation bundles and locale settings atomically from {@code langDir}.
     */
    public void reload(
            final Path langDir,
            final Locale defaultLocale,
            final boolean useClientLocale,
            final Logger logger
    ) {
        final Map<Locale, TranslationBundle> bundles = LanguageLoader.loadDirectory(langDir, logger);
        this.state = new State(
                Map.copyOf(bundles),
                LanguageLoader.normalizeLocale(defaultLocale),
                useClientLocale
        );
    }

    /**
     * Returns the configured default server {@link Locale}.
     */
    public Locale defaultLocale() {
        return state.defaultLocale();
    }

    /**
     * Returns whether client locale detection is enabled.
     */
    public boolean useClientLocale() {
        return state.useClientLocale();
    }

    /**
     * Resolves the effective {@link Locale} for a {@link CommandSender}, honoring {@code use-client-locale}.
     */
    public Locale locale(final CommandSender viewer) {
        if (viewer instanceof Player player) {
            return resolveLocale(player.locale());
        }
        return state.defaultLocale();
    }

    /**
     * Resolves the effective {@link Locale} for a player's client locale when available.
     */
    public Locale resolveLocale(@Nullable final Locale clientLocale) {
        final State current = this.state;
        if (current.useClientLocale() && clientLocale != null) {
            return LanguageLoader.normalizeLocale(clientLocale);
        }
        return current.defaultLocale();
    }

    /**
     * Renders a single-line {@link MessageKey} for the given {@link Locale}.
     */
    public Component get(final Locale locale, final MessageKey key, final TagResolver... resolvers) {
        final State current = this.state;
        final TranslationBundle bundle = resolveBundle(current, locale);
        final String template = lookupString(current, bundle, key);
        if (key == MessageKey.PREFIX) {
            return MINI_MESSAGE.deserialize(template, resolvers);
        }
        final String prefixTemplate = lookupString(current, bundle, MessageKey.PREFIX);
        final Component prefixComponent = MINI_MESSAGE.deserialize(prefixTemplate);
        final TagResolver combined = TagResolver.builder()
                .resolver(Placeholder.component("prefix", prefixComponent))
                .resolvers(resolvers)
                .build();
        return MINI_MESSAGE.deserialize(template, combined);
    }

    /**
     * Renders a single-line {@link MessageKey} for the given {@link CommandSender}.
     */
    public Component get(final CommandSender viewer, final MessageKey key, final TagResolver... resolvers) {
        return get(locale(viewer), key, resolvers);
    }

    /**
     * Renders a multi-line list {@link MessageKey} for the given {@link Locale}.
     */
    public List<Component> getList(final Locale locale, final MessageKey key, final TagResolver... resolvers) {
        final State current = this.state;
        final TranslationBundle bundle = resolveBundle(current, locale);
        final List<String> templates = lookupList(current, bundle, key);
        final String prefixTemplate = lookupString(current, bundle, MessageKey.PREFIX);
        final Component prefixComponent = MINI_MESSAGE.deserialize(prefixTemplate);
        final TagResolver combined = TagResolver.builder()
                .resolver(Placeholder.component("prefix", prefixComponent))
                .resolvers(resolvers)
                .build();
        final List<Component> result = new ArrayList<>(templates.size());
        for (final String line : templates) {
            result.add(MINI_MESSAGE.deserialize(line, combined));
        }
        return List.copyOf(result);
    }

    /**
     * Sends a localized message for {@code key} directly to {@code viewer}.
     */
    public void send(final CommandSender viewer, final MessageKey key, final TagResolver... resolvers) {
        viewer.sendMessage(get(viewer, key, resolvers));
    }

    /**
     * Resolves the best-matching {@link TranslationBundle} for {@code requested} using the 4-step fallback chain.
     */
    private static TranslationBundle resolveBundle(final State current, final Locale requested) {
        final Locale normalized = LanguageLoader.normalizeLocale(requested);
        final Map<Locale, TranslationBundle> bundles = current.bundles();

        // 1. Exact match (language + country)
        final TranslationBundle exact = bundles.get(normalized);
        if (exact != null) {
            return exact;
        }

        // 2. Language-only match
        final TranslationBundle byLang = findByLanguage(bundles, normalized.getLanguage());
        if (byLang != null) {
            return byLang;
        }

        // 3. Configured default locale (exact, then language)
        final Locale def = current.defaultLocale();
        final TranslationBundle defExact = bundles.get(def);
        if (defExact != null) {
            return defExact;
        }
        final TranslationBundle defLang = findByLanguage(bundles, def.getLanguage());
        if (defLang != null) {
            return defLang;
        }

        // 4. Built-in en_US fallback
        final TranslationBundle us = bundles.get(Locale.US);
        return us != null ? us : TranslationBundle.empty();
    }

    /**
     * Finds a {@link TranslationBundle} whose locale shares the given language code, selecting the lexicographically
     * smallest {@link Locale#toString()} for deterministic behavior across JVM restarts.
     */
    private static @Nullable TranslationBundle findByLanguage(
            final Map<Locale, TranslationBundle> bundles,
            final String language
    ) {
        if (language.isEmpty()) {
            return null;
        }
        final TranslationBundle langOnly = bundles.get(Locale.of(language));
        if (langOnly != null) {
            return langOnly;
        }
        return bundles.entrySet().stream()
                .filter(entry -> entry.getKey().getLanguage().equals(language))
                .min(Map.Entry.comparingByKey(Comparator.comparing(Locale::toString)))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    /**
     * Looks up a single-line template string for {@code key}, falling back to the default locale and {@code en_US}.
     */
    private static String lookupString(
            final State current,
            final TranslationBundle bundle,
            final MessageKey key
    ) {
        final String direct = bundle.strings().get(key);
        if (direct != null) {
            return direct;
        }
        final TranslationBundle def = current.bundles().get(current.defaultLocale());
        if (def != null && def.strings().containsKey(key)) {
            return def.strings().get(key);
        }
        final TranslationBundle us = current.bundles().get(Locale.US);
        if (us != null && us.strings().containsKey(key)) {
            return us.strings().get(key);
        }
        return key.path();
    }

    /**
     * Looks up a multi-line template list for {@code key}, falling back to the default locale and {@code en_US}.
     */
    private static List<String> lookupList(
            final State current,
            final TranslationBundle bundle,
            final MessageKey key
    ) {
        final List<String> direct = bundle.lists().get(key);
        if (direct != null) {
            return direct;
        }
        final TranslationBundle def = current.bundles().get(current.defaultLocale());
        if (def != null && def.lists().containsKey(key)) {
            return def.lists().get(key);
        }
        final TranslationBundle us = current.bundles().get(Locale.US);
        if (us != null && us.lists().containsKey(key)) {
            return us.lists().get(key);
        }
        return List.of(key.path());
    }

    // --- Convenience Formatting Methods ---

    /**
     * Returns the display name component for the Aetheryte creation item in the default server locale.
     */
    public Component aetheryteItemName() {
        return get(defaultLocale(), MessageKey.ITEM_NAME);
    }

    /**
     * Returns the lore lines for the Aetheryte creation item in the default server locale.
     */
    public List<Component> aetheryteItemLore() {
        return getList(defaultLocale(), MessageKey.ITEM_LORE);
    }

    /**
     * Formats the hologram text displayed above a placed Aetheryte for the given viewer.
     */
    public Component hologramTitle(final Player viewer, final String aetheryteName) {
        return hologramTitle(locale(viewer), aetheryteName);
    }

    /**
     * Formats the hologram text displayed above a placed Aetheryte for the given locale.
     */
    public Component hologramTitle(final Locale locale, final String aetheryteName) {
        return get(locale, MessageKey.HOLOGRAM_TITLE, Placeholder.unparsed("name", aetheryteName));
    }

    /**
     * Returns the free cost label for the given viewer.
     */
    public Component freeCostLabel(final CommandSender viewer) {
        return get(viewer, MessageKey.COST_FREE);
    }

    /**
     * Returns the title for the Aetheryte creation dialog.
     */
    public Component createDialogTitle(final CommandSender viewer) {
        return get(viewer, MessageKey.DIALOG_CREATE_TITLE);
    }

    /**
     * Returns the description body for the Aetheryte creation dialog showing target coordinates.
     */
    public Component createDialogDescription(
            final CommandSender viewer,
            final String worldName,
            final int x,
            final int y,
            final int z
    ) {
        return get(
                viewer,
                MessageKey.DIALOG_CREATE_DESCRIPTION,
                Placeholder.unparsed("world", worldName),
                Placeholder.unparsed("x", Integer.toString(x)),
                Placeholder.unparsed("y", Integer.toString(y)),
                Placeholder.unparsed("z", Integer.toString(z))
        );
    }

    /**
     * Returns the label for the Aetheryte name input field.
     */
    public Component createDialogNameInputLabel(final CommandSender viewer) {
        return get(viewer, MessageKey.DIALOG_CREATE_NAME_INPUT_LABEL);
    }

    /**
     * Returns the initial default Aetheryte name string for the creation dialog input field.
     */
    public String createDialogDefaultName(final Player player) {
        final State current = this.state;
        final TranslationBundle bundle = resolveBundle(current, locale(player));
        final String template = lookupString(current, bundle, MessageKey.DIALOG_CREATE_DEFAULT_NAME);
        return template.replace("<player>", player.getName());
    }

    /**
     * Returns the label for the confirm placement button.
     */
    public Component buttonConfirmCreate(final CommandSender viewer) {
        return get(viewer, MessageKey.DIALOG_BUTTON_CONFIRM_CREATE);
    }

    /**
     * Returns the label for generic cancel buttons.
     */
    public Component buttonCancel(final CommandSender viewer) {
        return get(viewer, MessageKey.DIALOG_BUTTON_CANCEL);
    }

    /**
     * Returns the label for generic close buttons.
     */
    public Component buttonClose(final CommandSender viewer) {
        return get(viewer, MessageKey.DIALOG_BUTTON_CLOSE);
    }

    /**
     * Returns the title for the warp destination selection dialog.
     */
    public Component warpDialogTitle(final CommandSender viewer, final Aetheryte current) {
        return get(viewer, MessageKey.DIALOG_WARP_TITLE, Placeholder.unparsed("name", current.name()));
    }

    /**
     * Returns the body message for the warp destination selection dialog.
     */
    public Component warpDialogBody(final CommandSender viewer, final Aetheryte current, final int destinationCount) {
        if (destinationCount == 0) {
            return get(viewer, MessageKey.DIALOG_WARP_BODY_EMPTY, Placeholder.unparsed("name", current.name()));
        }
        return get(viewer, MessageKey.DIALOG_WARP_BODY_SELECT, Placeholder.unparsed("name", current.name()));
    }

    /**
     * Returns the button label for an available warp destination entry showing its name and cost (#13).
     */
    public Component warpDestinationLabel(
            final CommandSender viewer,
            final Aetheryte destination,
            final Component costDisplay
    ) {
        return get(
                viewer,
                MessageKey.DIALOG_WARP_DESTINATION_AVAILABLE,
                Placeholder.unparsed("name", destination.name()),
                Placeholder.component("cost", costDisplay)
        );
    }

    /**
     * Returns the button label for an unavailable warp destination entry (#13).
     */
    public Component warpDestinationUnavailableLabel(
            final CommandSender viewer,
            final Aetheryte destination,
            final Component costDisplay
    ) {
        return get(
                viewer,
                MessageKey.DIALOG_WARP_DESTINATION_UNAVAILABLE,
                Placeholder.unparsed("name", destination.name()),
                Placeholder.component("cost", costDisplay)
        );
    }

    /**
     * Returns the button tooltip for a warp destination entry showing world, coordinates, distance, cost, and status (#13).
     */
    public Component warpDestinationTooltip(
            final CommandSender viewer,
            final Aetheryte current,
            final Aetheryte destination,
            final Component costDisplay,
            final boolean allowed,
            final @Nullable Component denyReason
    ) {
        final Locale loc = locale(viewer);
        final OptionalDouble distance = current.distanceTo(destination);
        final Component distanceText = distance.isPresent()
                ? get(
                        loc,
                        MessageKey.DIALOG_WARP_DISTANCE_BLOCKS,
                        Placeholder.unparsed("blocks", Long.toString(Math.round(distance.getAsDouble())))
                )
                : get(loc, MessageKey.DIALOG_WARP_DISTANCE_OTHER_DIMENSION);

        if (allowed) {
            return get(
                    loc,
                    MessageKey.DIALOG_WARP_TOOLTIP_ALLOWED,
                    Placeholder.unparsed("world", destination.worldName()),
                    Placeholder.unparsed("x", Integer.toString(destination.blockX())),
                    Placeholder.unparsed("y", Integer.toString(destination.blockY())),
                    Placeholder.unparsed("z", Integer.toString(destination.blockZ())),
                    Placeholder.component("distance", distanceText),
                    Placeholder.component("cost", costDisplay)
            );
        }

        final Component effectiveReason = denyReason != null
                ? denyReason
                : get(loc, MessageKey.ERROR_WARP_REQUIREMENTS_NOT_MET);
        return get(
                loc,
                MessageKey.DIALOG_WARP_TOOLTIP_DENIED,
                Placeholder.unparsed("world", destination.worldName()),
                Placeholder.unparsed("x", Integer.toString(destination.blockX())),
                Placeholder.unparsed("y", Integer.toString(destination.blockY())),
                Placeholder.unparsed("z", Integer.toString(destination.blockZ())),
                Placeholder.component("distance", distanceText),
                Placeholder.component("cost", costDisplay),
                Placeholder.component("reason", effectiveReason)
        );
    }

    /**
     * Returns the label for the button that opens the Aetheryte removal confirmation dialog.
     */
    public Component buttonOpenRemoveConfirm(final CommandSender viewer) {
        return get(viewer, MessageKey.DIALOG_BUTTON_OPEN_REMOVE_CONFIRM);
    }

    /**
     * Returns the tooltip for the button that opens the Aetheryte removal confirmation dialog.
     */
    public Component buttonOpenRemoveConfirmTooltip(final CommandSender viewer) {
        return get(viewer, MessageKey.DIALOG_BUTTON_OPEN_REMOVE_CONFIRM_TOOLTIP);
    }

    /**
     * Returns the title for the Aetheryte removal confirmation dialog.
     */
    public Component removeConfirmDialogTitle(final CommandSender viewer, final Aetheryte aetheryte) {
        return get(viewer, MessageKey.DIALOG_REMOVE_TITLE, Placeholder.unparsed("name", aetheryte.name()));
    }

    /**
     * Returns the body message for the Aetheryte removal confirmation dialog.
     */
    public Component removeConfirmDialogBody(final CommandSender viewer, final Aetheryte aetheryte) {
        return get(viewer, MessageKey.DIALOG_REMOVE_BODY, Placeholder.unparsed("name", aetheryte.name()));
    }

    /**
     * Returns the label for the final confirm removal button.
     */
    public Component buttonConfirmRemove(final CommandSender viewer) {
        return get(viewer, MessageKey.DIALOG_BUTTON_CONFIRM_REMOVE);
    }

    /**
     * Returns the notification message sent when an Aetheryte is placed.
     */
    public Component aetheryteCreated(final CommandSender viewer, final Aetheryte aetheryte) {
        return get(viewer, MessageKey.AETHERYTE_CREATED, Placeholder.unparsed("name", aetheryte.name()));
    }

    /**
     * Returns the notification message sent when an Aetheryte is removed.
     */
    public Component aetheryteRemoved(final CommandSender viewer, final String name) {
        return get(viewer, MessageKey.AETHERYTE_REMOVED, Placeholder.unparsed("name", name));
    }

    /**
     * Returns the notification message sent when a player warps to an Aetheryte.
     */
    public Component warpedTo(final CommandSender viewer, final Aetheryte destination) {
        return get(viewer, MessageKey.WARP_SUCCESS, Placeholder.unparsed("name", destination.name()));
    }

    /**
     * Returns the error message when a player cannot warp due to a specific reason.
     */
    public Component errorWarpDenied(final CommandSender viewer, final Component reason) {
        return get(viewer, MessageKey.ERROR_WARP_DENIED, Placeholder.component("reason", reason));
    }

    /**
     * Returns the confirmation message when giving Aetheryte items via command.
     */
    public Component commandGivenItem(final CommandSender viewer, final String playerName, final int amount) {
        return get(
                viewer,
                MessageKey.COMMAND_GIVEN_ITEM,
                Placeholder.unparsed("player", playerName),
                Placeholder.unparsed("amount", Integer.toString(amount))
        );
    }
}
