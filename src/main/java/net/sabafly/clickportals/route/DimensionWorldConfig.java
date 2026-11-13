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
package net.sabafly.clickportals.route;

import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/**
 * Per-dimension display name and Aetheryte placement/usage restrictions (#63).
 */
@ConfigSerializable
public record DimensionWorldConfig(
        @Setting("display-name")
        @Comment("Display name for this dimension in MiniMessage format (leave empty to use world name)")
        @Nullable String displayName,

        @Setting("allow-place")
        @Comment("Whether players can place new Aetherytes in this dimension")
        @Nullable Boolean allowPlace,

        @Setting("allow-use")
        @Comment("Whether players can use Aetherytes in or warp to this dimension")
        @Nullable Boolean allowUse
) {

    public DimensionWorldConfig {
        displayName = displayName == null ? "" : displayName.strip();
        allowPlace = allowPlace == null ? Boolean.TRUE : allowPlace;
        allowUse = allowUse == null ? Boolean.TRUE : allowUse;
    }

    /**
     * Returns default dimension settings (empty display name, placement allowed, usage allowed).
     */
    public static DimensionWorldConfig defaults() {
        return new DimensionWorldConfig("", Boolean.TRUE, Boolean.TRUE);
    }

    /**
     * Returns whether Aetheryte placement is enabled in this dimension.
     */
    public boolean isPlaceAllowed() {
        return !Boolean.FALSE.equals(allowPlace);
    }

    /**
     * Returns whether Aetheryte usage (warping from or to) is enabled in this dimension.
     */
    public boolean isUseAllowed() {
        return !Boolean.FALSE.equals(allowUse);
    }
}
