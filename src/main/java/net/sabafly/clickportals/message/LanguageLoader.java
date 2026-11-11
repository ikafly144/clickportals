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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Loads bundled and user-overridden YAML translation files from {@code <dataDir>/lang/} (#18, #19).
 */
public final class LanguageLoader {

    /**
     * Built-in locale identifiers bundled in the plugin jar under {@code /lang/}.
     */
    public static final List<String> BUNDLED_LOCALES = List.of("en_US", "ja_JP");

    private static final Set<String> KNOWN_PATHS;

    static {
        final Set<String> paths = new HashSet<>();
        for (final MessageKey key : MessageKey.values()) {
            paths.add(key.path());
        }
        KNOWN_PATHS = Set.copyOf(paths);
    }

    /**
     * Immutable bundle of single-line and multi-line MiniMessage templates for a single locale.
     */
    public record TranslationBundle(
            Map<MessageKey, String> strings,
            Map<MessageKey, List<String>> lists
    ) {
        /**
         * Creates an empty bundle.
         */
        public static TranslationBundle empty() {
            return new TranslationBundle(Map.of(), Map.of());
        }
    }

    private LanguageLoader() {
    }

    /**
     * Parses a locale identifier such as {@code "en_US"}, {@code "ja_jp"}, or {@code "ja-JP"} into a normalized
     * {@link Locale} with lowercase language and uppercase country.
     */
    public static Locale parseLocale(@Nullable final String raw) {
        if (raw == null || raw.isBlank()) {
            return Locale.US;
        }
        final String cleaned = raw.strip().replace('-', '_');
        final String[] parts = cleaned.split("_", 3);
        final String language = parts[0].toLowerCase(Locale.ROOT);
        if (language.isEmpty()) {
            return Locale.US;
        }
        final String country = parts.length >= 2 ? parts[1].toUpperCase(Locale.ROOT) : "";
        return Locale.of(language, country);
    }

    /**
     * Normalizes a {@link Locale} to lowercase language and uppercase country for map lookup.
     */
    public static Locale normalizeLocale(final Locale locale) {
        final String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        final String country = locale.getCountry().toUpperCase(Locale.ROOT);
        if (language.isEmpty()) {
            return Locale.US;
        }
        return Locale.of(language, country);
    }

    /**
     * Loads only the built-in classpath bundles without touching the filesystem.
     */
    public static Map<Locale, TranslationBundle> loadBundledOnly(@Nullable final Logger logger) {
        final Map<Locale, TranslationBundle> bundles = new LinkedHashMap<>();
        for (final String code : BUNDLED_LOCALES) {
            final Locale locale = parseLocale(code);
            final CommentedConfigurationNode node = loadClasspathNode("/lang/" + code + ".yml", logger);
            if (node != null) {
                bundles.put(locale, extractBundle(node, TranslationBundle.empty(), code + ".yml (bundled)", logger));
            }
        }
        return Map.copyOf(bundles);
    }

    /**
     * Copies missing bundled language files into {@code langDir} and loads all {@code *.yml} translation bundles,
     * merging user overrides on top of the bundled defaults (#19).
     */
    public static Map<Locale, TranslationBundle> loadDirectory(final Path langDir, final Logger logger) {
        final Map<Locale, TranslationBundle> bundled = loadBundledOnly(logger);
        final Map<Locale, TranslationBundle> result = new LinkedHashMap<>(bundled);

        try {
            if (!Files.exists(langDir)) {
                Files.createDirectories(langDir);
            }
            for (final String code : BUNDLED_LOCALES) {
                final Path target = langDir.resolve(code + ".yml");
                if (!Files.exists(target)) {
                    copyBundledResource("/lang/" + code + ".yml", target, logger);
                }
            }

            try (final Stream<Path> paths = Files.list(langDir)) {
                final List<Path> files = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            final String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.endsWith(".yml") || name.endsWith(".yaml");
                        })
                        .sorted()
                        .toList();

                final TranslationBundle fallbackBase = bundled.getOrDefault(Locale.US, TranslationBundle.empty());
                for (final Path file : files) {
                    final String fileName = file.getFileName().toString();
                    final int dotIndex = fileName.lastIndexOf('.');
                    final String stem = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
                    final Locale locale = parseLocale(stem);
                    final TranslationBundle base = bundled.getOrDefault(locale, fallbackBase);
                    try {
                        final CommentedConfigurationNode root = YamlConfigurationLoader.builder()
                                .path(file)
                                .build()
                                .load();
                        final TranslationBundle merged = extractBundle(root, base, fileName, logger);
                        result.put(locale, merged);
                    } catch (final IOException e) {
                        logger.warn("Failed to load language file {}; keeping defaults for {}", file, locale, e);
                    }
                }
            }
        } catch (final IOException e) {
            logger.warn("Failed to access language directory {}; using bundled translations", langDir, e);
        }

        return Map.copyOf(result);
    }

    /**
     * Copies a bundled classpath YAML resource to the target filesystem path.
     */
    private static void copyBundledResource(final String resourcePath, final Path target, final Logger logger) {
        try (final InputStream in = LanguageLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                logger.warn("Bundled language resource {} is missing from plugin jar", resourcePath);
                return;
            }
            Files.copy(in, target);
        } catch (final IOException e) {
            logger.warn("Failed to copy default language file {} to {}", resourcePath, target, e);
        }
    }

    /**
     * Loads and parses a YAML configuration node from a classpath resource path.
     */
    static @Nullable CommentedConfigurationNode loadClasspathNode(
            final String resourcePath,
            @Nullable final Logger logger
    ) {
        try (final InputStream in = LanguageLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                if (logger != null) {
                    logger.warn("Bundled language resource {} was not found", resourcePath);
                }
                return null;
            }
            return YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .build()
                    .load();
        } catch (final IOException e) {
            if (logger != null) {
                logger.warn("Failed to parse bundled language resource {}", resourcePath, e);
            }
            return null;
        }
    }

    /**
     * Extracts all known {@link MessageKey} entries from {@code root}, merging them over {@code base}.
     */
    static TranslationBundle extractBundle(
            final ConfigurationNode root,
            final TranslationBundle base,
            final String sourceName,
            @Nullable final Logger logger
    ) {
        final Map<MessageKey, String> strings = new EnumMap<>(MessageKey.class);
        final Map<MessageKey, List<String>> lists = new EnumMap<>(MessageKey.class);
        strings.putAll(base.strings());
        lists.putAll(base.lists());

        for (final MessageKey key : MessageKey.values()) {
            final ConfigurationNode node = root.node(key.nodePath());
            if (node.virtual()) {
                continue;
            }
            if (key.isList()) {
                if (node.isList()) {
                    try {
                        final List<String> val = node.getList(String.class);
                        if (val != null) {
                            lists.put(key, List.copyOf(val));
                        }
                    } catch (final SerializationException e) {
                        if (logger != null) {
                            logger.warn("Invalid list value for key '{}' in {}", key.path(), sourceName, e);
                        }
                    }
                } else {
                    final String single = node.getString();
                    if (single != null) {
                        lists.put(key, List.of(single));
                    }
                }
            } else {
                final String val = node.getString();
                if (val != null) {
                    strings.put(key, val);
                }
            }
        }

        if (logger != null) {
            warnUnknownKeys(root, "", sourceName, logger);
        }

        return new TranslationBundle(Map.copyOf(strings), Map.copyOf(lists));
    }

    /**
     * Recursively logs warnings for any leaf keys in {@code node} that do not match a known {@link MessageKey}.
     */
    private static void warnUnknownKeys(
            final ConfigurationNode node,
            final String prefix,
            final String sourceName,
            final Logger logger
    ) {
        if (node.isMap()) {
            for (final Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
                final String key = String.valueOf(entry.getKey());
                final String fullPath = prefix.isEmpty() ? key : prefix + "." + key;
                warnUnknownKeys(entry.getValue(), fullPath, sourceName, logger);
            }
        } else if (!prefix.isEmpty() && !KNOWN_PATHS.contains(prefix)) {
            logger.warn("Unknown translation key '{}' in {}", prefix, sourceName);
        }
    }

    /**
     * Collects all leaf key paths present in a configuration node tree (used for parity testing).
     */
    public static List<String> collectLeafPaths(final ConfigurationNode root) {
        final List<String> result = new ArrayList<>();
        collectLeafPathsRecursive(root, "", result);
        return List.copyOf(result);
    }

    /**
     * Recursively appends dot-separated leaf paths from {@code node} into {@code out}.
     */
    private static void collectLeafPathsRecursive(
            final ConfigurationNode node,
            final String prefix,
            final List<String> out
    ) {
        if (node.isMap()) {
            for (final Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
                final String key = String.valueOf(entry.getKey());
                final String fullPath = prefix.isEmpty() ? key : prefix + "." + key;
                collectLeafPathsRecursive(entry.getValue(), fullPath, out);
            }
        } else if (!prefix.isEmpty()) {
            out.add(prefix);
        }
    }
}
