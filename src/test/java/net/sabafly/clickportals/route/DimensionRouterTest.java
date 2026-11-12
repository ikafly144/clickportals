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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.CostType;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DimensionRouterTest {

    private World worldA;
    private World worldB;
    private World worldC;
    private World worldUnconnected;
    private DimensionRouter router;

    @BeforeEach
    void setUp() {
        worldA = mock(World.class);
        worldB = mock(World.class);
        worldC = mock(World.class);
        worldUnconnected = mock(World.class);

        when(worldA.getName()).thenReturn("world_a");
        when(worldB.getName()).thenReturn("world_b");
        when(worldC.getName()).thenReturn("world_c");
        when(worldUnconnected.getName()).thenReturn("world_isolated");

        final DimensionRoutesConfig config = new DimensionRoutesConfig(
                List.of(CostEntry.of(CostType.VAULT, "$amount")),
                Map.of(
                        "world_a", 0.0,
                        "world_b", 30.0,
                        "world_c", 100.0
                ),
                List.of(
                        new RouteEntryConfig(
                                List.of("world_a", "world_b"),
                                Map.of(
                                        "world_a", OriginConfig.scale(8.0),
                                        "world_b", OriginConfig.scale(0.125)
                                ),
                                Map.of()
                        ),
                        new RouteEntryConfig(
                                List.of("world_a", "world_c"),
                                Map.of(
                                        "world_a", OriginConfig.scale(1.0),
                                        "world_c", OriginConfig.fixed(0.0, 0.0)
                                ),
                                Map.of(
                                        "world_c", CostOverrideConfig.of("$default + 300")
                                )
                        )
                )
        );
        router = new DimensionRouter(config);
    }

    @Test
    void directRouteComputesCorrectCostAndScaledOrigin() {
        // A -> B: cost = A(0) + B(30) = 30. Origin scaled by 0.125
        final Location src = new Location(worldA, 800.0, 64.0, 1600.0);
        final Optional<RoutePlan> plan = router.findRoute(worldA, worldB, src);

        assertThat(plan).isPresent();
        assertThat(plan.get().hops()).isEqualTo(1);
        assertThat(plan.get().worldPath()).containsExactly("world_a", "world_b");
        assertThat(plan.get().dimensionCostAmount()).isEqualTo(30.0);
        assertThat(plan.get().targetOrigin().getX()).isEqualTo(100.0);
        assertThat(plan.get().targetOrigin().getZ()).isEqualTo(200.0);
    }

    @Test
    void multiHopRouteComputesCombinedCostWithOverrideAndOrigin() {
        // B -> C (via A):
        // B -> A: cost = 30 + 0 = 30. (Scaled by 8.0)
        // A -> C: default = 0 + 100 = 100, override = 100 + 300 = 400. (Fixed 0, 0)
        // Total cost: 30 + 400 = 430
        final Location src = new Location(worldB, 100.0, 64.0, 200.0);
        final Optional<RoutePlan> plan = router.findRoute(worldB, worldC, src);

        assertThat(plan).isPresent();
        assertThat(plan.get().hops()).isEqualTo(2);
        assertThat(plan.get().worldPath()).containsExactly("world_b", "world_a", "world_c");
        assertThat(plan.get().dimensionCostAmount()).isEqualTo(430.0);
        assertThat(plan.get().targetOrigin().getX()).isEqualTo(0.0);
        assertThat(plan.get().targetOrigin().getZ()).isEqualTo(0.0);
    }

    @Test
    void unconnectedWorldReturnsEmpty() {
        final Location src = new Location(worldA, 0.0, 64.0, 0.0);
        assertThat(router.findRoute(worldA, worldUnconnected, src)).isEmpty();
    }
}
