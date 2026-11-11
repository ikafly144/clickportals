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
package net.sabafly.clickportals.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import net.sabafly.clickportals.variation.AetheryteVariation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Resolves safe landing coordinates around or on top of an Aetheryte (#41).
 */
public final class LandingResolver {

    private static final int[] VERTICAL_SEARCH_OFFSETS = {0, 1, -1, 2, -2};

    /**
     * Classification of a block for landing safety evaluation.
     */
    public enum BlockKind {
        /**
         * Solid, non-hazardous block suitable as a floor to stand on.
         */
        SOLID_SAFE,
        /**
         * Passable, non-hazardous block suitable for the player's feet/head space.
         */
        PASSABLE_SAFE,
        /**
         * Solid obstruction or hazardous block (lava, fire, magma, cactus, etc.).
         */
        OBSTRUCTED_OR_HAZARD,
        /**
         * Block is in a chunk not owned by the current Folia region thread or outside world bounds.
         */
        UNAVAILABLE
    }

    /**
     * Abstraction for querying block safety at integer coordinates without requiring a live Bukkit world in unit tests.
     */
    @FunctionalInterface
    public interface BlockProbe {
        BlockKind kind(int x, int y, int z);
    }

    /**
     * Resolved landing position and orientation.
     */
    public record LandingSpot(double x, double y, double z, float yaw, float pitch) {
        /**
         * Converts this landing spot into a Bukkit {@link Location} in the given world.
         */
        public Location toLocation(final World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    private LandingResolver() {
    }

    /**
     * Resolves a safe landing {@link Location} for the given world, Aetheryte origin, and {@link AetheryteVariation}.
     */
    public static Optional<Location> resolveForVariation(
            final World world,
            final int originX,
            final int originY,
            final int originZ,
            final float playerYaw,
            final float playerPitch,
            final AetheryteVariation variation
    ) {
        final BlockProbe probe = worldProbe(world);
        final int minRadius = variation.teleport().minRadius();
        final int maxRadius = variation.teleport().maxRadius();
        final boolean faceAetheryte = variation.teleport().faceAetheryte();
        final double hitboxHalfWidth = Math.max(0.0, (variation.hitbox().width() - 1.0) * 0.5);

        return resolve(
                probe,
                originX,
                originY,
                originZ,
                minRadius,
                maxRadius,
                hitboxHalfWidth,
                faceAetheryte,
                playerYaw,
                playerPitch,
                ThreadLocalRandom.current()
        ).map(spot -> spot.toLocation(world));
    }

    /**
     * Resolves a safe {@link LandingSpot} inside a ring area ({@code minRadius..maxRadius}) using the given
     * {@link BlockProbe} and random generator (#41).
     */
    public static Optional<LandingSpot> resolve(
            final BlockProbe probe,
            final int originX,
            final int originY,
            final int originZ,
            final int minRadius,
            final int maxRadius,
            final double hitboxHalfWidth,
            final boolean faceAetheryte,
            final float fallbackYaw,
            final float fallbackPitch,
            final RandomGenerator rng
    ) {
        final int clampedMaxRadius = Math.clamp(maxRadius, 0, AetheryteVariation.MAX_HORIZONTAL_RADIUS);
        final int clampedMinRadius = Math.clamp(minRadius, 0, clampedMaxRadius);
        if (clampedMaxRadius > 0) {
            final List<int[]> candidateColumns = new ArrayList<>();
            final int maxDistSq = clampedMaxRadius * clampedMaxRadius;
            final double effectiveMinRadius = Math.max((double) clampedMinRadius, Math.max(0.5, hitboxHalfWidth));
            final double minDistSq = effectiveMinRadius * effectiveMinRadius;

            for (int dx = -clampedMaxRadius; dx <= clampedMaxRadius; dx++) {
                for (int dz = -clampedMaxRadius; dz <= clampedMaxRadius; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    final int distSq = dx * dx + dz * dz;
                    if (distSq > maxDistSq || distSq < minDistSq) {
                        continue;
                    }
                    final int cx = originX + dx;
                    final int cz = originZ + dz;
                    for (final int dy : VERTICAL_SEARCH_OFFSETS) {
                        final int feetY = originY + dy;
                        if (isSafeColumn(probe, cx, feetY, cz)) {
                            candidateColumns.add(new int[]{cx, feetY, cz});
                            break;
                        }
                    }
                }
            }

            if (!candidateColumns.isEmpty()) {
                final int[] chosen = candidateColumns.get(rng.nextInt(candidateColumns.size()));
                final double landingX = chosen[0] + 0.5;
                final double landingY = chosen[1];
                final double landingZ = chosen[2] + 0.5;
                final float yaw = faceAetheryte
                        ? computeYawFacing(landingX, landingZ, originX + 0.5, originZ + 0.5)
                        : fallbackYaw;
                return Optional.of(new LandingSpot(landingX, landingY, landingZ, yaw, fallbackPitch));
            }
        }

        // Fallback (or maxRadius == 0): check the origin column itself
        return resolveOriginFallback(probe, originX, originY, originZ, fallbackYaw, fallbackPitch);
    }

    /**
     * Checks whether the column at {@code (x, z)} has a solid safe floor at {@code feetY - 1} and two passable safe
     * blocks at {@code feetY} and {@code feetY + 1}.
     */
    private static boolean isSafeColumn(final BlockProbe probe, final int x, final int feetY, final int z) {
        final BlockKind floor = probe.kind(x, feetY - 1, z);
        if (floor != BlockKind.SOLID_SAFE) {
            return false;
        }
        final BlockKind feet = probe.kind(x, feetY, z);
        if (feet != BlockKind.PASSABLE_SAFE) {
            return false;
        }
        final BlockKind head = probe.kind(x, feetY + 1, z);
        return head == BlockKind.PASSABLE_SAFE;
    }

    /**
     * Resolves a safe landing spot directly on top of the origin column if available.
     */
    private static Optional<LandingSpot> resolveOriginFallback(
            final BlockProbe probe,
            final int originX,
            final int originY,
            final int originZ,
            final float yaw,
            final float pitch
    ) {
        final BlockKind origin = probe.kind(originX, originY, originZ);
        final BlockKind above1 = probe.kind(originX, originY + 1, originZ);
        // Case 1: Origin itself is passable (Marker entity style) and has a safe support block below
        if (origin == BlockKind.PASSABLE_SAFE && above1 == BlockKind.PASSABLE_SAFE) {
            final BlockKind floor = probe.kind(originX, originY - 1, originZ);
            if (floor == BlockKind.SOLID_SAFE) {
                return Optional.of(new LandingSpot(originX + 0.5, originY + 1.0, originZ + 0.5, yaw, pitch));
            }
        }
        // Case 2: Origin is a solid safe block (standing on top of the display hitbox at originY + 1.0)
        final BlockKind above2 = probe.kind(originX, originY + 2, originZ);
        if (origin == BlockKind.SOLID_SAFE && above1 == BlockKind.PASSABLE_SAFE && above2 == BlockKind.PASSABLE_SAFE) {
            return Optional.of(new LandingSpot(originX + 0.5, originY + 1.0, originZ + 0.5, yaw, pitch));
        }
        return Optional.empty();
    }

    /**
     * Computes the Minecraft yaw angle (in degrees, {@code [-180, 180]}) at {@code (fromX, fromZ)} looking toward
     * {@code (targetX, targetZ)}.
     */
    public static float computeYawFacing(
            final double fromX,
            final double fromZ,
            final double targetX,
            final double targetZ
    ) {
        final double dx = targetX - fromX;
        final double dz = targetZ - fromZ;
        if (dx == 0.0 && dz == 0.0) {
            return 0.0f;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * Creates a Folia-safe {@link BlockProbe} backed by a Bukkit {@link World}.
     */
    public static BlockProbe worldProbe(final World world) {
        return (x, y, z) -> {
            if (!isChunkOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
                return BlockKind.UNAVAILABLE;
            }
            final Block block = world.getBlockAt(x, y, z);
            final Material type = block.getType();
            if (isHazardousMaterial(type)) {
                return BlockKind.OBSTRUCTED_OR_HAZARD;
            }
            if (!isAir(type) && block.isSolid()) {
                return BlockKind.SOLID_SAFE;
            }
            if (isAir(type) || block.isPassable() || !block.isSolid()) {
                return BlockKind.PASSABLE_SAFE;
            }
            return BlockKind.OBSTRUCTED_OR_HAZARD;
        };
    }

    private static boolean isAir(final Material type) {
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }

    /**
     * Checks whether the chunk at {@code (chunkX, chunkZ)} is owned by the current Folia region thread.
     */
    static boolean isChunkOwnedByCurrentRegion(final World world, final int chunkX, final int chunkZ) {
        try {
            if (Bukkit.getServer() == null) {
                return true;
            }
            return Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ);
        } catch (final Throwable ignored) {
            return true;
        }
    }

    /**
     * Checks whether the given block material causes contact/environmental damage when standing on or inside it.
     */
    private static boolean isHazardousMaterial(final Material type) {
        return type == Material.LAVA
                || type == Material.FIRE
                || type == Material.SOUL_FIRE
                || type == Material.MAGMA_BLOCK
                || type == Material.CACTUS
                || type == Material.CAMPFIRE
                || type == Material.SOUL_CAMPFIRE
                || type == Material.SWEET_BERRY_BUSH
                || type == Material.WITHER_ROSE
                || type == Material.POWDER_SNOW;
    }
}
