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

import java.util.List;
import net.sabafly.clickportals.cost.CostEntry;
import org.bukkit.Location;

/**
 * Resolved route plan traversing one or more dimensions (#9).
 *
 * @param worldPath sequence of world names from source to target
 * @param dimensionCostAmount total evaluated dimension cost amount
 * @param dimensionCostEntries cost entries generated for the dimension traversal
 * @param targetOrigin origin location in target world from which distance is measured
 */
public record RoutePlan(
        List<String> worldPath,
        double dimensionCostAmount,
        List<CostEntry> dimensionCostEntries,
        Location targetOrigin
) {

    /**
     * Number of dimension hops traversed.
     */
    public int hops() {
        return Math.max(1, worldPath.size() - 1);
    }
}
