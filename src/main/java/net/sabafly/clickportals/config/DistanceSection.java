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
 * Min and max distance configuration for Aetheryte warping (#26).
 */
@ConfigSerializable
public record DistanceSection(
        @Setting("min")
        @Comment("Minimum block distance required to warp (0 for no minimum limit; in variations: -1 to inherit global)")
        @Nullable Double min,

        @Setting("max")
        @Comment("Maximum block distance allowed to warp (-1 for unlimited; in variations: -1 to inherit global)")
        @Nullable Double max
) {

    public DistanceSection(@Nullable final Double min, @Nullable final Double max) {
        this.min = min;
        this.max = max;
    }

    public double minOrDefault(final double fallback) {
        return (min != null && min >= 0.0) ? min : fallback;
    }

    public double maxOrDefault(final double fallback) {
        return (max != null && max >= 0.0) ? max : fallback;
    }

    public static DistanceSection defaults() {
        return new DistanceSection(0.0, -1.0);
    }

    public static DistanceSection inherit() {
        return new DistanceSection(-1.0, -1.0);
    }
}
