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

import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.World;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/**
 * Origin point definition used to compute distance when traversing dimensions (#9).
 */
@ConfigSerializable
public record OriginConfig(
        @Setting("type")
        @Comment("Origin type: scale (coordinate scaling), spawn (world spawn point), or fixed (fixed x/z coordinates)")
        String type,

        @Setting("ratio")
        @Comment("Scaling ratio applied to horizontal coordinates for 'scale' type (e.g. 8.0 for nether->overworld, 0.125 for overworld->nether)")
        double ratio,

        @Setting("x")
        @Comment("Fixed X coordinate for 'fixed' type")
        double x,

        @Setting("z")
        @Comment("Fixed Z coordinate for 'fixed' type")
        double z
) {

    public OriginConfig {
        if (type == null || type.isBlank()) {
            type = "scale";
        }
        if (ratio == 0.0) {
            ratio = 1.0;
        }
    }

    public static OriginConfig scale(final double ratio) {
        return new OriginConfig("scale", ratio, 0.0, 0.0);
    }

    public static OriginConfig spawn() {
        return new OriginConfig("spawn", 1.0, 0.0, 0.0);
    }

    public static OriginConfig fixed(final double x, final double z) {
        return new OriginConfig("fixed", 1.0, x, z);
    }

    /**
     * Resolves the origin point in {@code targetWorld} given incoming coordinates.
     */
    public Location resolve(final World targetWorld, final double currentX, final double currentY, final double currentZ) {
        final String mode = type.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "spawn" -> targetWorld.getSpawnLocation();
            case "fixed" -> new Location(targetWorld, x, currentY, z);
            default -> new Location(targetWorld, currentX * ratio, currentY, currentZ * ratio);
        };
    }
}
