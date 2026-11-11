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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Optional;
import java.util.Random;
import net.sabafly.clickportals.service.LandingResolver.BlockKind;
import net.sabafly.clickportals.service.LandingResolver.BlockProbe;
import net.sabafly.clickportals.service.LandingResolver.LandingSpot;
import org.junit.jupiter.api.Test;

class LandingResolverTest {

    @Test
    void radiusZeroResolvesOriginColumnWhenFloorIsSolidAndAboveIsPassable() {
        // Flat floor at y = 63, passable air at y >= 64
        final BlockProbe flatWorld = (x, y, z) -> y <= 63 ? BlockKind.SOLID_SAFE : BlockKind.PASSABLE_SAFE;

        final Optional<LandingSpot> spot = LandingResolver.resolve(
                flatWorld,
                10,
                64,
                20,
                0,
                0,
                0.0,
                true,
                45.0f,
                10.0f,
                new Random(42L)
        );

        assertThat(spot).isPresent();
        assertThat(spot.get().x()).isEqualTo(10.5);
        assertThat(spot.get().y()).isEqualTo(65.0);
        assertThat(spot.get().z()).isEqualTo(20.5);
        assertThat(spot.get().yaw()).isEqualTo(45.0f);
    }

    @Test
    void surroundingRadiusSelectsSafeSpotOutsideOriginAndFacesAetheryte() {
        // Only column (12, 20) has a safe floor at y = 63; all other surrounding columns are lava/obstructed
        final BlockProbe singleSafeColumnWorld = (x, y, z) -> {
            if (x == 12 && z == 20) {
                return y == 63 ? BlockKind.SOLID_SAFE : (y >= 64 ? BlockKind.PASSABLE_SAFE : BlockKind.OBSTRUCTED_OR_HAZARD);
            }
            return BlockKind.OBSTRUCTED_OR_HAZARD;
        };

        final Optional<LandingSpot> spot = LandingResolver.resolve(
                singleSafeColumnWorld,
                10,
                64,
                20,
                1,
                3,
                0.5,
                true,
                0.0f,
                0.0f,
                new Random(1L)
        );

        assertThat(spot).isPresent();
        // Landing at (12.5, 64.0, 20.5) looking West (+90 deg yaw) toward origin (10.5, 20.5)
        assertThat(spot.get().x()).isEqualTo(12.5);
        assertThat(spot.get().y()).isEqualTo(64.0);
        assertThat(spot.get().z()).isEqualTo(20.5);
        assertThat(spot.get().yaw()).isCloseTo(90.0f, within(0.01f));
    }

    @Test
    void innerRadiusExcludesColumnsCloserThanMinRadius() {
        // Both (11, 20) at distance 1 and (13, 20) at distance 3 have safe floors, but minRadius = 2 excludes (11, 20)
        final BlockProbe twoSafeColumnsWorld = (x, y, z) -> {
            if ((x == 11 || x == 13) && z == 20) {
                return y == 63 ? BlockKind.SOLID_SAFE : (y >= 64 ? BlockKind.PASSABLE_SAFE : BlockKind.OBSTRUCTED_OR_HAZARD);
            }
            return BlockKind.OBSTRUCTED_OR_HAZARD;
        };

        final Optional<LandingSpot> spot = LandingResolver.resolve(
                twoSafeColumnsWorld,
                10,
                64,
                20,
                2,
                3,
                0.5,
                true,
                0.0f,
                0.0f,
                new Random(1L)
        );

        assertThat(spot).isPresent();
        assertThat(spot.get().x()).isEqualTo(13.5);
        assertThat(spot.get().y()).isEqualTo(64.0);
        assertThat(spot.get().z()).isEqualTo(20.5);
    }

    @Test
    void surroundingRadiusFallsBackToOriginWhenRingIsCompletelyUnavailableOrObstructed() {
        // Origin (0, 64, 0) is safe, but all surrounding columns are UNAVAILABLE (e.g. unowned Folia chunks)
        final BlockProbe originOnlyWorld = (x, y, z) -> {
            if (x == 0 && z == 0) {
                return y == 63 ? BlockKind.SOLID_SAFE : (y >= 64 ? BlockKind.PASSABLE_SAFE : BlockKind.OBSTRUCTED_OR_HAZARD);
            }
            return BlockKind.UNAVAILABLE;
        };

        final Optional<LandingSpot> fallback = LandingResolver.resolve(
                originOnlyWorld,
                0,
                64,
                0,
                1,
                2,
                0.5,
                true,
                -30.0f,
                5.0f,
                new Random(1L)
        );

        assertThat(fallback).isPresent();
        assertThat(fallback.get().x()).isEqualTo(0.5);
        assertThat(fallback.get().y()).isEqualTo(65.0);
        assertThat(fallback.get().z()).isEqualTo(0.5);
    }

    @Test
    void returnsEmptyWhenBothRingAndOriginAreHazardous() {
        final BlockProbe lavaWorld = (x, y, z) -> BlockKind.OBSTRUCTED_OR_HAZARD;

        final Optional<LandingSpot> spot = LandingResolver.resolve(
                lavaWorld,
                0,
                64,
                0,
                1,
                2,
                0.5,
                true,
                0.0f,
                0.0f,
                new Random(1L)
        );

        assertThat(spot).isEmpty();
    }
}
