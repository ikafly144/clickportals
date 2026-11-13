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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.dialog.WarpSortOrder;
import net.sabafly.clickportals.message.LanguageLoader.TranslationBundle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Internationalized Adventure {@link Component} service backed by YAML + MiniMessage bundles (#13, #18, #19).
 */
public final class Messages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final char SENTINEL_START = '\uE000';
    private static final char SENTINEL_END = '\uE001';
    private static final List<String> ARG_NAME_OWNER_COST = List.of("name", "owner", "cost");
    private static final List<String> ARG_AMOUNT = List.of("amount");

    private record TemplateCacheKey(MessageKey key, List<String> argNames) {
    }

    private sealed interface CompiledNode permits StaticNode, SlotNode, GroupNode {
        Component render(Component[] args);
    }

    private record StaticNode(Component component) implements CompiledNode {
        @Override
        public Component render(final Component[] args) {
            return component;
        }
    }

    private record SlotNode(int index, Style style) implements CompiledNode {
        @Override
        public Component render(final Component[] args) {
            final Component value = args[index];
            if (style.isEmpty()) {
                return value;
            }
            if (value instanceof TextComponent text && text.style().isEmpty() && text.children().isEmpty()) {
                return Component.text(text.content(), style);
            }
            return Component.text("", style).append(value);
        }
    }

    private record GroupNode(Component shellWithoutChildren, List<CompiledNode> children) implements CompiledNode {
        @Override
        public Component render(final Component[] args) {
            final List<Component> rendered = new ArrayList<>(children.size());
            for (final CompiledNode child : children) {
                rendered.add(child.render(args));
            }
            return shellWithoutChildren.children(rendered);
        }
    }

    private record CompiledTemplate(@Nullable CompiledNode root) {
        static final CompiledTemplate UNSUPPORTED = new CompiledTemplate(null);
    }

    private record CachedBundle(
            TranslationBundle bundle,
            Component prefixComponent,
            TagResolver prefixResolver,
            Map<MessageKey, Component> staticCache,
            Map<TemplateCacheKey, CompiledTemplate> compiledTemplates
    ) {
    }

    private record State(
            Map<Locale, TranslationBundle> bundles,
            Map<Locale, CachedBundle> cachedBundles,
            Map<String, CachedBundle> cachedByLanguage,
            CachedBundle defaultFallback,
            Locale defaultLocale,
            boolean useClientLocale
    ) {
        static State create(
                final Map<Locale, TranslationBundle> rawBundles,
                final Locale rawDefaultLocale,
                final boolean useClientLocale
        ) {
            final Map<Locale, TranslationBundle> bundles = Map.copyOf(rawBundles);
            final Locale defaultLocale = LanguageLoader.normalizeLocale(rawDefaultLocale);
            final Map<Locale, CachedBundle> cached = new HashMap<>(bundles.size() * 2);
            for (final Map.Entry<Locale, TranslationBundle> entry : bundles.entrySet()) {
                cached.put(entry.getKey(), buildCachedBundle(bundles, defaultLocale, entry.getValue()));
            }
            final Map<Locale, CachedBundle> immutableCached = Map.copyOf(cached);
            final Map<String, CachedBundle> byLanguage = new HashMap<>();
            for (final Locale locale : immutableCached.keySet()) {
                final String language = locale.getLanguage();
                if (language.isEmpty() || byLanguage.containsKey(language)) {
                    continue;
                }
                final CachedBundle resolved = findByLanguage(immutableCached, language);
                if (resolved != null) {
                    byLanguage.put(language, resolved);
                }
            }
            final Map<String, CachedBundle> immutableByLanguage = Map.copyOf(byLanguage);
            final CachedBundle emptyFallback = buildCachedBundle(bundles, defaultLocale, TranslationBundle.empty());
            CachedBundle defaultFallback = immutableCached.get(defaultLocale);
            if (defaultFallback == null) {
                defaultFallback = immutableByLanguage.get(defaultLocale.getLanguage());
            }
            if (defaultFallback == null) {
                defaultFallback = immutableCached.get(Locale.US);
            }
            if (defaultFallback == null) {
                defaultFallback = emptyFallback;
            }
            return new State(
                    bundles,
                    immutableCached,
                    immutableByLanguage,
                    defaultFallback,
                    defaultLocale,
                    useClientLocale
            );
        }

        private static CachedBundle buildCachedBundle(
                final Map<Locale, TranslationBundle> bundles,
                final Locale defaultLocale,
                final TranslationBundle bundle
        ) {
            final String prefixTemplate = lookupString(bundles, defaultLocale, bundle, MessageKey.PREFIX);
            final Component prefixComponent = MINI_MESSAGE.deserialize(prefixTemplate);
            final TagResolver prefixResolver = Placeholder.component("prefix", prefixComponent);
            return new CachedBundle(
                    bundle,
                    prefixComponent,
                    prefixResolver,
                    new ConcurrentHashMap<>(),
                    new ConcurrentHashMap<>()
            );
        }
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
        this.state = State.create(bundles, defaultLocale, useClientLocale);
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
        this.state = State.create(bundles, defaultLocale, useClientLocale);
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
        final CachedBundle cached = resolveCachedBundle(current, locale);
        if (resolvers.length == 0) {
            if (key == MessageKey.PREFIX) {
                return cached.prefixComponent();
            }
            return cached.staticCache().computeIfAbsent(key, k -> renderSingle(current, cached, k, resolvers));
        }
        return renderSingle(current, cached, key, resolvers);
    }

    /**
     * Renders a single-line cost amount placeholder ({@code <amount>}) using a pre-compiled template cache (#20).
     */
    public Component renderAmountPlaceholder(
            final Locale locale,
            final MessageKey key,
            final String amount
    ) {
        return renderWithPlaceholders(locale, key, ARG_AMOUNT, Component.text(amount));
    }

    private Component renderWithPlaceholders(
            final Locale locale,
            final MessageKey key,
            final List<String> argNames,
            final Component... args
    ) {
        final State current = this.state;
        final CachedBundle cached = resolveCachedBundle(current, locale);
        final CompiledTemplate template = cached.compiledTemplates().computeIfAbsent(
                new TemplateCacheKey(key, argNames),
                cacheKey -> compileTemplate(current, cached, cacheKey)
        );
        final CompiledNode root = template.root();
        if (root != null) {
            return root.render(args);
        }
        final TagResolver[] resolvers = new TagResolver[argNames.size()];
        for (int i = 0; i < argNames.size(); i++) {
            resolvers[i] = Placeholder.component(argNames.get(i), args[i]);
        }
        return renderSingle(current, cached, key, resolvers);
    }

    private static CompiledTemplate compileTemplate(
            final State current,
            final CachedBundle cached,
            final TemplateCacheKey cacheKey
    ) {
        final String raw = lookupString(current, cached.bundle(), cacheKey.key());
        if (raw.indexOf(SENTINEL_START) >= 0 || raw.indexOf(SENTINEL_END) >= 0) {
            return CompiledTemplate.UNSUPPORTED;
        }
        final String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("<gradient")
                || lower.contains("<rainbow")
                || lower.contains("<transition")
                || lower.contains("<hover")
                || lower.contains("<click")
                || lower.contains("<insert")
                || lower.contains("<selector")) {
            return CompiledTemplate.UNSUPPORTED;
        }

        final TagResolver.Builder builder = TagResolver.builder().resolver(cached.prefixResolver());
        final List<String> argNames = cacheKey.argNames();
        for (int i = 0; i < argNames.size(); i++) {
            builder.resolver(Placeholder.unparsed(argNames.get(i), SENTINEL_START + Integer.toString(i) + SENTINEL_END));
        }
        final Component parsed = MINI_MESSAGE.deserialize(raw, builder.build());
        final @Nullable CompiledNode compiled = compileNode(parsed, argNames.size());
        return compiled != null ? new CompiledTemplate(compiled) : CompiledTemplate.UNSUPPORTED;
    }

    private static @Nullable CompiledNode compileNode(final Component component, final int argCount) {
        if (!containsSentinel(component)) {
            return new StaticNode(component);
        }
        if (!(component instanceof TextComponent text)) {
            return null;
        }
        final Style style = text.style();
        if (style.hoverEvent() != null || style.clickEvent() != null || style.insertion() != null) {
            return null;
        }

        final String content = text.content();
        final List<CompiledNode> children = new ArrayList<>();
        if (content.indexOf(SENTINEL_START) >= 0) {
            int pos = 0;
            final int len = content.length();
            while (pos < len) {
                final int start = content.indexOf(SENTINEL_START, pos);
                if (start < 0) {
                    children.add(new StaticNode(Component.text(content.substring(pos))));
                    break;
                }
                if (start > pos) {
                    children.add(new StaticNode(Component.text(content.substring(pos, start))));
                }
                final int end = content.indexOf(SENTINEL_END, start + 1);
                if (end < 0) {
                    return null;
                }
                final int slotIndex;
                try {
                    slotIndex = Integer.parseInt(content.substring(start + 1, end));
                } catch (final NumberFormatException e) {
                    return null;
                }
                if (slotIndex < 0 || slotIndex >= argCount) {
                    return null;
                }
                if (start == 0 && end == len - 1 && text.children().isEmpty()) {
                    return new SlotNode(slotIndex, style);
                }
                children.add(new SlotNode(slotIndex, Style.empty()));
                pos = end + 1;
            }
        } else if (!content.isEmpty()) {
            children.add(new StaticNode(Component.text(content)));
        }

        for (final Component child : text.children()) {
            final @Nullable CompiledNode compiledChild = compileNode(child, argCount);
            if (compiledChild == null) {
                return null;
            }
            children.add(compiledChild);
        }
        return new GroupNode(Component.text("", style), List.copyOf(children));
    }

    private static boolean containsSentinel(final Component component) {
        if (component instanceof TextComponent text
                && (text.content().indexOf(SENTINEL_START) >= 0 || text.content().indexOf(SENTINEL_END) >= 0)) {
            return true;
        }
        for (final Component child : component.children()) {
            if (containsSentinel(child)) {
                return true;
            }
        }
        return false;
    }

    private static Component renderSingle(
            final State current,
            final CachedBundle cached,
            final MessageKey key,
            final TagResolver... resolvers
    ) {
        final String template = lookupString(current, cached.bundle(), key);
        if (key == MessageKey.PREFIX) {
            return MINI_MESSAGE.deserialize(template, resolvers);
        }
        final TagResolver combined = switch (resolvers.length) {
            case 0 -> cached.prefixResolver();
            case 1 -> TagResolver.resolver(cached.prefixResolver(), resolvers[0]);
            default -> TagResolver.builder()
                    .resolver(cached.prefixResolver())
                    .resolvers(resolvers)
                    .build();
        };
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
        final CachedBundle cached = resolveCachedBundle(current, locale);
        final List<String> templates = lookupList(current, cached.bundle(), key);
        final TagResolver combined = switch (resolvers.length) {
            case 0 -> cached.prefixResolver();
            case 1 -> TagResolver.resolver(cached.prefixResolver(), resolvers[0]);
            default -> TagResolver.builder()
                    .resolver(cached.prefixResolver())
                    .resolvers(resolvers)
                    .build();
        };
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
     * Resolves the best-matching pre-cached {@link CachedBundle} for {@code requested} without retaining
     * arbitrary unregistered client locales.
     */
    private static CachedBundle resolveCachedBundle(final State current, final Locale requested) {
        final Locale normalized = LanguageLoader.normalizeLocale(requested);
        // 1. Exact match (language + country)
        final CachedBundle exact = current.cachedBundles().get(normalized);
        if (exact != null) {
            return exact;
        }
        // 2. Language-only match (precomputed across registered bundles)
        final String language = normalized.getLanguage();
        if (!language.isEmpty()) {
            final CachedBundle byLang = current.cachedByLanguage().get(language);
            if (byLang != null) {
                return byLang;
            }
        }
        // 3 & 4. Configured default locale -> en_US -> empty fallback (precomputed)
        return current.defaultFallback();
    }

    /**
     * Finds a {@link CachedBundle} whose locale shares the given language code, selecting the lexicographically
     * smallest {@link Locale#toString()} for deterministic behavior across JVM restarts.
     */
    private static @Nullable CachedBundle findByLanguage(
            final Map<Locale, CachedBundle> bundles,
            final String language
    ) {
        if (language.isEmpty()) {
            return null;
        }
        final CachedBundle langOnly = bundles.get(Locale.of(language));
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
        return lookupString(current.bundles(), current.defaultLocale(), bundle, key);
    }

    private static String lookupString(
            final Map<Locale, TranslationBundle> bundles,
            final Locale defaultLocale,
            final TranslationBundle bundle,
            final MessageKey key
    ) {
        final String direct = bundle.strings().get(key);
        if (direct != null) {
            return direct;
        }
        final TranslationBundle def = bundles.get(defaultLocale);
        if (def != null) {
            final String defStr = def.strings().get(key);
            if (defStr != null) {
                return defStr;
            }
        }
        final TranslationBundle us = bundles.get(Locale.US);
        if (us != null) {
            final String usStr = us.strings().get(key);
            if (usStr != null) {
                return usStr;
            }
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
     * Returns the localized label for free costs using a pre-resolved {@link Locale} (#20).
     */
    public Component freeCostLabel(final Locale locale) {
        return get(locale, MessageKey.COST_FREE);
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
        final TranslationBundle bundle = resolveCachedBundle(current, locale(player)).bundle();
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
     * Returns the common warp dialog header showing the current Aetheryte's name, owner, and coordinates (#53, #54).
     */
    public Component warpDialogHeader(final CommandSender viewer, final Aetheryte current, final String ownerName) {
        return get(
                viewer,
                MessageKey.DIALOG_WARP_HEADER,
                Placeholder.unparsed("name", current.name()),
                Placeholder.unparsed("owner", ownerName),
                Placeholder.unparsed("world", current.worldName()),
                Placeholder.unparsed("x", Integer.toString(current.blockX())),
                Placeholder.unparsed("y", Integer.toString(current.blockY())),
                Placeholder.unparsed("z", Integer.toString(current.blockZ()))
        );
    }

    /**
     * Returns the body message for the warp destination selection dialog.
     */
    public Component warpDialogBody(final CommandSender viewer, final Aetheryte current, final int destinationCount) {
        return warpDialogBody(viewer, current, fallbackOwnerName(viewer, current), destinationCount);
    }

    /**
     * Returns the body message for the warp destination selection dialog with an explicit resolved owner name (#53).
     */
    public Component warpDialogBody(
            final CommandSender viewer,
            final Aetheryte current,
            final String ownerName,
            final int destinationCount
    ) {
        final MessageKey key = destinationCount == 0 ? MessageKey.DIALOG_WARP_BODY_EMPTY : MessageKey.DIALOG_WARP_BODY_SELECT;
        return get(
                viewer,
                key,
                Placeholder.unparsed("name", current.name()),
                Placeholder.unparsed("owner", ownerName)
        );
    }

    /**
     * Returns the button label for an available warp destination entry showing its name and cost (#13).
     */
    public Component warpDestinationLabel(
            final CommandSender viewer,
            final Aetheryte destination,
            final Component costDisplay
    ) {
        return warpDestinationLabel(locale(viewer), destination, costDisplay);
    }

    /**
     * Returns the button label for an available warp destination entry using a pre-resolved {@link Locale} (#13, #20).
     */
    public Component warpDestinationLabel(
            final Locale locale,
            final Aetheryte destination,
            final Component costDisplay
    ) {
        return warpDestinationLabel(locale, destination, fallbackOwnerName(locale, destination), costDisplay, false);
    }

    /**
     * Returns the button label for an available warp destination entry showing its name, owner, and cost (#13, #53).
     */
    public Component warpDestinationLabel(
            final CommandSender viewer,
            final Aetheryte destination,
            final String ownerName,
            final Component costDisplay
    ) {
        return warpDestinationLabel(locale(viewer), destination, ownerName, costDisplay, false);
    }

    /**
     * Returns the button label for an available warp destination entry with optional favorite badge (#13, #53, #56).
     */
    public Component warpDestinationLabel(
            final CommandSender viewer,
            final Aetheryte destination,
            final String ownerName,
            final Component costDisplay,
            final boolean favorite
    ) {
        return warpDestinationLabel(locale(viewer), destination, ownerName, costDisplay, favorite);
    }

    /**
     * Returns the button label for an available warp destination entry using a pre-resolved {@link Locale} (#13, #20, #53, #56).
     */
    public Component warpDestinationLabel(
            final Locale locale,
            final Aetheryte destination,
            final String ownerName,
            final Component costDisplay,
            final boolean favorite
    ) {
        return renderWarpDestinationLabel(locale, MessageKey.DIALOG_WARP_DESTINATION_AVAILABLE, destination, ownerName, costDisplay, favorite);
    }

    /**
     * Returns the button label for an unavailable warp destination entry (#13).
     */
    public Component warpDestinationUnavailableLabel(
            final CommandSender viewer,
            final Aetheryte destination,
            final Component costDisplay
    ) {
        return warpDestinationUnavailableLabel(locale(viewer), destination, costDisplay);
    }

    /**
     * Returns the button label for an unavailable warp destination entry using a pre-resolved {@link Locale} (#13, #20).
     */
    public Component warpDestinationUnavailableLabel(
            final Locale locale,
            final Aetheryte destination,
            final Component costDisplay
    ) {
        return warpDestinationUnavailableLabel(locale, destination, fallbackOwnerName(locale, destination), costDisplay, false);
    }

    /**
     * Returns the button label for an unavailable warp destination entry with an explicit owner name (#13, #53).
     */
    public Component warpDestinationUnavailableLabel(
            final CommandSender viewer,
            final Aetheryte destination,
            final String ownerName,
            final Component costDisplay
    ) {
        return warpDestinationUnavailableLabel(locale(viewer), destination, ownerName, costDisplay, false);
    }

    /**
     * Returns the button label for an unavailable warp destination entry with optional favorite badge (#13, #53, #56).
     */
    public Component warpDestinationUnavailableLabel(
            final CommandSender viewer,
            final Aetheryte destination,
            final String ownerName,
            final Component costDisplay,
            final boolean favorite
    ) {
        return warpDestinationUnavailableLabel(locale(viewer), destination, ownerName, costDisplay, favorite);
    }

    /**
     * Returns the button label for an unavailable warp destination entry using a pre-resolved {@link Locale} (#13, #20, #53, #56).
     */
    public Component warpDestinationUnavailableLabel(
            final Locale locale,
            final Aetheryte destination,
            final String ownerName,
            final Component costDisplay,
            final boolean favorite
    ) {
        return renderWarpDestinationLabel(locale, MessageKey.DIALOG_WARP_DESTINATION_UNAVAILABLE, destination, ownerName, costDisplay, favorite);
    }

    private Component renderWarpDestinationLabel(
            final Locale locale,
            final MessageKey key,
            final Aetheryte destination,
            final String ownerName,
            final Component costDisplay,
            final boolean favorite
    ) {
        final Component base = renderWithPlaceholders(
                locale,
                key,
                ARG_NAME_OWNER_COST,
                Component.text(destination.name()),
                Component.text(ownerName),
                costDisplay
        );
        return favorite ? get(locale, MessageKey.DIALOG_WARP_FAVORITE_BADGE).append(base) : base;
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
        return warpDestinationTooltip(
                viewer,
                current,
                destination,
                fallbackOwnerName(viewer, destination),
                costDisplay,
                allowed,
                denyReason
        );
    }

    /**
     * Returns the button tooltip for a warp destination entry showing owner, world, coordinates, distance, cost, and status (#13, #53).
     */
    public Component warpDestinationTooltip(
            final CommandSender viewer,
            final Aetheryte current,
            final Aetheryte destination,
            final String ownerName,
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

        final TagResolver.Builder resolvers = TagResolver.builder()
                .resolver(Placeholder.unparsed("owner", ownerName))
                .resolver(Placeholder.unparsed("world", destination.worldName()))
                .resolver(Placeholder.unparsed("x", Integer.toString(destination.blockX())))
                .resolver(Placeholder.unparsed("y", Integer.toString(destination.blockY())))
                .resolver(Placeholder.unparsed("z", Integer.toString(destination.blockZ())))
                .resolver(Placeholder.component("distance", distanceText))
                .resolver(Placeholder.component("cost", costDisplay));

        if (allowed) {
            return get(loc, MessageKey.DIALOG_WARP_TOOLTIP_ALLOWED, resolvers.build());
        }

        final Component effectiveReason = denyReason != null
                ? denyReason
                : get(loc, MessageKey.ERROR_WARP_REQUIREMENTS_NOT_MET);
        resolvers.resolver(Placeholder.component("reason", effectiveReason));
        return get(loc, MessageKey.DIALOG_WARP_TOOLTIP_DENIED, resolvers.build());
    }

    private String fallbackOwnerName(final CommandSender viewer, final Aetheryte aetheryte) {
        return fallbackOwnerName(locale(viewer), aetheryte);
    }

    private String fallbackOwnerName(final Locale locale, final Aetheryte aetheryte) {
        if (aetheryte.ownerName() != null) {
            return aetheryte.ownerName();
        }
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(get(locale, MessageKey.DIALOG_WARP_UNKNOWN_OWNER));
    }

    /**
     * Returns the root menu body prompt message (#54).
     */
    public Component warpRootBody(final CommandSender viewer, final Aetheryte current, final String ownerName) {
        return get(
                viewer,
                MessageKey.DIALOG_WARP_ROOT_BODY,
                Placeholder.unparsed("name", current.name()),
                Placeholder.unparsed("owner", ownerName),
                Placeholder.unparsed("world", current.worldName()),
                Placeholder.unparsed("x", Integer.toString(current.blockX())),
                Placeholder.unparsed("y", Integer.toString(current.blockY())),
                Placeholder.unparsed("z", Integer.toString(current.blockZ()))
        );
    }

    /**
     * Returns a formatted world category button label according to {@link World.Environment} (#54).
     */
    public Component warpWorldCategoryLabel(
            final CommandSender viewer,
            final World.Environment environment,
            final String worldName,
            final int count,
            final boolean currentWorld
    ) {
        final MessageKey key = switch (environment) {
            case NORMAL -> MessageKey.DIALOG_WARP_CATEGORY_WORLD_OVERWORLD;
            case NETHER -> MessageKey.DIALOG_WARP_CATEGORY_WORLD_NETHER;
            case THE_END -> MessageKey.DIALOG_WARP_CATEGORY_WORLD_END;
            case CUSTOM -> MessageKey.DIALOG_WARP_CATEGORY_WORLD_CUSTOM;
        };
        final Component currentBadge = currentWorld
                ? get(viewer, MessageKey.DIALOG_WARP_CATEGORY_WORLD_CURRENT_BADGE)
                : Component.empty();
        return get(
                viewer,
                key,
                Placeholder.unparsed("world", worldName),
                Placeholder.unparsed("count", Integer.toString(count)),
                Placeholder.component("current", currentBadge)
        );
    }

    /**
     * Returns the localized label for the given {@link WarpSortOrder} (#55).
     */
    public Component warpSortOrderLabel(final CommandSender viewer, final WarpSortOrder order) {
        final MessageKey key = switch (order) {
            case DISTANCE -> MessageKey.DIALOG_WARP_SORT_DISTANCE;
            case NAME -> MessageKey.DIALOG_WARP_SORT_NAME;
            case OWNER -> MessageKey.DIALOG_WARP_SORT_OWNER;
            case CREATED -> MessageKey.DIALOG_WARP_SORT_CREATED;
        };
        return get(viewer, key);
    }

    /**
     * Returns the header body for a paginated, filterable warp list screen (#54, #55).
     */
    public Component warpListHeader(
            final CommandSender viewer,
            final Component scopeLabel,
            final WarpSortOrder sortOrder,
            final String searchQuery,
            final int currentPageZeroBased,
            final int totalPages,
            final int totalItems
    ) {
        Component header = get(
                viewer,
                MessageKey.DIALOG_WARP_LIST_HEADER,
                Placeholder.component("scope", scopeLabel),
                Placeholder.component("sort", warpSortOrderLabel(viewer, sortOrder))
        );
        if (!searchQuery.isEmpty()) {
            header = header.append(get(
                    viewer,
                    MessageKey.DIALOG_WARP_LIST_HEADER_SEARCH,
                    Placeholder.unparsed("query", searchQuery)
            ));
        }
        if (totalItems > 0) {
            header = header.append(Component.newline()).append(get(
                    viewer,
                    MessageKey.DIALOG_WARP_PAGE_INDICATOR,
                    Placeholder.unparsed("current", Integer.toString(currentPageZeroBased + 1)),
                    Placeholder.unparsed("total", Integer.toString(totalPages)),
                    Placeholder.unparsed("count", Integer.toString(totalItems))
            ));
        }
        return header;
    }

    /**
     * Returns the operations dialog title (#57).
     */
    public Component operationsDialogTitle(final CommandSender viewer, final Aetheryte current) {
        return get(viewer, MessageKey.DIALOG_OPERATIONS_TITLE, Placeholder.unparsed("name", current.name()));
    }

    /**
     * Returns the operations dialog body showing Aetheryte details (#57).
     */
    public Component operationsDialogBody(
            final CommandSender viewer,
            final Aetheryte current,
            final String ownerName,
            final String variationDisplayNameMiniMessage
    ) {
        return get(
                viewer,
                MessageKey.DIALOG_OPERATIONS_BODY,
                Placeholder.unparsed("name", current.name()),
                Placeholder.unparsed("owner", ownerName),
                Placeholder.unparsed("world", current.worldName()),
                Placeholder.unparsed("x", Integer.toString(current.blockX())),
                Placeholder.unparsed("y", Integer.toString(current.blockY())),
                Placeholder.unparsed("z", Integer.toString(current.blockZ())),
                Placeholder.component("variation", MINI_MESSAGE.deserialize(variationDisplayNameMiniMessage))
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
     * Returns the variation line for the Aetheryte creation dialog (#37).
     */
    public Component createDialogVariation(final CommandSender viewer, final String variationDisplayNameMiniMessage) {
        final Component variationComponent = MINI_MESSAGE.deserialize(variationDisplayNameMiniMessage);
        return get(viewer, MessageKey.DIALOG_CREATE_VARIATION, Placeholder.component("variation", variationComponent));
    }

    /**
     * Returns the error message when the surrounding clearance volume is obstructed (#42).
     */
    public Component errorClearanceInsufficient(final CommandSender viewer, final int horizontalSpan, final int height) {
        return get(
                viewer,
                MessageKey.ERROR_CLEARANCE_INSUFFICIENT,
                Placeholder.unparsed("width", Integer.toString(horizontalSpan)),
                Placeholder.unparsed("height", Integer.toString(height))
        );
    }

    /**
     * Returns the error message when the global per-player Aetheryte limit is reached (#43).
     */
    public Component errorLimitReachedGlobal(final CommandSender viewer, final int max) {
        return get(viewer, MessageKey.ERROR_LIMIT_REACHED_GLOBAL, Placeholder.unparsed("max", Integer.toString(max)));
    }

    /**
     * Returns the error message when the per-variation placement limit is reached (#43).
     */
    public Component errorLimitReachedVariation(
            final CommandSender viewer,
            final String variationDisplayNameMiniMessage,
            final int max
    ) {
        final Component variationComponent = MINI_MESSAGE.deserialize(variationDisplayNameMiniMessage);
        return get(
                viewer,
                MessageKey.ERROR_LIMIT_REACHED_VARIATION,
                Placeholder.component("variation", variationComponent),
                Placeholder.unparsed("max", Integer.toString(max))
        );
    }

    /**
     * Returns the error message when the player is on placement cooldown (#43).
     */
    public Component errorPlacementCooldown(final CommandSender viewer, final int remainingTicks) {
        final double secondsValue = Math.max(0.1, remainingTicks / 20.0);
        final String formattedSeconds = String.format(Locale.ROOT, "%.1f", secondsValue);
        return get(viewer, MessageKey.ERROR_PLACEMENT_COOLDOWN, Placeholder.unparsed("seconds", formattedSeconds));
    }

    /**
     * Returns the error message when an unknown variation identifier is given to a command (#37).
     */
    public Component commandUnknownVariation(final CommandSender viewer, final String variationId) {
        return get(viewer, MessageKey.COMMAND_UNKNOWN_VARIATION, Placeholder.unparsed("variation", variationId));
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
