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
package net.sabafly.clickportals.variation;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Thread-safe registry of loaded {@link AetheryteVariation} definitions with atomic reload and
 * fallback to {@link Aetheryte#DEFAULT_VARIATION} (#37).
 */
public final class VariationRegistry {

    private final Set<String> warnedUnknownIds = ConcurrentHashMap.newKeySet();
    private final @Nullable Logger logger;
    private volatile Map<String, AetheryteVariation> variations;

    /**
     * Creates a new {@link VariationRegistry} with the given initial variation map.
     */
    public VariationRegistry(final Map<String, AetheryteVariation> initialVariations, @Nullable final Logger logger) {
        this.logger = logger;
        final Map<String, AetheryteVariation> copy = new LinkedHashMap<>(initialVariations);
        copy.putIfAbsent(Aetheryte.DEFAULT_VARIATION, AetheryteVariation.defaultVariation());
        this.variations = Map.copyOf(copy);
    }

    /**
     * Creates a {@link VariationRegistry} containing only the built-in default variation.
     */
    public static VariationRegistry defaults() {
        return new VariationRegistry(
                Map.of(Aetheryte.DEFAULT_VARIATION, AetheryteVariation.defaultVariation()),
                null
        );
    }

    /**
     * Loads a {@link VariationRegistry} from {@code aetheryteDir}.
     */
    public static VariationRegistry load(final Path aetheryteDir, final Logger logger) {
        return new VariationRegistry(VariationLoader.loadDirectory(aetheryteDir, logger), logger);
    }

    /**
     * Reloads all variation definitions from {@code aetheryteDir} atomically.
     */
    public void reload(final Path aetheryteDir, final Logger reloadLogger) {
        this.variations = VariationLoader.loadDirectory(aetheryteDir, reloadLogger);
        this.warnedUnknownIds.clear();
    }

    /**
     * Returns the {@link AetheryteVariation} for {@code id}, falling back to {@code default} and logging a warning
     * once if the variation ID is unknown.
     */
    public AetheryteVariation getOrDefault(@Nullable final String id) {
        final String normalized = Aetheryte.normalizeVariation(id);
        final Map<String, AetheryteVariation> current = this.variations;
        final AetheryteVariation found = current.get(normalized);
        if (found != null) {
            return found;
        }
        if (logger != null && warnedUnknownIds.add(normalized)) {
            logger.warn("Unknown Aetheryte variation '{}'; falling back to '{}'",
                    normalized, Aetheryte.DEFAULT_VARIATION);
        }
        final AetheryteVariation def = current.get(Aetheryte.DEFAULT_VARIATION);
        return def != null ? def : AetheryteVariation.defaultVariation();
    }

    /**
     * Looks up a registered variation by exact ID without falling back to {@code default}.
     */
    public Optional<AetheryteVariation> find(@Nullable final String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.variations.get(Aetheryte.normalizeVariation(id)));
    }

    /**
     * Returns an immutable snapshot of all registered variations keyed by variation ID.
     */
    public Map<String, AetheryteVariation> all() {
        return this.variations;
    }

    /**
     * Returns the set of all registered variation identifiers.
     */
    public Set<String> ids() {
        return this.variations.keySet();
    }
}
