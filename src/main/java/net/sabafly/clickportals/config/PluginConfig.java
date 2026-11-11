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

import java.util.Locale;
import net.sabafly.clickportals.message.LanguageLoader;
import org.slf4j.Logger;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/**
 * Type-safe Configurate root configuration model for ClickPortals (#19, #33, #37, #43).
 */
@ConfigSerializable
public record PluginConfig(
        @Setting("language")
        @Comment("Language and localization settings")
        LanguageSection language,

        @Setting("hologram")
        @Comment("Client-side visual and interaction (PacketEvents Display & Interaction) settings")
        HologramSection hologram,

        @Setting("limits")
        @Comment("Global per-player Aetheryte placement limit and cooldown settings (#43)")
        LimitsSection limits
) {

    /**
     * Localization configuration section (#19).
     */
    @ConfigSerializable
    public record LanguageSection(
            @Setting("default-locale")
            @Comment("Fallback locale used for console messages, item names, and unsupported client locales (e.g. en_US, ja_JP)")
            String defaultLocale,

            @Setting("use-client-locale")
            @Comment("Whether to automatically switch player-facing messages and holograms to match the player's client language")
            boolean useClientLocale
    ) {
        /**
         * Returns the default language section.
         */
        public static LanguageSection defaults() {
            return new LanguageSection("en_US", true);
        }
    }

    /**
     * Client-side visual and interaction configuration section.
     */
    @ConfigSerializable
    public record HologramSection(
            @Setting("enabled")
            @Comment("Whether PacketEvents client-side displays and interaction hitboxes are enabled")
            boolean enabled,

            @Setting("view-distance")
            @Comment("View distance for holograms and models in blocks (minimum 4.0)")
            double viewDistance
    ) {
        /**
         * Returns the default hologram section.
         */
        public static HologramSection defaults() {
            return new HologramSection(true, 32.0);
        }
    }

    /**
     * Default placement cooldown in ticks (100 ticks = 5 seconds).
     */
    public static final int DEFAULT_PLACEMENT_COOLDOWN_TICKS = 100;

    /**
     * Global per-player placement limit and cooldown configuration section (#43).
     */
    @ConfigSerializable
    public record LimitsSection(
            @Setting("max-aetherytes-per-player")
            @Comment("Maximum total Aetherytes a single player may own across all variations (-1 for unlimited)")
            int maxAetherytesPerPlayer,

            @Setting("placement-cooldown-ticks")
            @Comment("Default cooldown in ticks after placing an Aetheryte before placing another (100 = 5 seconds, 0 to disable)")
            int placementCooldownTicks
    ) {
        /**
         * Returns the default limits section (-1 = unlimited, 100 ticks = 5s cooldown).
         */
        public static LimitsSection defaults() {
            return new LimitsSection(-1, DEFAULT_PLACEMENT_COOLDOWN_TICKS);
        }
    }

    /**
     * Compact constructor ensuring non-null sections when deserialized by Configurate.
     */
    public PluginConfig {
        if (language == null) {
            language = LanguageSection.defaults();
        }
        if (hologram == null) {
            hologram = HologramSection.defaults();
        }
        if (limits == null) {
            limits = LimitsSection.defaults();
        }
    }

    /**
     * Returns the default {@link PluginConfig}.
     */
    public static PluginConfig defaultConfig() {
        return new PluginConfig(
                LanguageSection.defaults(),
                HologramSection.defaults(),
                LimitsSection.defaults()
        );
    }

    /**
     * Validates and normalizes this configuration, falling back to safe defaults on invalid values.
     */
    public PluginConfig validated(final Logger logger) {
        final LanguageSection defaultLanguage = LanguageSection.defaults();

        final String rawLocale = language.defaultLocale();
        final String validDefaultLocale = (rawLocale == null || rawLocale.isBlank())
                ? defaultLanguage.defaultLocale()
                : rawLocale.strip();

        final double validViewDistance;
        if (!Double.isFinite(hologram.viewDistance()) || hologram.viewDistance() < 4.0) {
            logger.warn("Clamping hologram.view-distance {} to minimum 4.0", hologram.viewDistance());
            validViewDistance = 4.0;
        } else {
            validViewDistance = hologram.viewDistance();
        }

        final int validMaxPerPlayer = limits.maxAetherytesPerPlayer() < -1 ? -1 : limits.maxAetherytesPerPlayer();
        final int validCooldownTicks = Math.max(0, limits.placementCooldownTicks());

        return new PluginConfig(
                new LanguageSection(
                        validDefaultLocale,
                        language.useClientLocale()
                ),
                new HologramSection(
                        hologram.enabled(),
                        validViewDistance
                ),
                new LimitsSection(validMaxPerPlayer, validCooldownTicks)
        );
    }

    /**
     * Returns the parsed default {@link Locale}.
     */
    public Locale defaultLocale() {
        return LanguageLoader.parseLocale(language.defaultLocale());
    }

    /**
     * Returns whether player-facing text should adapt to the player's client locale.
     */
    public boolean useClientLocale() {
        return language.useClientLocale();
    }

    /**
     * Returns whether client-side holograms and visuals are enabled.
     */
    public boolean hologramEnabled() {
        return hologram.enabled();
    }

    /**
     * Returns the view distance in blocks for client-side visuals.
     */
    public double hologramViewDistance() {
        return hologram.viewDistance();
    }

    /**
     * Returns the global maximum number of Aetherytes a player may own ({@code -1} for unlimited) (#43).
     */
    public int maxAetherytesPerPlayer() {
        return limits.maxAetherytesPerPlayer();
    }

    /**
     * Returns the global default placement cooldown in ticks ({@code 0} for disabled) (#43).
     */
    public int placementCooldownTicks() {
        return limits.placementCooldownTicks();
    }
}
