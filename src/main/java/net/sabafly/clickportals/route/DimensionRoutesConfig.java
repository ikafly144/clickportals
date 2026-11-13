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
import java.util.Map;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.CostType;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/**
 * Configuration section governing cross-dimension routes and pricing (#9).
 */
@ConfigSerializable
public record DimensionRoutesConfig(
        @Setting("default-cost")
        @Comment("Default cost specifications applied to cross-dimension travel ($amount represents the dimension amount)")
        List<CostEntry> defaultCost,

        @Setting("amounts")
        @Comment("Base dimension amount per world name")
        Map<String, Double> amounts,

        @Setting("routes")
        @Comment("List of configured cross-dimension connections")
        List<RouteEntryConfig> routes
) {

    public DimensionRoutesConfig {
        if (defaultCost == null) {
            defaultCost = List.of(CostEntry.of(CostType.VAULT, "$amount"));
        }
        if (amounts == null) {
            amounts = Map.of();
        }
        if (routes == null) {
            routes = List.of();
        }
    }

    /**
     * Default stock vanilla dimension routes configuration.
     */
    public static DimensionRoutesConfig defaults() {
        return new DimensionRoutesConfig(
                List.of(
                        CostEntry.of(CostType.VAULT, "$amount"),
                        CostEntry.of(CostType.EXP_LEVELS, "3", false)
                ),
                Map.of(
                        "world", 0.0,
                        "world_nether", 30.0,
                        "world_the_end", 100.0
                ),
                List.of(
                        new RouteEntryConfig(
                                List.of("world", "world_nether"),
                                Map.of(
                                        "world", OriginConfig.scale(8.0),
                                        "world_nether", OriginConfig.scale(0.125)
                                ),
                                Map.of()
                        ),
                        new RouteEntryConfig(
                                List.of("world", "world_the_end"),
                                Map.of(
                                        "world", OriginConfig.spawn(),
                                        "world_the_end", OriginConfig.fixed(0.0, 0.0)
                                ),
                                Map.of(
                                        "world_the_end", CostOverrideConfig.of("$default + 300")
                                )
                        )
                )
        );
    }
}
