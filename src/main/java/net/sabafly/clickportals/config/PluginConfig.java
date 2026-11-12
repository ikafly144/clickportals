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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.expr.ExpressionException;
import net.sabafly.clickportals.cost.expr.ExpressionParser;
import net.sabafly.clickportals.message.LanguageLoader;
import net.sabafly.clickportals.route.CostOverrideConfig;
import net.sabafly.clickportals.route.DimensionRoutesConfig;
import net.sabafly.clickportals.route.RouteEntryConfig;
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
        LimitsSection limits,

        @Setting("warp")
        @Comment("Global warping settings: distance limits and base cost (#14, #26)")
        WarpSection warp,

        @Setting("dimensions")
        @Comment("Cross-dimension warping routes and dimension pricing (#9)")
        DimensionRoutesConfig dimensions
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
     * Global warping configuration section (#14, #26).
     */
    @ConfigSerializable
    public record WarpSection(
            @Setting("distance")
            @Comment("Distance limitations for warping")
            DistanceSection distance,

            @Setting("cost")
            @Comment("Base cost specifications for warping")
            List<CostEntry> cost,

            @Setting("show-optional-costs")
            @Comment("Whether non-mandatory costs are shown in destination menus and tooltips (default true)")
            boolean showOptionalCosts
    ) {
        public WarpSection {
            if (distance == null) {
                distance = DistanceSection.defaults();
            }
            if (cost == null) {
                cost = List.of();
            }
        }

        public static WarpSection defaults() {
            return new WarpSection(DistanceSection.defaults(), List.of(), true);
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
        if (warp == null) {
            warp = WarpSection.defaults();
        }
        if (dimensions == null) {
            dimensions = DimensionRoutesConfig.defaults();
        }
    }

    /**
     * Returns the default {@link PluginConfig}.
     */
    public static PluginConfig defaultConfig() {
        return new PluginConfig(
                LanguageSection.defaults(),
                HologramSection.defaults(),
                LimitsSection.defaults(),
                WarpSection.defaults(),
                DimensionRoutesConfig.defaults()
        );
    }

    /**
     * Validates and normalizes this configuration, logging warnings and falling back to safe defaults on invalid values.
     */
    public PluginConfig validated(final Logger logger) {
        final LanguageSection defaultLanguage = LanguageSection.defaults();

        final String rawLocale = language.defaultLocale();
        final String validDefaultLocale;
        if (rawLocale == null || rawLocale.isBlank()) {
            logger.warn("language.default-locale is missing or empty; falling back to {}", defaultLanguage.defaultLocale());
            validDefaultLocale = defaultLanguage.defaultLocale();
        } else {
            validDefaultLocale = rawLocale.strip();
        }

        final double validViewDistance;
        if (!Double.isFinite(hologram.viewDistance()) || hologram.viewDistance() < 4.0) {
            logger.warn("Clamping hologram.view-distance {} to minimum 4.0", hologram.viewDistance());
            validViewDistance = 4.0;
        } else {
            validViewDistance = hologram.viewDistance();
        }

        final int rawMaxPerPlayer = limits.maxAetherytesPerPlayer();
        final int validMaxPerPlayer;
        if (rawMaxPerPlayer < -1) {
            logger.warn("Invalid limits.max-aetherytes-per-player {} (must be >= -1); falling back to -1 (unlimited)", rawMaxPerPlayer);
            validMaxPerPlayer = -1;
        } else {
            validMaxPerPlayer = rawMaxPerPlayer;
        }

        final int rawCooldownTicks = limits.placementCooldownTicks();
        final int validCooldownTicks;
        if (rawCooldownTicks < 0) {
            logger.warn("Invalid limits.placement-cooldown-ticks {} (must be >= 0); falling back to 0", rawCooldownTicks);
            validCooldownTicks = 0;
        } else {
            validCooldownTicks = rawCooldownTicks;
        }

        final DistanceSection rawDist = warp.distance();
        final double validMinDist;
        if (rawDist.min() == null) {
            logger.warn("warp.distance.min is missing; falling back to 0.0");
            validMinDist = 0.0;
        } else if (rawDist.min() < 0.0) {
            logger.warn("Invalid warp.distance.min {} (must be >= 0.0); falling back to 0.0", rawDist.min());
            validMinDist = 0.0;
        } else {
            validMinDist = rawDist.min();
        }

        double validMaxDist;
        if (rawDist.max() == null) {
            logger.warn("warp.distance.max is missing; falling back to -1.0 (unlimited)");
            validMaxDist = -1.0;
        } else if (rawDist.max() < -1.0) {
            logger.warn("Invalid warp.distance.max {} (must be >= -1.0); falling back to -1.0 (unlimited)", rawDist.max());
            validMaxDist = -1.0;
        } else {
            validMaxDist = rawDist.max();
        }

        if (validMaxDist >= 0.0 && validMinDist > validMaxDist) {
            logger.warn("warp.distance.min ({}) is greater than warp.distance.max ({}). Disabling max distance (-1.0) to prevent deadlocks.",
                    validMinDist, validMaxDist);
            validMaxDist = -1.0;
        }
        final DistanceSection validDist = new DistanceSection(validMinDist, validMaxDist);

        if (warp.cost() != null) {
            for (final CostEntry entry : warp.cost()) {
                if (entry.type() == null) {
                    logger.warn("Unknown cost type '{}' in warp.cost; this cost will fail affordability checks.", entry.rawType());
                } else {
                    try {
                        ExpressionParser.parse(entry.amount(), entry.type().resourceVariable());
                    } catch (final ExpressionException e) {
                        logger.warn("Invalid cost amount expression '{}' in warp.cost: {}", entry.amount(), e.getMessage());
                    }
                }
            }
        }

        final WarpSection validWarp = new WarpSection(validDist, warp.cost(), warp.showOptionalCosts());

        final DimensionRoutesConfig validDimensions;
        if (dimensions == null) {
            logger.warn("Missing 'dimensions' section in config.yml; falling back to default dimension routes");
            validDimensions = DimensionRoutesConfig.defaults();
        } else {
            validDimensions = validateDimensions(dimensions, logger);
        }

        return new PluginConfig(
                new LanguageSection(
                        validDefaultLocale,
                        language.useClientLocale()
                ),
                new HologramSection(
                        hologram.enabled(),
                        validViewDistance
                ),
                new LimitsSection(validMaxPerPlayer, validCooldownTicks),
                validWarp,
                validDimensions
        );
    }

    private static DimensionRoutesConfig validateDimensions(final DimensionRoutesConfig config, final Logger logger) {
        if (config.defaultCost() != null) {
            for (final CostEntry entry : config.defaultCost()) {
                if (entry.type() == null) {
                    logger.warn("Unknown cost type '{}' in dimensions.default-cost", entry.rawType());
                } else {
                    try {
                        ExpressionParser.parse(entry.amount(), "$amount");
                    } catch (final ExpressionException e) {
                        logger.warn("Invalid cost expression '{}' in dimensions.default-cost: {}", entry.amount(), e.getMessage());
                    }
                }
            }
        }
        if (config.amounts() != null) {
            for (final Map.Entry<String, Double> entry : config.amounts().entrySet()) {
                final Double amount = entry.getValue();
                if (amount == null || !Double.isFinite(amount) || amount < 0.0) {
                    logger.warn("Invalid dimension amount {} for world '{}' in dimensions.amounts; must be >= 0.0",
                            amount, entry.getKey());
                }
            }
        }
        if (config.routes() != null) {
            for (final RouteEntryConfig route : config.routes()) {
                if (route.between() == null || route.between().size() < 2) {
                    logger.warn("Invalid route in dimensions.routes: 'between' must contain at least 2 world names, got {}",
                            route.between());
                }
                if (route.costOverride() != null) {
                    for (final Map.Entry<String, CostOverrideConfig> entry : route.costOverride().entrySet()) {
                        final String expr = entry.getValue().amount();
                        if (expr != null) {
                            try {
                                ExpressionParser.parse(expr, "$default");
                            } catch (final ExpressionException e) {
                                logger.warn("Invalid cost-override expression '{}' for world '{}' in dimensions.routes: {}",
                                        expr, entry.getKey(), e.getMessage());
                            }
                        }
                    }
                }
            }
        }
        return config;
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

    /**
     * Returns the global minimum warp distance in blocks (0 for disabled) (#26).
     */
    public double warpDistanceMin() {
        return warp.distance().minOrDefault(0.0);
    }

    /**
     * Returns the global maximum warp distance in blocks (-1 for unlimited) (#26).
     */
    public double warpDistanceMax() {
        return warp.distance().maxOrDefault(-1.0);
    }

    /**
     * Returns the global warp cost specifications (#14).
     */
    public List<CostEntry> warpCost() {
        return warp.cost();
    }

    /**
     * Returns whether non-mandatory costs are shown in destination menus and tooltips.
     */
    public boolean showOptionalCosts() {
        return warp.showOptionalCosts();
    }
}
