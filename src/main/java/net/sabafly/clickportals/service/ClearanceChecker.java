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

import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.ClearanceSection;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Verifies that the surrounding clearance volume around a candidate Aetheryte placement origin consists
 * entirely of replaceable blocks (#42).
 */
public final class ClearanceChecker {

    /**
     * Predicate for querying whether a block coordinate is replaceable and owned by the current region.
     */
    @FunctionalInterface
    public interface ReplaceableProbe {
        boolean isReplaceable(int x, int y, int z);
    }

    private ClearanceChecker() {
    }

    /**
     * Checks whether all blocks in the horizontal Chebyshev radius {@code [-radius, radius]} and vertical range
     * {@code [0, height - 1]} around {@code (originX, originY, originZ)} satisfy {@code probe.isReplaceable} (#42).
     */
    public static boolean hasClearance(
            final ReplaceableProbe probe,
            final int originX,
            final int originY,
            final int originZ,
            final int radius,
            final int height
    ) {
        final int clampedRadius = Math.clamp(radius, 0, AetheryteVariation.MAX_HORIZONTAL_RADIUS);
        final int clampedHeight = Math.clamp(height, 1, AetheryteVariation.MAX_CLEARANCE_HEIGHT);

        for (int dy = 0; dy < clampedHeight; dy++) {
            final int y = originY + dy;
            for (int dx = -clampedRadius; dx <= clampedRadius; dx++) {
                final int x = originX + dx;
                for (int dz = -clampedRadius; dz <= clampedRadius; dz++) {
                    final int z = originZ + dz;
                    if (!probe.isReplaceable(x, y, z)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Checks whether the target coordinates in {@code world} satisfy the clearance requirements of {@code variation}.
     */
    public static boolean hasClearance(
            final World world,
            final int originX,
            final int originY,
            final int originZ,
            final AetheryteVariation variation
    ) {
        final ClearanceSection clearance = variation.placement().clearance();
        if (clearance.radius() <= 0 && clearance.height() <= 1) {
            return isReplaceableBlock(world.getBlockAt(originX, originY, originZ));
        }
        return hasClearance(
                worldReplaceableProbe(world),
                originX,
                originY,
                originZ,
                clearance.radius(),
                clearance.height()
        );
    }

    /**
     * Creates a Folia-safe {@link ReplaceableProbe} backed by a Bukkit {@link World}.
     */
    public static ReplaceableProbe worldReplaceableProbe(final World world) {
        return (x, y, z) -> {
            if (!LandingResolver.isChunkOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
                return false;
            }
            return isReplaceableBlock(world.getBlockAt(x, y, z));
        };
    }

    /**
     * Checks whether the given block is air or replaceable (excluding liquid blocks such as water or lava).
     */
    public static boolean isReplaceableBlock(final Block block) {
        final Material type = block.getType();
        if (type == Material.WATER || type == Material.LAVA) {
            return false;
        }
        return isAir(type) || block.isReplaceable();
    }

    private static boolean isAir(final Material type) {
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }
}
