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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.cost.CostBundle;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.CostItemQuote;
import net.sabafly.clickportals.cost.CostQuote;
import net.sabafly.clickportals.cost.CostType;
import net.sabafly.clickportals.economy.EconomyProvider;
import net.sabafly.clickportals.item.AetheryteItemFactory;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.PlacementSection;
import net.sabafly.clickportals.variation.VariationRegistry;
import net.sabafly.clickportals.visual.HologramService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlacementCostTest {

    private Plugin plugin;
    private AetheryteRegistry registry;
    private VariationRegistry variationRegistry;
    private HologramService hologramService;
    private Messages messages;
    private EconomyProvider economy;
    private AetheryteService service;

    private Player player;
    private World world;
    private UUID worldId;
    private UUID playerId;
    private PlayerInventory inventory;
    private EntityScheduler scheduler;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        registry = new AetheryteRegistry();
        variationRegistry = mock(VariationRegistry.class);
        when(variationRegistry.getOrDefault(any())).thenReturn(AetheryteVariation.defaultVariation());
        hologramService = mock(HologramService.class);
        messages = Messages.defaults();
        economy = mock(EconomyProvider.class);

        player = mock(Player.class);
        world = mock(World.class);
        worldId = UUID.randomUUID();
        playerId = UUID.randomUUID();
        inventory = mock(PlayerInventory.class);
        scheduler = mock(EntityScheduler.class);

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getScheduler()).thenReturn(scheduler);
        when(player.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(player.hasPermission(Permissions.PLACE)).thenReturn(true);

        when(world.getUID()).thenReturn(worldId);
        when(world.getName()).thenReturn("world");
        when(world.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));

        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(player)).thenReturn(500.0);
        when(economy.has(eq(player), anyDouble())).thenAnswer(inv -> (double) inv.getArgument(1) <= 500.0);
        when(economy.withdraw(eq(player), anyDouble())).thenReturn(true);
        when(economy.format(anyDouble())).thenAnswer(inv -> String.format("$%.2f", (double) inv.getArgument(0)));

        when(player.calculateTotalExperiencePoints()).thenReturn(100);
        when(player.getLevel()).thenReturn(10);
        when(player.getFoodLevel()).thenReturn(20);

        // Immediate scheduler execution for testing entity-thread steps
        when(plugin.getSLF4JLogger()).thenReturn(org.slf4j.LoggerFactory.getLogger("PlacementCostTest"));
        when(scheduler.run(eq(plugin), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            final Consumer<Object> consumer = (Consumer<Object>) inv.getArgument(1);
            consumer.accept(null);
            return mock(io.papermc.paper.threadedregions.scheduler.ScheduledTask.class);
        });

        service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                () -> -1,
                () -> 0,
                p -> 0L,
                (p, t) -> {},
                (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty())
        );
        service.setEconomyProvider(economy);
    }

    @Test
    void rejectsPlacementWhenPlayerHasInsufficientFunds() {
        final AetheryteVariation expensiveVariation = new AetheryteVariation(
                "<aqua>Costly</aqua>",
                null, null, null, null, null,
                new PlacementSection(null, List.of(CostEntry.of(CostType.VAULT, "1000"))), // costs 1000, has 500
                null
        );
        when(variationRegistry.getOrDefault("costly")).thenReturn(expensiveVariation);

        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                () -> -1,
                () -> 0,
                p -> 0L,
                (p, t) -> {},
                (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty())
        );
        service.setEconomyProvider(economy);

        service.placeAetheryte(player, world, 10, 64, 20, "CostlyPortal", "costly");

        // Should not withdraw money and should not consume items
        verify(economy, never()).withdraw(any(), anyDouble());
        verify(inventory, never()).setItem(any(int.class), any(ItemStack.class));

        final ArgumentCaptor<Component> msgCaptor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(msgCaptor.capture());
        assertThat(msgCaptor.getValue()).isNotNull();
    }

    @Test
    void bypassPermissionExemptsPlayerFromPlacementCost() {
        when(player.hasPermission(Permissions.BYPASS_COST_PLACE)).thenReturn(true);

        final AetheryteVariation expensiveVariation = new AetheryteVariation(
                "<aqua>Costly</aqua>",
                null, null, null, null, null,
                new PlacementSection(null, List.of(CostEntry.of(CostType.VAULT, "1000"))),
                null
        );
        when(variationRegistry.getOrDefault("costly")).thenReturn(expensiveVariation);

        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                () -> -1,
                () -> 0,
                p -> 0L,
                (p, t) -> {},
                (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty())
        );
        service.setEconomyProvider(economy);

        service.placeAetheryte(player, world, 10, 64, 20, "CostlyPortal", "costly");

        // With bypass, economy is not charged
        verify(economy, never()).withdraw(any(), anyDouble());
    }

    @Test
    void creativeModeExemptsPlayerFromPlacementCostEvenWithZeroFunds() throws Exception {
        final org.bukkit.Server server = mock(org.bukkit.Server.class);
        final java.lang.reflect.Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
        try {
            final io.papermc.paper.threadedregions.scheduler.RegionScheduler regionScheduler =
                    mock(io.papermc.paper.threadedregions.scheduler.RegionScheduler.class);
            when(server.getRegionScheduler()).thenReturn(regionScheduler);

            when(player.getGameMode()).thenReturn(org.bukkit.GameMode.CREATIVE);
            when(player.hasPermission(Permissions.BYPASS_COST_PLACE)).thenReturn(false);
            when(economy.has(any(), anyDouble())).thenReturn(false);

            final AetheryteVariation expensiveVariation = new AetheryteVariation(
                    "<aqua>Costly</aqua>",
                    null, null, null, null, null,
                    new PlacementSection(null, List.of(CostEntry.of(CostType.VAULT, "1000"))),
                    null
            );
            when(variationRegistry.getOrDefault("costly")).thenReturn(expensiveVariation);

            final AetheryteService service = new AetheryteService(
                    plugin,
                    registry,
                    variationRegistry,
                    hologramService,
                    messages,
                    () -> -1,
                    () -> 0,
                    p -> 0L,
                    (p, t) -> {},
                    (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty())
            );
            service.setEconomyProvider(economy);

            service.placeAetheryte(player, world, 10, 64, 20, "CostlyPortal", "costly");

            verify(economy, never()).withdraw(any(), anyDouble());
            verify(regionScheduler).execute(eq(plugin), any(Location.class), any(Runnable.class));
        } finally {
            serverField.set(null, null);
        }
    }

    @Test
    void processPendingRefundsRestoresResourcesOnJoin() {
        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                () -> -1,
                () -> 0,
                p -> 0L,
                (p, t) -> {},
                (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty())
        );
        service.setEconomyProvider(economy);

        final CostBundle costBundle = new CostBundle(List.of(
                CostEntry.of(CostType.VAULT, "100")
        ));
        final CostQuote costQuote = costBundle.quote(player, Map.of(), economy);

        // Immediate deposit fails on initial refund
        when(economy.deposit(eq(player), anyDouble())).thenReturn(false);

        final Location targetLocation = new Location(world, 10, 64, 20);
        service.refundPlacement(player, targetLocation, "costly", false, false, costBundle, costQuote);

        // Deposit was attempted once during placement failure and failed, queuing into pendingRefunds
        verify(economy, times(1)).deposit(eq(player), eq(100.0));

        // When player reconnects and economy deposit succeeds
        when(economy.deposit(eq(player), anyDouble())).thenReturn(true);
        service.processPendingRefunds(player);

        // Second deposit succeeds
        verify(economy, times(2)).deposit(eq(player), eq(100.0));

        // Calling processPendingRefunds again does not deposit a third time (record was cleared)
        service.processPendingRefunds(player);
        verify(economy, times(2)).deposit(eq(player), eq(100.0));
    }

    @Test
    void processPendingRefundsPreservesNewlyAddedRefunds() {
        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                () -> -1,
                () -> 0,
                p -> 0L,
                (p, t) -> {},
                (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty())
        );
        service.setEconomyProvider(economy);

        final CostBundle bundle1 = new CostBundle(List.of(CostEntry.of(CostType.VAULT, "100")));
        final CostQuote quote1 = bundle1.quote(player, Map.of(), economy);
        final CostBundle bundle2 = new CostBundle(List.of(CostEntry.of(CostType.VAULT, "200")));
        final CostQuote quote2 = bundle2.quote(player, Map.of(), economy);

        final Location targetLocation = new Location(world, 10, 64, 20);
        final java.util.concurrent.atomic.AtomicBoolean deposit200Success = new java.util.concurrent.atomic.AtomicBoolean(false);

        // Fail immediate refund for bundle1
        when(economy.deposit(eq(player), anyDouble())).thenReturn(false);
        service.refundPlacement(player, targetLocation, "costly", false, false, bundle1, quote1);

        // When processing pending refunds for bundle1, another failure adds bundle2
        when(economy.deposit(eq(player), eq(100.0))).thenAnswer(inv -> {
            service.refundPlacement(player, targetLocation, "costly", false, false, bundle2, quote2);
            return true;
        });
        when(economy.deposit(eq(player), eq(200.0))).thenAnswer(inv -> deposit200Success.get());

        service.processPendingRefunds(player);

        // Bundle 1 was refunded: attempted once on initial placement failure and once on processPendingRefunds
        verify(economy, times(2)).deposit(eq(player), eq(100.0));
        // Bundle 2 attempted once on nested failure and failed
        verify(economy, times(1)).deposit(eq(player), eq(200.0));

        // Bundle 2 is still in pendingRefunds and is refunded on the next processing pass
        deposit200Success.set(true);
        service.processPendingRefunds(player);
        verify(economy, times(2)).deposit(eq(player), eq(200.0));
    }

    @Test
    void duplicateIdenticalPendingRefundsRemoveOnlySucceededInstance() {
        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                () -> -1,
                () -> 0,
                p -> 0L,
                (p, t) -> {},
                (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty())
        );
        service.setEconomyProvider(economy);

        final CostBundle bundle = new CostBundle(List.of(CostEntry.of(CostType.VAULT, "100")));
        final CostQuote quote1 = bundle.quote(player, Map.of(), economy);
        final CostQuote quote2 = bundle.quote(player, Map.of(), economy);
        final Location targetLocation = new Location(world, 10, 64, 20);

        // Initial deposit failures enqueue two identical items into pendingRefunds
        when(economy.deposit(eq(player), anyDouble())).thenReturn(false);
        service.refundPlacement(player, targetLocation, "costly", false, false, bundle, quote1);
        service.refundPlacement(player, targetLocation, "costly", false, false, bundle, quote2);
        verify(economy, times(2)).deposit(eq(player), eq(100.0));

        // On first retry pass, 1st refund succeeds but 2nd refund fails
        final java.util.concurrent.atomic.AtomicInteger retryCount = new java.util.concurrent.atomic.AtomicInteger(0);
        when(economy.deposit(eq(player), eq(100.0))).thenAnswer(inv -> retryCount.incrementAndGet() == 1);

        service.processPendingRefunds(player);
        verify(economy, times(4)).deposit(eq(player), eq(100.0));

        // Exactly one duplicate item must remain in pendingRefunds; refund it on the second pass
        when(economy.deposit(eq(player), eq(100.0))).thenReturn(true);
        service.processPendingRefunds(player);
        verify(economy, times(5)).deposit(eq(player), eq(100.0));

        // Pending refunds queue is now empty; further processing performs no deposits
        service.processPendingRefunds(player);
        verify(economy, times(5)).deposit(eq(player), eq(100.0));
    }

    @Test
    void evaluateWarpBypassesCostOnlyWhenAffordabilityFails() {
        final AetheryteService.WarpCostEvaluator mockEvaluator = mock(AetheryteService.WarpCostEvaluator.class);
        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                () -> -1,
                () -> 0,
                p -> 0L,
                (p, t) -> {},
                mockEvaluator
        );

        final UUID worldId = UUID.randomUUID();
        final net.sabafly.clickportals.aetheryte.Aetheryte dest =
                new net.sabafly.clickportals.aetheryte.Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 10, 64, 20, UUID.randomUUID(), 0L);

        when(player.hasPermission(Permissions.USE)).thenReturn(true);

        // When evaluator denies due to INSUFFICIENT_COST
        when(mockEvaluator.evaluate(eq(player), any(), eq(dest)))
                .thenReturn(AetheryteService.WarpEvaluation.denied(
                        Component.text("100 G"),
                        Component.text("Too poor"),
                        AetheryteService.WarpEvaluation.DenyReason.INSUFFICIENT_COST
                ));

        // Without bypass, it is denied
        when(player.hasPermission(Permissions.BYPASS_COST_WARP)).thenReturn(false);
        final AetheryteService.WarpEvaluation deniedEval = service.evaluateWarp(player, null, dest);
        assertThat(deniedEval.allowed()).isFalse();
        assertThat(deniedEval.denyReasonCode()).isEqualTo(AetheryteService.WarpEvaluation.DenyReason.INSUFFICIENT_COST);

        // With bypass, INSUFFICIENT_COST is overridden to permitted
        when(player.hasPermission(Permissions.BYPASS_COST_WARP)).thenReturn(true);
        final AetheryteService.WarpEvaluation permittedEval = service.evaluateWarp(player, null, dest);
        assertThat(permittedEval.allowed()).isTrue();
        assertThat(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(permittedEval.costDisplay()))
                .contains("100 G")
                .contains("Bypassed");

        // But NO_ROUTE or TOO_FAR is NOT bypassed even with BYPASS_COST_WARP
        when(mockEvaluator.evaluate(eq(player), any(), eq(dest)))
                .thenReturn(AetheryteService.WarpEvaluation.denied(
                        Component.text("Free"),
                        Component.text("No route"),
                        AetheryteService.WarpEvaluation.DenyReason.NO_ROUTE
                ));
        final AetheryteService.WarpEvaluation unbypassedRoute = service.evaluateWarp(player, null, dest);
        assertThat(unbypassedRoute.allowed()).isFalse();
        assertThat(unbypassedRoute.denyReasonCode()).isEqualTo(AetheryteService.WarpEvaluation.DenyReason.NO_ROUTE);
    }

    @Test
    void evaluateWarpPreservesRealCostDisplayWhenPermissionDenied() {
        final AetheryteService.WarpCostEvaluator mockEvaluator = mock(AetheryteService.WarpCostEvaluator.class);
        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                () -> -1,
                () -> 0,
                p -> 0L,
                (p, t) -> {},
                mockEvaluator
        );

        final UUID worldId = UUID.randomUUID();
        final net.sabafly.clickportals.aetheryte.Aetheryte dest =
                new net.sabafly.clickportals.aetheryte.Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 10, 64, 20, UUID.randomUUID(), 0L);

        when(player.hasPermission(Permissions.USE)).thenReturn(false);
        when(mockEvaluator.evaluate(eq(player), any(), eq(dest)))
                .thenReturn(AetheryteService.WarpEvaluation.permitted(net.kyori.adventure.text.Component.text("$85.00")));

        final AetheryteService.WarpEvaluation eval = service.evaluateWarp(player, null, dest);
        assertThat(eval.allowed()).isFalse();
        assertThat(eval.denyReasonCode()).isEqualTo(AetheryteService.WarpEvaluation.DenyReason.NO_PERMISSION);
        assertThat(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(eval.costDisplay()))
                .isEqualTo("$85.00");
    }

    @Test
    void successfulPlacementChargesCostAndSendsPaidNotification() throws Exception {
        final org.bukkit.Server server = mock(org.bukkit.Server.class);
        final java.lang.reflect.Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
        try {
            final io.papermc.paper.threadedregions.scheduler.RegionScheduler regionScheduler =
                    mock(io.papermc.paper.threadedregions.scheduler.RegionScheduler.class);
            when(server.getRegionScheduler()).thenReturn(regionScheduler);
            when(server.isOwnedByCurrentRegion(any(), any(int.class), any(int.class))).thenReturn(true);
            doAnswer(inv -> {
                ((Runnable) inv.getArgument(2)).run();
                return null;
            }).when(regionScheduler).execute(any(), any(Location.class), any());

            final Block originBlock = mock(Block.class);
            when(originBlock.getType()).thenReturn(Material.AIR);
            when(originBlock.isReplaceable()).thenReturn(true);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(originBlock);
            when(world.getBlockAt(any(Location.class))).thenReturn(originBlock);

            final ItemStack mainHand = mock(ItemStack.class);
            final PersistentDataContainer pdc = mock(PersistentDataContainer.class);
            when(mainHand.isEmpty()).thenReturn(false);
            when(mainHand.getAmount()).thenReturn(1);
            when(mainHand.getPersistentDataContainer()).thenReturn(pdc);
            when(pdc.get(AetheryteItemFactory.ITEM_MARKER_KEY, PersistentDataType.BYTE)).thenReturn((byte) 1);
            when(pdc.get(AetheryteItemFactory.VARIATION_KEY, PersistentDataType.STRING)).thenReturn("cheap");
            when(inventory.getItemInMainHand()).thenReturn(mainHand);

            final AetheryteVariation cheapVariation = new AetheryteVariation(
                    "<aqua>Cheap</aqua>",
                    null, null, null, null, null,
                    new PlacementSection(null, List.of(CostEntry.of(CostType.VAULT, "50"))),
                    null
            );
            when(variationRegistry.getOrDefault("cheap")).thenReturn(cheapVariation);

            final AetheryteService service = new AetheryteService(
                    plugin,
                    registry,
                    variationRegistry,
                    hologramService,
                    messages,
                    () -> -1,
                    () -> 0,
                    p -> 0L,
                    (p, t) -> {},
                    (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty())
            );
            service.setEconomyProvider(economy);

            service.placeAetheryte(player, world, 10, 64, 20, "CheapPortal", "cheap");

            verify(economy).withdraw(player, 50.0);
            final ArgumentCaptor<Component> msgCaptor = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(msgCaptor.capture());
            final boolean hasPaidMsg = msgCaptor.getAllValues().stream()
                    .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
                    .anyMatch(s -> s.contains("Paid") && s.contains("$50.00"));
            assertThat(hasPaidMsg).isTrue();
        } finally {
            serverField.set(null, null);
        }
    }

    @Test
    void placementIsRejectedWhenCloserThanGlobalMinDistance() {
        service.setGlobalMinDistanceSupplier(() -> 50.0);
        registry.register(world, new Aetheryte(UUID.randomUUID(), "Existing", worldId, "world", 100, 64, 100, playerId, 0L));

        final Optional<Component> err = service.checkPlacementDistance(player, world, 120, 64, 100, Aetheryte.DEFAULT_VARIATION);
        assertThat(err).isPresent();
        final String text = PlainTextComponentSerializer.plainText().serialize(err.get());
        assertThat(text).contains("too close");
        assertThat(text).contains("50.0");
    }

    @Test
    void placementSucceedsWhenBeyondGlobalMinDistance() {
        service.setGlobalMinDistanceSupplier(() -> 50.0);
        registry.register(world, new Aetheryte(UUID.randomUUID(), "Existing", worldId, "world", 100, 64, 100, playerId, 0L));

        final Optional<Component> err = service.checkPlacementDistance(player, world, 160, 64, 100, Aetheryte.DEFAULT_VARIATION);
        assertThat(err).isEmpty();
    }

    @Test
    void variationMinDistanceOverridesGlobalMinDistance() {
        service.setGlobalMinDistanceSupplier(() -> 100.0);
        final AetheryteVariation customVar = new AetheryteVariation(
                "<aqua>Custom</aqua>",
                null, null, null, null, null,
                new PlacementSection(null, List.of(), 20.0),
                null
        );
        when(variationRegistry.getOrDefault("custom")).thenReturn(customVar);
        registry.register(world, new Aetheryte(UUID.randomUUID(), "Existing", worldId, "world", 100, 64, 100, playerId, 0L));

        // Distance is 30 blocks: passes variation's 20.0 limit despite global 100.0
        assertThat(service.checkPlacementDistance(player, world, 130, 64, 100, "custom")).isEmpty();

        // Distance is 10 blocks: fails variation's 20.0 limit
        final Optional<Component> err = service.checkPlacementDistance(player, world, 110, 64, 100, "custom");
        assertThat(err).isPresent();
        assertThat(PlainTextComponentSerializer.plainText().serialize(err.get())).contains("20.0");
    }

    @Test
    void bypassPermissionAllowsPlacementCloserThanMinDistance() {
        service.setGlobalMinDistanceSupplier(() -> 50.0);
        registry.register(world, new Aetheryte(UUID.randomUUID(), "Existing", worldId, "world", 100, 64, 100, playerId, 0L));

        when(player.hasPermission("clickportals.bypass.distance")).thenReturn(true);
        assertThat(service.checkPlacementDistance(player, world, 110, 64, 100, Aetheryte.DEFAULT_VARIATION)).isEmpty();

        when(player.hasPermission("clickportals.bypass.distance")).thenReturn(false);
        when(player.hasPermission("clickportals.bypass.limit")).thenReturn(true);
        assertThat(service.checkPlacementDistance(player, world, 110, 64, 100, Aetheryte.DEFAULT_VARIATION)).isEmpty();
    }

    @Test
    void pendingRefundsPersistAcrossServerRestarts(@org.junit.jupiter.api.io.TempDir final java.nio.file.Path tempDir) {
        final java.nio.file.Path storageFile = tempDir.resolve("pending_refunds.yml");
        final PendingRefundStorage storage = new PendingRefundStorage(storageFile, null);

        final AetheryteService service1 = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                net.sabafly.clickportals.model.NoopAetheryteModelService.INSTANCE,
                messages,
                () -> -1,
                () -> 0,
                () -> 0.0,
                p -> 0L,
                (p, t) -> {},
                (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty()),
                storage
        );
        service1.setEconomyProvider(economy);

        final CostBundle costBundle = new CostBundle(List.of(
                CostEntry.of(CostType.VAULT, "75")
        ));
        final CostQuote costQuote = costBundle.quote(player, Map.of(), economy);

        // Initial refund fails, queuing to storage and persisting to file
        when(economy.deposit(eq(player), anyDouble())).thenReturn(false);
        service1.refundPlacement(player, new Location(world, 10, 64, 20), "costly", false, false, costBundle, costQuote);

        assertThat(java.nio.file.Files.exists(storageFile)).isTrue();

        // Simulate server restart: new service instance reading from same storage file
        final PendingRefundStorage newStorage = new PendingRefundStorage(storageFile, null);
        final AetheryteService restartedService = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                net.sabafly.clickportals.model.NoopAetheryteModelService.INSTANCE,
                messages,
                () -> -1,
                () -> 0,
                () -> 0.0,
                p -> 0L,
                (p, t) -> {},
                (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty()),
                newStorage
        );
        restartedService.setEconomyProvider(economy);

        // When player reconnects, deposit succeeds
        when(economy.deposit(eq(player), anyDouble())).thenReturn(true);
        restartedService.processPendingRefunds(player);

        verify(economy, times(2)).deposit(eq(player), eq(75.0));
        // Storage file deleted upon clean refund
        assertThat(java.nio.file.Files.exists(storageFile)).isFalse();
    }

    @Test
    void processPendingRefundsSkipsAlreadyProcessedTransactionIdInPdc(@org.junit.jupiter.api.io.TempDir final java.nio.file.Path tempDir) {
        final java.nio.file.Path storageFile = tempDir.resolve("pending_refunds.yml");
        final PendingRefundStorage storage = new PendingRefundStorage(storageFile, null);

        final UUID txId = UUID.randomUUID();
        final CostItemQuote quote = new CostItemQuote(CostType.VAULT, 120.0, true, true, 120.0, false, txId);
        storage.save(Map.of(playerId, List.of(quote)));
        assertThat(java.nio.file.Files.exists(storageFile)).isTrue();

        // Player PDC already records txId as processed (e.g. crash occurred after deposit before disk cleanup)
        final PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(player.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(AetheryteService.PROCESSED_REFUNDS_KEY, PersistentDataType.LIST.strings()))
                .thenReturn(List.of(txId.toString()));

        final AetheryteService restartedService = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                net.sabafly.clickportals.model.NoopAetheryteModelService.INSTANCE,
                messages,
                () -> -1,
                () -> 0,
                () -> 0.0,
                p -> 0L,
                (p, t) -> {},
                (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty()),
                storage
        );
        restartedService.setEconomyProvider(economy);

        restartedService.processPendingRefunds(player);

        // Must NOT deposit again!
        verify(economy, never()).deposit(any(), anyDouble());
        // Must clean up the storage file
        assertThat(java.nio.file.Files.exists(storageFile)).isFalse();
    }

    @Test
    void recordPendingRefundSchedulesAsyncRetryWhenSaveFails() {
        final PendingRefundStorage failingStorage = mock(PendingRefundStorage.class);
        when(failingStorage.load()).thenReturn(Map.of());
        when(failingStorage.save(any())).thenReturn(false);

        final org.bukkit.Server server = mock(org.bukkit.Server.class);
        final io.papermc.paper.threadedregions.scheduler.AsyncScheduler asyncScheduler =
                mock(io.papermc.paper.threadedregions.scheduler.AsyncScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getAsyncScheduler()).thenReturn(asyncScheduler);

        final AetheryteService customService = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                net.sabafly.clickportals.model.NoopAetheryteModelService.INSTANCE,
                messages,
                () -> -1,
                () -> 0,
                () -> 0.0,
                p -> 0L,
                (p, t) -> {},
                (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty()),
                failingStorage
        );
        customService.setEconomyProvider(economy);

        final CostBundle costBundle = new CostBundle(List.of(CostEntry.of(CostType.VAULT, "50")));
        final CostQuote costQuote = costBundle.quote(player, Map.of(), economy);
        when(economy.deposit(eq(player), anyDouble())).thenReturn(false);

        customService.refundPlacement(player, new Location(world, 10, 64, 20), "costly", false, false, costBundle, costQuote);

        verify(failingStorage, atLeastOnce()).save(any());
        verify(asyncScheduler).runDelayed(eq(plugin), any(), eq(5L), eq(java.util.concurrent.TimeUnit.SECONDS));
    }
}
