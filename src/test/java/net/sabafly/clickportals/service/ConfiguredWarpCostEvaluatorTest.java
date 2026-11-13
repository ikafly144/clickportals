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
import net.sabafly.clickportals.cost.CostItemQuote;
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
        when(economy.deposit(eq(player), anyDouble())).thenReturn(true);
        when(economy.format(anyDouble())).thenAnswer(inv -> String.format("$%.2f", (double) inv.getArgument(0)));

        when(player.calculateTotalExperiencePoints()).thenReturn(100);
        when(player.getLevel()).thenReturn(10);
        when(player.getFoodLevel()).thenReturn(20);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

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
        assertThat(eval.denyReasonCode()).isEqualTo(WarpEvaluation.DenyReason.TOO_CLOSE);

        final String reason = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(eval.denyReason()));
        assertThat(reason).contains("too close");

        // Real cost is calculated and displayed even when denied
        final String cost = PlainTextComponentSerializer.plainText().serialize(eval.costDisplay());
        assertThat(cost).contains("$7.50");
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
        assertThat(eval.denyReasonCode()).isEqualTo(WarpEvaluation.DenyReason.TOO_FAR);

        final String reason = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(eval.denyReason()));
        assertThat(reason).contains("too far");

        // Real cost is calculated and displayed even when denied
        final String cost = PlainTextComponentSerializer.plainText().serialize(eval.costDisplay());
        assertThat(cost).contains("$755.00");
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
        assertThat(eval.denyReasonCode()).isEqualTo(WarpEvaluation.DenyReason.NO_ROUTE);

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
        // Both entries combined (55 + 30 = 85) via cost aggregation
        assertThat(costText).contains("$85.00");
    }

    @Test
    void rejectsWhenPlayerIsOutOfReachOfSourceAetheryte() {
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        // Player moved 30 blocks away from source Aetheryte
        when(player.getLocation()).thenReturn(new Location(world, 30, 64, 0));
        when(variationRegistry.getOrDefault(any())).thenReturn(AetheryteVariation.defaultVariation());

        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 50, 64, 0, UUID.randomUUID(), 0L);

        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isFalse();
        assertThat(eval.denyReason()).isNotNull();
        final String reason = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(eval.denyReason()));
        assertThat(reason).contains("too far");

        // Real cost is calculated and displayed even when denied
        final String cost = PlainTextComponentSerializer.plainText().serialize(eval.costDisplay());
        assertThat(cost).contains("$30.00");
    }

    @Test
    void refundRestoresChargedAmountOnTeleportFailure() {
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 50, 64, 0, UUID.randomUUID(), 0L);
        when(variationRegistry.getOrDefault(any())).thenReturn(AetheryteVariation.defaultVariation());

        // Charge 50 * 0.5 + 5 = 30
        final boolean charged = evaluator.charge(player, src, dest);
        assertThat(charged).isTrue();

        evaluator.refund(player, src, dest);
        org.mockito.Mockito.verify(economy).deposit(player, 30.0);
    }

    @Test
    void variationDistanceOverrideInheritsGlobalMinWhenOnlyMaxIsSpecified() {
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        // Variation specifies max 500 but omits min (null). Global min is 10.
        final AetheryteVariation varWithOmittedMin = new AetheryteVariation(
                "Custom",
                AetheryteVariation.ItemSection.defaults(),
                AetheryteVariation.RecipeSection.defaults(),
                AetheryteVariation.ModelSection.defaults(),
                AetheryteVariation.HitboxSection.defaults(),
                AetheryteVariation.TeleportSection.defaults(),
                AetheryteVariation.PlacementSection.defaults(),
                AetheryteVariation.LimitSection.defaults(),
                new AetheryteVariation.WarpOverrideSection(new DistanceSection(null, 500.0), null)
        );
        when(variationRegistry.getOrDefault("custom")).thenReturn(varWithOmittedMin);

        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L, "custom");
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 5, 64, 0, UUID.randomUUID(), 0L);

        // Distance is 5 blocks, which is less than inherited global min 10
        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isFalse();
        assertThat(eval.denyReason()).isNotNull();
        final String reason = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(eval.denyReason()));
        assertThat(reason).contains("too close");

        // Real cost is calculated and displayed even when denied
        final String cost = PlainTextComponentSerializer.plainText().serialize(eval.costDisplay());
        assertThat(cost).contains("$7.50");
    }

    @Test
    void effectiveMinGreaterThanMaxClampsToUnlimited() {
        // Global min is 10, global max is 50 for this test
        final WarpSection warpSection = new WarpSection(
                new DistanceSection(10.0, 50.0),
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
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        // Variation specifies min 100, but omits max (inherits global max 50)
        final AetheryteVariation varWithConflictingLimits = new AetheryteVariation(
                "Custom",
                AetheryteVariation.ItemSection.defaults(),
                AetheryteVariation.RecipeSection.defaults(),
                AetheryteVariation.ModelSection.defaults(),
                AetheryteVariation.HitboxSection.defaults(),
                AetheryteVariation.TeleportSection.defaults(),
                AetheryteVariation.PlacementSection.defaults(),
                AetheryteVariation.LimitSection.defaults(),
                new AetheryteVariation.WarpOverrideSection(new DistanceSection(100.0, null), null)
        );
        when(variationRegistry.getOrDefault("custom")).thenReturn(varWithConflictingLimits);

        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L, "custom");
        // Distance is 150 blocks (would be > 50 if max wasn't clamped to -1.0)
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 150, 64, 0, UUID.randomUUID(), 0L);

        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isTrue();
    }

    @Test
    void partialRefundPushesBackOnlyUnrefundedItems() {
        final java.util.concurrent.atomic.AtomicInteger level = new java.util.concurrent.atomic.AtomicInteger(10);
        when(player.getLevel()).thenAnswer(inv -> level.get());
        org.mockito.Mockito.doAnswer(inv -> {
            level.set((int) inv.getArgument(0));
            return null;
        }).when(player).setLevel(org.mockito.ArgumentMatchers.anyInt());

        final WarpSection warpSection = new WarpSection(
                new DistanceSection(0.0, -1.0),
                List.of(
                        CostEntry.of(CostType.EXP_LEVELS, "2"),
                        CostEntry.of(CostType.VAULT, "50")
                ),
                true
        );
        pluginConfig = new PluginConfig(
                PluginConfig.LanguageSection.defaults(),
                PluginConfig.HologramSection.defaults(),
                PluginConfig.LimitsSection.defaults(),
                warpSection,
                net.sabafly.clickportals.route.DimensionRoutesConfig.defaults()
        );
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 10, 64, 0, UUID.randomUUID(), 0L);

        assertThat(evaluator.charge(player, src, dest)).isTrue();
        assertThat(level.get()).isEqualTo(8);

        // Exp refund succeeds, Vault deposit fails
        when(economy.deposit(eq(player), anyDouble())).thenReturn(false);

        final boolean refunded = evaluator.refund(player, src, dest);
        assertThat(refunded).isFalse();

        // Exp level was refunded back to player (8 + 2 = 10)
        assertThat(level.get()).isEqualTo(10);

        // Remaining unrefunded items in evaluator contains only Vault
        final List<CostItemQuote> remaining = evaluator.popPendingCharge(player, dest);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).type()).isEqualTo(CostType.VAULT);
        assertThat(remaining.get(0).amount()).isEqualTo(50.0);
    }

    @Test
    void concurrentWarpsToDifferentDestinationsDoNotOverwriteCharges() {
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest1 = new Aetheryte(UUID.randomUUID(), "Dest1", worldId, "world", 50, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest2 = new Aetheryte(UUID.randomUUID(), "Dest2", worldId, "world", 100, 64, 0, UUID.randomUUID(), 0L);

        // dest1 cost: 50 * 0.5 + 5 = 30
        assertThat(evaluator.charge(player, src, dest1)).isTrue();
        // dest2 cost: 100 * 0.5 + 5 = 55
        assertThat(evaluator.charge(player, src, dest2)).isTrue();

        // dest1 fails and refunds; must refund 30, not 55
        final boolean refunded1 = evaluator.refund(player, src, dest1);
        assertThat(refunded1).isTrue();
        org.mockito.Mockito.verify(economy).deposit(player, 30.0);

        // dest2 succeeds and commits; clears charge without refunding
        evaluator.commit(player, src, dest2);
        assertThat(evaluator.popPendingCharge(player, dest2)).isEmpty();
    }

    @Test
    void successfulTeleportClearsChargedPaymentViaCommit() {
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 50, 64, 0, UUID.randomUUID(), 0L);

        assertThat(evaluator.charge(player, src, dest)).isTrue();
        evaluator.commit(player, src, dest);

        // Charge was committed and cleared; subsequent refund or popPendingCharge is a no-op
        assertThat(evaluator.popPendingCharge(player, dest)).isEmpty();
    }

    @Test
    void crossDimensionWarpAppliesDistanceLimitsWhenCrossDimensionTrue() {
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        final Location targetOrigin = new Location(otherWorld, 0, 64, 0);
        final RoutePlan plan = new RoutePlan(
                List.of("world", "world_nether"),
                30.0,
                List.of(CostEntry.of(CostType.VAULT, "30")),
                targetOrigin
        );
        when(router.findRoute(any(), any(), any())).thenReturn(Optional.of(plan));

        // Distance is 5 blocks (min is 10)
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", otherWorldId, "world_nether", 5, 64, 0, UUID.randomUUID(), 0L);

        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isFalse();
        assertThat(eval.denyReasonCode()).isEqualTo(WarpEvaluation.DenyReason.TOO_CLOSE);
    }

    @Test
    void crossDimensionWarpIgnoresDistanceLimitsWhenCrossDimensionFalse() {
        final WarpSection warpSection = new WarpSection(
                new DistanceSection(10.0, 1000.0, false), // cross-dimension = false
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
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        final Location targetOrigin = new Location(otherWorld, 0, 64, 0);
        final RoutePlan plan = new RoutePlan(
                List.of("world", "world_nether"),
                30.0,
                List.of(CostEntry.of(CostType.VAULT, "30")),
                targetOrigin
        );
        when(router.findRoute(any(), any(), any())).thenReturn(Optional.of(plan));

        // Distance is 5 blocks (would violate min 10, but distance limits skipped for cross-dimension)
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", otherWorldId, "world_nether", 5, 64, 0, UUID.randomUUID(), 0L);

        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isTrue();
        // Cost: 5 * 0.5 + 5 = 7.50 + 30 = 37.50
        final String costText = PlainTextComponentSerializer.plainText().serialize(eval.costDisplay());
        assertThat(costText).contains("$37.50");
    }

    @Test
    void sameWorldWarpAlwaysEnforcesDistanceLimitsRegardlessOfCrossDimensionToggle() {
        final WarpSection warpSection = new WarpSection(
                new DistanceSection(10.0, 1000.0, false), // cross-dimension = false
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
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        // Same-world distance 5 blocks (min is 10)
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 5, 64, 0, UUID.randomUUID(), 0L);

        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isFalse();
        assertThat(eval.denyReasonCode()).isEqualTo(WarpEvaluation.DenyReason.TOO_CLOSE);
    }

    @Test
    void variationOverrideCrossDimensionOverridesGlobal() {
        // Global has cross-dimension: true
        final WarpSection warpSection = new WarpSection(
                new DistanceSection(10.0, 1000.0, true),
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

        // Variation overrides cross-dimension to false
        final AetheryteVariation overrideVar = new AetheryteVariation(
                "<aqua>Override</aqua>",
                null, null, null, null, null, null, null,
                new AetheryteVariation.WarpOverrideSection(new DistanceSection(-1.0, -1.0, false), null)
        );
        when(variationRegistry.getOrDefault("override")).thenReturn(overrideVar);

        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        final Location targetOrigin = new Location(otherWorld, 0, 64, 0);
        final RoutePlan plan = new RoutePlan(
                List.of("world", "world_nether"),
                30.0,
                List.of(CostEntry.of(CostType.VAULT, "30")),
                targetOrigin
        );
        when(router.findRoute(any(), any(), any())).thenReturn(Optional.of(plan));

        // Distance 5 blocks from variation "override"
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L, "override");
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", otherWorldId, "world_nether", 5, 64, 0, UUID.randomUUID(), 0L);

        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isTrue();
    }

    @Test
    void chargeFailureRetainsUnrefundedItemsForPendingRefund() {
        final PluginConfig.WarpSection warpSection = new PluginConfig.WarpSection(
                new DistanceSection(0.0, 1000.0, true),
                List.of(
                        CostEntry.of(CostType.VAULT, "40"),
                        CostEntry.of(CostType.VAULT, "30")
                ),
                true
        );
        pluginConfig = new PluginConfig(
                PluginConfig.LanguageSection.defaults(),
                PluginConfig.HologramSection.defaults(),
                PluginConfig.LimitsSection.defaults(),
                warpSection,
                net.sabafly.clickportals.route.DimensionRoutesConfig.defaults()
        );
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();

        when(economy.getBalance(player)).thenReturn(100.0);
        // First cost withdraws cleanly, second cost fails, rollback deposit fails
        when(economy.withdraw(player, 40.0)).thenReturn(true);
        when(economy.withdraw(player, 30.0)).thenReturn(false);
        when(economy.deposit(player, 40.0)).thenReturn(false);

        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 50, 64, 0, UUID.randomUUID(), 0L);

        assertThat(evaluator.charge(player, src, dest)).isFalse();
        // Active charge was aborted, so popPendingCharge is empty
        assertThat(evaluator.popPendingCharge(player, dest)).isEmpty();
        // Unrefunded item from rollback failure is exposed via popFailedChargeUnrefunded and consumed once
        final List<CostItemQuote> unrefunded = evaluator.popFailedChargeUnrefunded(player, dest);
        assertThat(unrefunded).hasSize(1);
        assertThat(unrefunded.get(0).type()).isEqualTo(CostType.VAULT);
        assertThat(unrefunded.get(0).amount()).isEqualTo(40.0);
        assertThat(evaluator.popFailedChargeUnrefunded(player, dest)).isEmpty();
    }

    @Test
    void peekPendingChargeWithCommitOnlyConsumesOnce() {
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator();
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 0, 64, 0, UUID.randomUUID(), 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 50, 64, 0, UUID.randomUUID(), 0L);

        assertThat(evaluator.charge(player, src, dest)).isTrue();
        assertThat(evaluator.charge(player, src, dest)).isTrue();

        final List<CostItemQuote> peeked = evaluator.peekPendingCharge(player, dest);
        assertThat(peeked).isNotEmpty();
        evaluator.commit(player, src, dest);

        // Second charge remains intact until committed or refunded
        assertThat(evaluator.peekPendingCharge(player, dest)).isNotEmpty();
        evaluator.commit(player, src, dest);
        assertThat(evaluator.peekPendingCharge(player, dest)).isEmpty();
    }
}
