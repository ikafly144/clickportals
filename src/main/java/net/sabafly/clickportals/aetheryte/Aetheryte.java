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
package net.sabafly.clickportals.aetheryte;

import java.util.Locale;
import java.util.OptionalDouble;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * Immutable domain model representing a placed Aetheryte warp point (#6, #12, #37).
 */
public record Aetheryte(
        UUID id,
        String name,
        UUID worldId,
        String worldName,
        int blockX,
        int blockY,
        int blockZ,
        UUID ownerId,
        long createdAt,
        String variation
) {

    /**
     * Identifier of the default Aetheryte variation (#37).
     */
    public static final String DEFAULT_VARIATION = "default";

    /**
     * Compact constructor normalizing null or blank variation identifiers to {@link #DEFAULT_VARIATION}.
     */
    public Aetheryte {
        variation = normalizeVariation(variation);
    }

    /**
     * Convenience constructor using {@link #DEFAULT_VARIATION}.
     */
    public Aetheryte(
            final UUID id,
            final String name,
            final UUID worldId,
            final String worldName,
            final int blockX,
            final int blockY,
            final int blockZ,
            final UUID ownerId,
            final long createdAt
    ) {
        this(id, name, worldId, worldName, blockX, blockY, blockZ, ownerId, createdAt, DEFAULT_VARIATION);
    }

    /**
     * Normalizes a variation identifier to lowercase or returns {@link #DEFAULT_VARIATION} if null/blank.
     */
    public static String normalizeVariation(@Nullable final String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_VARIATION;
        }
        return raw.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the {@link BlockPosKey} corresponding to this Aetheryte's block coordinates.
     */
    public BlockPosKey posKey() {
        return new BlockPosKey(worldId, blockX, blockY, blockZ);
    }

    /**
     * Returns a Bukkit {@link Location} pointing to the Aetheryte's block origin.
     */
    public Location blockLocation(final World world) {
        return new Location(world, blockX, blockY, blockZ);
    }

    /**
     * Returns the Bukkit {@link Location} where the backing {@link org.bukkit.entity.Marker} entity is spawned.
     */
    public Location markerLocation(final World world) {
        return new Location(world, blockX + 0.5, blockY, blockZ + 0.5);
    }

    /**
     * Returns the landing {@link Location} centered at the Aetheryte position.
     */
    public Location teleportDestination(final World world, final float yaw, final float pitch) {
        return new Location(world, blockX + 0.5, blockY, blockZ + 0.5, yaw, pitch);
    }

    /**
     * Computes the 3D Euclidean block distance to another Aetheryte if both are in the same world.
     */
    public OptionalDouble distanceTo(final Aetheryte other) {
        if (!this.worldId.equals(other.worldId)) {
            return OptionalDouble.empty();
        }
        final double dx = this.blockX - other.blockX;
        final double dy = this.blockY - other.blockY;
        final double dz = this.blockZ - other.blockZ;
        return OptionalDouble.of(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }
}
