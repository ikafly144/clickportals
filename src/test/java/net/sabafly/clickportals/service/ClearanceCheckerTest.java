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

import net.sabafly.clickportals.service.ClearanceChecker.ReplaceableProbe;
import org.junit.jupiter.api.Test;

class ClearanceCheckerTest {

    @Test
    void passesWhenEntireClearanceVolumeIsReplaceable() {
        final ReplaceableProbe allClear = (x, y, z) -> y >= 64;

        assertThat(ClearanceChecker.hasClearance(allClear, 0, 64, 0, 1, 3)).isTrue();
    }

    @Test
    void failsWhenAnyBlockInRadiusOrHeightIsObstructed() {
        // Obstructed at (1, 66, -1), which is at dx=+1, dy=+2, dz=-1
        final ReplaceableProbe obstructedCorner = (x, y, z) -> !(x == 1 && y == 66 && z == -1);

        // Radius 1, height 3 covers dx in [-1,1], dy in [0,2], dz in [-1,1] -> includes (1, 66, -1)
        assertThat(ClearanceChecker.hasClearance(obstructedCorner, 0, 64, 0, 1, 3)).isFalse();

        // Radius 0, height 3 only checks (0, 64..66, 0) -> passes
        assertThat(ClearanceChecker.hasClearance(obstructedCorner, 0, 64, 0, 0, 3)).isTrue();

        // Radius 1, height 2 only checks y = 64..65 -> passes
        assertThat(ClearanceChecker.hasClearance(obstructedCorner, 0, 64, 0, 1, 2)).isTrue();
    }
}
