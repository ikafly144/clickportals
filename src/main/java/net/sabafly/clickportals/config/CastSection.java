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

import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/**
 * Warp casting (cast time and player animation) configuration (#65).
 */
@ConfigSerializable
public record CastSection(
        @Setting("ticks")
        @Comment("Cast time in ticks before warping (0 or less for instant warp; in variations: -1 to inherit global)")
        @Nullable Integer ticks,

        @Setting("player-model")
        @Comment("BetterModel player limb model name used for player animations (e.g. steve; in variations: null to inherit global)")
        @Nullable String playerModel,

        @Setting("cast-animation")
        @Comment("BetterModel player animation played during casting with HOLD_ON_LAST (empty to disable; in variations: null to inherit global)")
        @Nullable String castAnimation,

        @Setting("arrive-animation")
        @Comment("BetterModel player animation played after teleport arrival with PLAY_ONCE (empty to disable; in variations: null to inherit global)")
        @Nullable String arriveAnimation,

        @Setting("immobilize")
        @Comment("Whether to prevent the player from moving via PacketEvents while casting (head rotation remains allowed; in variations: null to inherit global)")
        @Nullable Boolean immobilize,

        @Setting("cancel")
        @Comment("Cancellation conditions while casting")
        @Nullable CastCancelSection cancel
) {

    /**
     * Backward-compatible constructor preserving 5-argument call sites (inherits {@code immobilize}).
     */
    public CastSection(
            final @Nullable Integer ticks,
            final @Nullable String playerModel,
            final @Nullable String castAnimation,
            final @Nullable String arriveAnimation,
            final @Nullable CastCancelSection cancel
    ) {
        this(ticks, playerModel, castAnimation, arriveAnimation, null, cancel);
    }

    /**
     * Concrete, non-null resolved cast settings after merging variation overrides with global defaults (#65, #67).
     */
    public record Resolved(
            int ticks,
            String playerModel,
            String castAnimation,
            String arriveAnimation,
            boolean immobilize,
            boolean cancelOnMove,
            boolean cancelOnDamage
    ) {
        /**
         * Backward-compatible constructor preserving 6-argument call sites.
         */
        public Resolved(
                final int ticks,
                final String playerModel,
                final String castAnimation,
                final String arriveAnimation,
                final boolean cancelOnMove,
                final boolean cancelOnDamage
        ) {
            this(ticks, playerModel, castAnimation, arriveAnimation, true, cancelOnMove, cancelOnDamage);
        }

        /**
         * Returns whether casting is enabled ({@code ticks > 0}).
         */
        public boolean hasCastTime() {
            return ticks > 0;
        }
    }

    /**
     * Cast cancellation conditions (#65).
     */
    @ConfigSerializable
    public record CastCancelSection(
            @Setting("on-move")
            @Comment("Cancel casting when the player moves (head rotation is allowed; in variations: null to inherit global)")
            @Nullable Boolean onMove,

            @Setting("on-damage")
            @Comment("Cancel casting when the player takes damage (in variations: null to inherit global)")
            @Nullable Boolean onDamage
    ) {
        /**
         * Returns the global default cancellation settings.
         */
        public static CastCancelSection defaults() {
            return new CastCancelSection(true, true);
        }

        /**
         * Returns an inherit-all cancellation section for variations.
         */
        public static CastCancelSection inherit() {
            return new CastCancelSection(null, null);
        }

        /**
         * Returns {@code onMove} if configured, otherwise {@code fallback}.
         */
        public boolean onMoveOrDefault(final boolean fallback) {
            return onMove != null ? onMove : fallback;
        }

        /**
         * Returns {@code onDamage} if configured, otherwise {@code fallback}.
         */
        public boolean onDamageOrDefault(final boolean fallback) {
            return onDamage != null ? onDamage : fallback;
        }
    }

    /**
     * Returns the global default cast section (0 ticks = instant warp, immobilize = true).
     */
    public static CastSection defaults() {
        return new CastSection(0, "steve", "", "", true, CastCancelSection.defaults());
    }

    /**
     * Returns an inherit-all cast section for variations.
     */
    public static CastSection inherit() {
        return new CastSection(-1, null, null, null, null, CastCancelSection.inherit());
    }

    /**
     * Returns {@code ticks} if >= 0, otherwise {@code fallback}.
     */
    public int ticksOrDefault(final int fallback) {
        return (ticks != null && ticks >= 0) ? ticks : fallback;
    }

    /**
     * Returns {@code playerModel} if non-null, otherwise {@code fallback}.
     */
    public String playerModelOrDefault(final String fallback) {
        return playerModel != null ? playerModel : fallback;
    }

    /**
     * Returns {@code castAnimation} if non-null, otherwise {@code fallback}.
     */
    public String castAnimationOrDefault(final String fallback) {
        return castAnimation != null ? castAnimation : fallback;
    }

    /**
     * Returns {@code arriveAnimation} if non-null, otherwise {@code fallback}.
     */
    public String arriveAnimationOrDefault(final String fallback) {
        return arriveAnimation != null ? arriveAnimation : fallback;
    }

    /**
     * Returns {@code immobilize} if non-null, otherwise {@code fallback}.
     */
    public boolean immobilizeOrDefault(final boolean fallback) {
        return immobilize != null ? immobilize : fallback;
    }

    /**
     * Returns {@code cancel.onMove} if non-null, otherwise {@code fallback}.
     */
    public boolean cancelOnMoveOrDefault(final boolean fallback) {
        return cancel != null ? cancel.onMoveOrDefault(fallback) : fallback;
    }

    /**
     * Returns {@code cancel.onDamage} if non-null, otherwise {@code fallback}.
     */
    public boolean cancelOnDamageOrDefault(final boolean fallback) {
        return cancel != null ? cancel.onDamageOrDefault(fallback) : fallback;
    }

    /**
     * Resolves this (potentially partial/inheriting) cast section against {@code global} defaults.
     */
    public Resolved resolve(final CastSection global) {
        final int globalTicks = global.ticksOrDefault(0);
        final String globalModel = global.playerModelOrDefault("steve");
        final String globalCastAnim = global.castAnimationOrDefault("");
        final String globalArriveAnim = global.arriveAnimationOrDefault("");
        final boolean globalImmobilize = global.immobilizeOrDefault(true);
        final boolean globalOnMove = global.cancelOnMoveOrDefault(true);
        final boolean globalOnDamage = global.cancelOnDamageOrDefault(true);
        return new Resolved(
                ticksOrDefault(globalTicks),
                playerModelOrDefault(globalModel),
                castAnimationOrDefault(globalCastAnim),
                arriveAnimationOrDefault(globalArriveAnim),
                immobilizeOrDefault(globalImmobilize),
                cancelOnMoveOrDefault(globalOnMove),
                cancelOnDamageOrDefault(globalOnDamage)
        );
    }

    /**
     * Resolves this global cast section into concrete settings.
     */
    public Resolved resolveGlobal() {
        return resolve(defaults());
    }
}
