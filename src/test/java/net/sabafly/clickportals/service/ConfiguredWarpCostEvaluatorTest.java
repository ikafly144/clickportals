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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.config.DistanceSection;
import net.sabafly.clickportals.config.PluginConfig;
import net.sabafly.clickportals.config.PluginConfig.WarpSection;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.CostType;
import net.sabafly.clickportals.economy.EconomyProvider;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.route.DimensionRouter;
import net.sabafly.clickportals.route.RoutePlan;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.VariationRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfiguredWarpCostEvaluatorTest {

    private Player player;
    private EconomyProvider economy;
    private DimensionRouter router;
    private VariationRegistry variationRegistry;
    private Messages messages;
    private PluginConfig pluginConfig;

    private World world;
    private World otherWorld;
    private UUID worldId;
    private UUID otherWorldId;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        economy = mock(EconomyProvider.class);
        router = mock(DimensionRouter.class);
        variationRegistry = mock(VariationRegistry.class);
        messages = Messages.defaults();

        world = mock(World.class);
        otherWorld = mock(World.class);
        worldId = UUID.randomUUID();
        otherWorldId = UUID.randomUUID();

        when(world.getUID()).thenReturn(worldId);
        when(world.getName()).thenReturn("world");
        when(otherWorld.getUID()).thenReturn(otherWorldId);
        when(otherWorld.getName()).thenReturn("world_nether");

        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(player)).thenReturn(1000.0);
        when(economy.has(eq(player), anyDouble())).thenAnswer(inv -> (double) inv.getArgument(1) <= 1000.0);
        when(economy.withdraw(eq(player), anyDouble())).thenReturn(true);
        when(economy.format(anyDouble())).thenAnswer(inv -> String.format("$%.2f", (double) inv.getArgument(0)));

        when(player.calculateTotalExperiencePoints()).thenReturn(100);
        when(player.getLevel()).thenReturn(10);
        when(player.getFoodLevel()).thenReturn(20);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));

        final WarpSection warpSection = new WarpSection(
                new DistanceSection(10.0, 1000.0), // min 10, max 1000
                List.of(CostEntry.of(CostType.VAULT, "$block_distance * 0.5 + 5")),
                true
        );

        pluginConfig = new PluginConfig(
                PluginConfig.LanguageSection.defaults(),
                PluginConfig.HologramSection.defaults(),
                PluginConfig.LimitsSection.defaults(),
                warpSection,
                net.sabafly.clickportals.route.DimensionRoutesConfig.defaults()
        );

        when(variationRegistry.getOrDefault(any())).thenReturn(AetheryteVariation.defaultVariation());
    }

    private ConfiguredWarpCostEvaluator createEvaluator() {
        return new ConfiguredWarpCostEvaluator(
                () -> pluginConfig,
                variationRegistry,
                () -> economy,
                () -> router,
                messages,
                id -> id.equals(worldId) ? world : (id.equals(otherWorldId) ? otherWorld : null)
        );
    }

    @Test
    void allowsWarpWithinDistanceBoundsAndComputesCost() {
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        // Distance: 100 blocks
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 100, 64, 0, UUID.randomUUID(), 0L);

        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isTrue();

        // Cost: 100 * 0.5 + 5 = 55.00
        final String costText = PlainTextComponentSerializer.plainText().serialize(eval.costDisplay());
        assertThat(costText).contains("$55.00");
    }

    @Test
    void rejectsDestinationCloserThanMinDistance() {
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        // Distance: 5 blocks (min is 10)
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 5, 64, 0, UUID.randomUUID(), 0L);

        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isFalse();
        assertThat(eval.denyReason()).isNotNull();

        final String reason = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(eval.denyReason()));
        assertThat(reason).contains("too close");
    }

    @Test
    void rejectsDestinationFartherThanMaxDistance() {
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        // Distance: 1500 blocks (max is 1000)
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 1500, 64, 0, UUID.randomUUID(), 0L);

        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isFalse();
        assertThat(eval.denyReason()).isNotNull();

        final String reason = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(eval.denyReason()));
        assertThat(reason).contains("too far");
    }

    @Test
    void rejectsWhenNoRouteConnectsDimensions() {
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        when(router.findRoute(any(), any(), any())).thenReturn(Optional.empty());

        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", otherWorldId, "world_nether", 100, 64, 0, UUID.randomUUID(), 0L);

        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isFalse();
        assertThat(eval.denyReason()).isNotNull();

        final String reason = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(eval.denyReason()));
        assertThat(reason).contains("No route connects to this dimension");
    }

    @Test
    void crossDimensionRouteAppliesDimensionCostAndOriginDistance() {
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        final Location targetOrigin = new Location(otherWorld, 0, 64, 0);
        final RoutePlan plan = new RoutePlan(
                List.of("world", "world_nether"),
                30.0,
                List.of(CostEntry.of(CostType.VAULT, "30")),
                targetOrigin
        );
        when(router.findRoute(any(), any(), any())).thenReturn(Optional.of(plan));

        // Distance from targetOrigin (0,64,0) to dest (100,64,0) = 100 blocks
        // Base cost: 100 * 0.5 + 5 = 55. Dimension cost: 30. Total: 85
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", otherWorldId, "world_nether", 100, 64, 0, UUID.randomUUID(), 0L);

        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isTrue();

        final String costText = PlainTextComponentSerializer.plainText().serialize(eval.costDisplay());
        // Both entries combined (55 + 30) or formatted items
        assertThat(costText).contains("$55.00").contains("$30.00");
    }
}
