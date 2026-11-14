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

import net.sabafly.clickportals.aetheryte.AetheryteVisibility;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/**
 * Cost multipliers keyed by {@link AetheryteVisibility} (#25).
 * In variation placement overrides, {@code null} fields inherit from global {@code config.yml}.
 */
@ConfigSerializable
public record VisibilityCostSection(
        @Setting("public-multiplier")
        @Comment("Cost multiplier applied to PUBLIC Aetherytes (>= 0.0; null in variation to inherit)")
        @Nullable Double publicMultiplier,

        @Setting("invite-only-multiplier")
        @Comment("Cost multiplier applied to INVITE_ONLY Aetherytes (>= 0.0; null in variation to inherit)")
        @Nullable Double inviteOnlyMultiplier,

        @Setting("owner-only-multiplier")
        @Comment("Cost multiplier applied to OWNER_ONLY Aetherytes (>= 0.0; null in variation to inherit)")
        @Nullable Double ownerOnlyMultiplier
) {

    public static final double DEFAULT_MULTIPLIER = 1.0;

    public static VisibilityCostSection defaults() {
        return new VisibilityCostSection(DEFAULT_MULTIPLIER, DEFAULT_MULTIPLIER, DEFAULT_MULTIPLIER);
    }

    public static VisibilityCostSection inherit() {
        return new VisibilityCostSection(null, null, null);
    }

    public VisibilityCostSection validatedGlobal(final Logger logger) {
        return new VisibilityCostSection(
                validateMultiplier("warp.visibility-cost.public-multiplier", publicMultiplier, DEFAULT_MULTIPLIER, logger),
                validateMultiplier("warp.visibility-cost.invite-only-multiplier", inviteOnlyMultiplier, DEFAULT_MULTIPLIER, logger),
                validateMultiplier("warp.visibility-cost.owner-only-multiplier", ownerOnlyMultiplier, DEFAULT_MULTIPLIER, logger)
        );
    }

    public VisibilityCostSection validatedOverride(final String prefix, final Logger logger) {
        return new VisibilityCostSection(
                validateNullableMultiplier(prefix + ".public-multiplier", publicMultiplier, logger),
                validateNullableMultiplier(prefix + ".invite-only-multiplier", inviteOnlyMultiplier, logger),
                validateNullableMultiplier(prefix + ".owner-only-multiplier", ownerOnlyMultiplier, logger)
        );
    }

    public VisibilityCostSection resolve(final VisibilityCostSection global) {
        final double pub = publicMultiplier != null
                ? publicMultiplier
                : (global.publicMultiplier() != null ? global.publicMultiplier() : DEFAULT_MULTIPLIER);
        final double inv = inviteOnlyMultiplier != null
                ? inviteOnlyMultiplier
                : (global.inviteOnlyMultiplier() != null ? global.inviteOnlyMultiplier() : DEFAULT_MULTIPLIER);
        final double own = ownerOnlyMultiplier != null
                ? ownerOnlyMultiplier
                : (global.ownerOnlyMultiplier() != null ? global.ownerOnlyMultiplier() : DEFAULT_MULTIPLIER);
        return new VisibilityCostSection(pub, inv, own);
    }

    public double multiplierFor(final AetheryteVisibility visibility) {
        final @Nullable Double val = switch (visibility) {
            case PUBLIC -> publicMultiplier;
            case INVITE_ONLY -> inviteOnlyMultiplier;
            case OWNER_ONLY -> ownerOnlyMultiplier;
        };
        if (val == null || !Double.isFinite(val) || val < 0.0) {
            return DEFAULT_MULTIPLIER;
        }
        return val;
    }

    private static double validateMultiplier(
            final String path,
            final @Nullable Double raw,
            final double fallback,
            final Logger logger
    ) {
        if (raw == null) {
            return fallback;
        }
        if (!Double.isFinite(raw) || raw < 0.0) {
            logger.warn("Invalid {} {} (must be >= 0.0); falling back to {}", path, raw, fallback);
            return fallback;
        }
        return raw;
    }

    private static @Nullable Double validateNullableMultiplier(
            final String path,
            final @Nullable Double raw,
            final Logger logger
    ) {
        if (raw == null) {
            return null;
        }
        if (!Double.isFinite(raw) || raw < 0.0) {
            logger.warn("Invalid {} {} (must be >= 0.0); falling back to inherit global", path, raw);
            return null;
        }
        return raw;
    }
}
