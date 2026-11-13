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
package net.sabafly.clickportals.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sabafly.clickportals.ClickPortals;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.cost.CostItemQuote;
import net.sabafly.clickportals.cost.CostType;
import net.sabafly.clickportals.dialog.AetheryteDialogFactory;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import net.sabafly.clickportals.service.AetheryteService;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation;
import net.sabafly.clickportals.variation.VariationRegistry;
import net.sabafly.clickportals.visual.HologramService;
import net.sabafly.clickportals.visual.NoopHologramService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AetheryteListenerTest {

    private static final UUID WORLD_ID = UUID.fromString("12345678-1234-1234-1234-1234567890ab");

    private Plugin plugin;
    private AetheryteRegistry registry;
    private AetheryteService service;
    private AetheryteListener listener;
    private World world;
    private Block aetheryteBlock;
    private Block normalBlock;
    private Aetheryte registeredAetheryte;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        when(plugin.getSLF4JLogger()).thenReturn(mock(org.slf4j.Logger.class));
        registry = new AetheryteRegistry();
        service = new AetheryteService(plugin, registry, NoopHologramService.INSTANCE);
        final AetheryteDialogFactory dialogFactory = new AetheryteDialogFactory(service);
        listener = new AetheryteListener(plugin, registry, service, dialogFactory, NoopHologramService.INSTANCE, Messages.defaults());

        world = mock(World.class);
        when(world.getUID()).thenReturn(WORLD_ID);
        when(world.getName()).thenReturn("world");
        when(world.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));

        aetheryteBlock = mock(Block.class);
        when(aetheryteBlock.getWorld()).thenReturn(world);
        when(aetheryteBlock.getX()).thenReturn(5);
        when(aetheryteBlock.getY()).thenReturn(64);
        when(aetheryteBlock.getZ()).thenReturn(5);

        normalBlock = mock(Block.class);
        when(normalBlock.getWorld()).thenReturn(world);
        when(normalBlock.getX()).thenReturn(6);
        when(normalBlock.getY()).thenReturn(64);
        when(normalBlock.getZ()).thenReturn(5);

        registeredAetheryte = new Aetheryte(
                UUID.randomUUID(),
                "保護テスト用",
                WORLD_ID,
                "world",
                5,
                64,
                5,
                UUID.randomUUID(),
                1000L
        );
        registry.register(world, registeredAetheryte);
    }

    @Test
    void blockBreakOnAetheryteIsCancelled() {
        final Player player = mock(Player.class);
        final BlockBreakEvent breakEvent = new BlockBreakEvent(aetheryteBlock, player);

        listener.onBlockBreak(breakEvent);

        assertThat(breakEvent.isCancelled()).isTrue();
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    void blockPlaceIntoOccupiedAetherytePositionIsCancelled() {
        final Player player = mock(Player.class);
        final ItemStack regularBlockItem = mock(ItemStack.class);
        when(regularBlockItem.isEmpty()).thenReturn(true);

        final BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                aetheryteBlock,
                mock(BlockState.class),
                normalBlock,
                regularBlockItem,
                player,
                true,
                EquipmentSlot.HAND
        );

        listener.onBlockPlace(placeEvent);

        assertThat(placeEvent.isCancelled()).isTrue();
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    void entityExplosionExcludesAetheryteBlock() {
        final List<Block> explodedBlocks = new ArrayList<>(List.of(aetheryteBlock, normalBlock));
        final EntityExplodeEvent explodeEvent = new EntityExplodeEvent(
                mock(Entity.class),
                new Location(world, 5, 64, 5),
                explodedBlocks,
                1.0f,
                ExplosionResult.DESTROY
        );

        listener.onEntityExplode(explodeEvent);

        assertThat(explodeEvent.blockList()).containsExactly(normalBlock);
    }

    @Test
    void pistonExtendMovingAetheryteIsCancelled() {
        final Block pistonBlock = mock(Block.class);
        final BlockPistonExtendEvent pistonEvent = new BlockPistonExtendEvent(
                pistonBlock,
                List.of(normalBlock, aetheryteBlock),
                BlockFace.EAST
        );

        listener.onPistonExtend(pistonEvent);

        assertThat(pistonEvent.isCancelled()).isTrue();
    }

    @Test
    void warpToAetheryteIsRejectedWhenCostCannotBeAfforded() {
        final AetheryteService.WarpCostEvaluator expensiveEvaluator = (p, src, dst) ->
                WarpEvaluation.denied(
                        Component.text("1000 G", NamedTextColor.GOLD),
                        Component.text("所持金が不足しています", NamedTextColor.RED)
                );
        final AetheryteService customService = new AetheryteService(
                plugin,
                registry,
                NoopHologramService.INSTANCE,
                expensiveEvaluator
        );
        final Player player = mock(Player.class);
        when(player.hasPermission("clickportals.use")).thenReturn(true);

        customService.warpToAetheryte(player, null, registeredAetheryte.id());

        verify(player).sendMessage(any(Component.class));
    }

    @Test
    void warpToAetheryteDoesNotChargeCostWhenPlayerLacksUsePermission() throws Exception {
        final Server server = mock(Server.class);
        final java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
        try {
            when(server.getWorld(WORLD_ID)).thenReturn(world);
            when(server.isOwnedByCurrentRegion(any(), any(int.class), any(int.class))).thenReturn(true);
            final RegionScheduler regionScheduler = mock(RegionScheduler.class);
            when(server.getRegionScheduler()).thenReturn(regionScheduler);
            doAnswer(inv -> {
                ((Runnable) inv.getArgument(2)).run();
                return null;
            }).when(regionScheduler).execute(any(), any(Location.class), any());

            stubFlatSafeTerrain(world);

            final AetheryteService.WarpCostEvaluator evaluator = mock(AetheryteService.WarpCostEvaluator.class);
            when(evaluator.evaluate(any(), any(), any())).thenReturn(WarpEvaluation.permitted(Component.empty()));

            final AetheryteService customService = new AetheryteService(
                    plugin,
                    registry,
                    NoopHologramService.INSTANCE,
                    evaluator
            );
            final Player player = mock(Player.class);
            final EntityScheduler entityScheduler = mock(EntityScheduler.class);
            when(player.getScheduler()).thenReturn(entityScheduler);

            // Initially allowed during preliminary evaluation
            when(player.hasPermission("clickportals.use")).thenReturn(true);
            when(player.hasPermission("clickportals.bypass.cost.warp")).thenReturn(false);

            // Revoke permission before the scheduled task charges the cost
            doAnswer(inv -> {
                when(player.hasPermission("clickportals.use")).thenReturn(false);
                @SuppressWarnings("unchecked")
                final Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) inv.getArgument(1);
                consumer.accept(mock(ScheduledTask.class));
                return mock(ScheduledTask.class);
            }).when(entityScheduler).run(any(), any(), any());

            customService.warpToAetheryte(player, null, registeredAetheryte.id());

            verify(evaluator, never()).charge(any(), any(), any());
            verify(player).sendMessage(any(Component.class));
        } finally {
            serverField.set(null, null);
        }
    }

    @Test
    void inFlightWarpDebouncesDuplicateAttempts() throws Exception {
        final Server server = mock(Server.class);
        final java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
        try {
            when(server.getWorld(WORLD_ID)).thenReturn(world);
            final RegionScheduler regionScheduler = mock(RegionScheduler.class);
            when(server.getRegionScheduler()).thenReturn(regionScheduler);

            final AetheryteService.WarpCostEvaluator evaluator = mock(AetheryteService.WarpCostEvaluator.class);
            when(evaluator.evaluate(any(), any(), any())).thenReturn(WarpEvaluation.permitted(Component.empty()));

            final AetheryteService customService = new AetheryteService(
                    plugin,
                    registry,
                    VariationRegistry.defaults(),
                    NoopHologramService.INSTANCE,
                    Messages.defaults(),
                    () -> -1,
                    () -> 0,
                    p -> 0L,
                    (p, t) -> {},
                    evaluator
            );

            final Player player = mock(Player.class);
            final UUID playerId = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.hasPermission("clickportals.use")).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 5, 64, 5));

            // Keep region execution pending so warp remains in flight
            customService.warpToAetheryte(player, registeredAetheryte, registeredAetheryte.id());

            // Second warp attempt should be debounced immediately
            customService.warpToAetheryte(player, registeredAetheryte, registeredAetheryte.id());

            // Region execution was requested exactly once
            verify(regionScheduler, times(1)).execute(any(), any(Location.class), any(Runnable.class));
        } finally {
            serverField.set(null, null);
        }
    }

    @Test
    void warpToAetheryteRecoversAndClearsInFlightWhenExceptionThrownDuringCharge() throws Exception {
        final Server server = mock(Server.class);
        final java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
        try {
            when(server.getWorld(WORLD_ID)).thenReturn(world);
            when(server.isOwnedByCurrentRegion(any(), any(int.class), any(int.class))).thenReturn(true);
            final RegionScheduler regionScheduler = mock(RegionScheduler.class);
            when(server.getRegionScheduler()).thenReturn(regionScheduler);
            doAnswer(inv -> {
                ((Runnable) inv.getArgument(2)).run();
                return null;
            }).when(regionScheduler).execute(any(), any(Location.class), any());

            stubFlatSafeTerrain(world);

            final AetheryteService.WarpCostEvaluator evaluator = mock(AetheryteService.WarpCostEvaluator.class);
            when(evaluator.evaluate(any(), any(), any())).thenReturn(WarpEvaluation.permitted(Component.empty()));
            when(evaluator.charge(any(), any(), any())).thenThrow(new RuntimeException("Economy database crashed"));

            final AetheryteService customService = new AetheryteService(
                    plugin,
                    registry,
                    VariationRegistry.defaults(),
                    NoopHologramService.INSTANCE,
                    Messages.defaults(),
                    () -> -1,
                    () -> 0,
                    p -> 0L,
                    (p, t) -> {},
                    evaluator
            );

            final Player player = mock(Player.class);
            final UUID playerId = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.hasPermission("clickportals.use")).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 5, 64, 5));

            final EntityScheduler entityScheduler = mock(EntityScheduler.class);
            when(player.getScheduler()).thenReturn(entityScheduler);
            doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                final Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) inv.getArgument(1);
                consumer.accept(mock(ScheduledTask.class));
                return mock(ScheduledTask.class);
            }).when(entityScheduler).run(any(), any(), any());

            // First warp attempt crashes in charge()
            customService.warpToAetheryte(player, registeredAetheryte, registeredAetheryte.id());

            // Player should receive error notification
            verify(player).sendMessage(any(Component.class));

            // Evaluator charge was called once
            verify(evaluator, times(1)).charge(any(), any(), any());

            // Next warp attempt: player is NOT locked in inFlightWarps, so charge() is invoked again!
            customService.warpToAetheryte(player, registeredAetheryte, registeredAetheryte.id());
            verify(evaluator, times(2)).charge(any(), any(), any());
        } finally {
            serverField.set(null, null);
        }
    }

    @Test
    void warpToAetheryteChargesCostAndSendsPaidNotificationOnSuccess() throws Exception {
        final Server server = mock(Server.class);
        final java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
        try {
            when(server.getWorld(WORLD_ID)).thenReturn(world);
            when(server.isOwnedByCurrentRegion(any(), any(int.class), any(int.class))).thenReturn(true);
            final RegionScheduler regionScheduler = mock(RegionScheduler.class);
            when(server.getRegionScheduler()).thenReturn(regionScheduler);
            doAnswer(inv -> {
                ((Runnable) inv.getArgument(2)).run();
                return null;
            }).when(regionScheduler).execute(any(), any(Location.class), any());

            stubFlatSafeTerrain(world);

            final AetheryteService.WarpCostEvaluator evaluator = mock(AetheryteService.WarpCostEvaluator.class);
            when(evaluator.evaluate(any(), any(), any())).thenReturn(WarpEvaluation.permitted(Component.text("$30.00")));
            when(evaluator.charge(any(), any(), any())).thenReturn(true);
            when(evaluator.popPendingCharge(any(), any())).thenReturn(
                    List.of(new CostItemQuote(CostType.VAULT, 30.0, true, true, 100.0, false))
            );

            final AetheryteService customService = new AetheryteService(
                    plugin,
                    registry,
                    VariationRegistry.defaults(),
                    NoopHologramService.INSTANCE,
                    Messages.defaults(),
                    () -> -1,
                    () -> 0,
                    p -> 0L,
                    (p, t) -> {},
                    evaluator
            );
            final net.sabafly.clickportals.economy.EconomyProvider economy = mock(net.sabafly.clickportals.economy.EconomyProvider.class);
            when(economy.isAvailable()).thenReturn(true);
            when(economy.format(30.0)).thenReturn("$30.00");
            customService.setEconomyProvider(economy);

            final Player player = mock(Player.class);
            final UUID playerId = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.hasPermission("clickportals.use")).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 5, 64, 5));
            when(player.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));

            final EntityScheduler entityScheduler = mock(EntityScheduler.class);
            when(player.getScheduler()).thenReturn(entityScheduler);
            doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                final Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) inv.getArgument(1);
                consumer.accept(mock(ScheduledTask.class));
                return mock(ScheduledTask.class);
            }).when(entityScheduler).run(any(), any(), any());

            customService.warpToAetheryte(player, registeredAetheryte, registeredAetheryte.id());

            verify(evaluator).commit(eq(player), eq(registeredAetheryte), eq(registeredAetheryte));
            final ArgumentCaptor<Component> msgCaptor = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(msgCaptor.capture());
            final boolean hasPaidMessage = msgCaptor.getAllValues().stream()
                    .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
                    .anyMatch(s -> s.contains("Paid") && s.contains("$30.00"));
            assertThat(hasPaidMessage).isTrue();
        } finally {
            serverField.set(null, null);
        }
    }

    @Test
    void playerQuitRemovesPlayerFromHologramServiceAndAetheryteService() {
        final HologramService mockHologramService = mock(HologramService.class);
        final AetheryteService mockService = mock(AetheryteService.class);
        final AetheryteDialogFactory dialogFactory = new AetheryteDialogFactory(mockService);
        final AetheryteListener customListener = new AetheryteListener(
                plugin,
                registry,
                mockService,
                dialogFactory,
                mockHologramService,
                Messages.defaults()
        );

        final UUID playerId = UUID.randomUUID();
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        final PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED);

        customListener.onPlayerQuit(quitEvent);

        verify(mockHologramService).removePlayer(playerId);
        verify(mockService).removePlayer(playerId);
    }

    @Test
    void placementCooldownIsComputedFromPlayerPdcAndSendsClientPacketUnlessBypassed() {
        final AtomicLong currentTick = new AtomicLong(1000L);
        final AtomicInteger sentPacketTicks = new AtomicInteger(0);
        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                VariationRegistry.defaults(),
                NoopHologramService.INSTANCE,
                Messages.defaults(),
                () -> -1,
                () -> 100,
                p -> currentTick.get(),
                (p, ticks) -> sentPacketTicks.set(ticks),
                (p, src, dst) -> WarpEvaluation.permitted(Component.empty())
        );

        final Player player = mock(Player.class);
        final PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(player.getPersistentDataContainer()).thenReturn(pdc);

        // No prior placement recorded -> remaining cooldown is 0
        assertThat(service.checkPlacementCooldown(player, Aetheryte.DEFAULT_VARIATION)).isEmpty();

        // Record placement at tick 1000 (default cooldown = 100 ticks)
        service.recordPlacementCooldown(player, Aetheryte.DEFAULT_VARIATION);
        verify(pdc).set(AetheryteService.LAST_PLACE_TICK_KEY, PersistentDataType.LONG, 1000L);
        assertThat(sentPacketTicks.get()).isEqualTo(100);

        // At tick 1040, 60 ticks remain -> placement is rejected with error component and re-syncs 60 tick packet
        when(pdc.get(AetheryteService.LAST_PLACE_TICK_KEY, PersistentDataType.LONG)).thenReturn(1000L);
        currentTick.set(1040L);
        assertThat(service.getRemainingCooldownTicks(player, Aetheryte.DEFAULT_VARIATION)).isEqualTo(60);
        assertThat(service.checkPlacementCooldown(player, Aetheryte.DEFAULT_VARIATION)).isPresent();
        assertThat(sentPacketTicks.get()).isEqualTo(60);

        // Bypass permission skips cooldown check
        when(player.hasPermission(Permissions.BYPASS_LIMIT)).thenReturn(true);
        assertThat(service.checkPlacementCooldown(player, Aetheryte.DEFAULT_VARIATION)).isEmpty();

        // At tick 1100, cooldown has expired -> allowed even without bypass
        when(player.hasPermission(Permissions.BYPASS_LIMIT)).thenReturn(false);
        currentTick.set(1100L);
        assertThat(service.getRemainingCooldownTicks(player, Aetheryte.DEFAULT_VARIATION)).isZero();
        assertThat(service.checkPlacementCooldown(player, Aetheryte.DEFAULT_VARIATION)).isEmpty();
    }

    @Test
    void serverLoadEventTriggersModelSyncWhenPluginIsClickPortals() {
        final ClickPortals clickPortals = mock(ClickPortals.class);
        when(clickPortals.isEnabled()).thenReturn(true);

        final AetheryteListener customListener = new AetheryteListener(
                clickPortals,
                registry,
                mock(AetheryteDialogFactory.class),
                NoopHologramService.INSTANCE
        );

        final ServerLoadEvent event = mock(ServerLoadEvent.class);
        customListener.onServerLoad(event);

        verify(clickPortals).syncAllAetheryteModels();
    }

    @Test
    void playerChunkLoadSpawnsAetheryteHologramWhenChunkContainsAetheryte() {
        final HologramService mockHolograms = mock(HologramService.class);
        final net.sabafly.clickportals.model.AetheryteModelService mockModels = mock(net.sabafly.clickportals.model.AetheryteModelService.class);
        service.setHologramService(mockHolograms);
        service.setModelService(mockModels);
        listener.setHologramService(mockHolograms);

        final Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(0);
        when(chunk.getZ()).thenReturn(0);

        final Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        final EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(entityScheduler);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            final Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) inv.getArgument(1);
            consumer.accept(mock(ScheduledTask.class));
            return mock(ScheduledTask.class);
        }).when(entityScheduler).run(any(), any(), any());

        // registeredAetheryte is at (0, 64, 0), which is in chunk (0, 0)
        final PlayerChunkLoadEvent loadEvent = new PlayerChunkLoadEvent(chunk, player);
        listener.onPlayerChunkLoad(loadEvent);

        verify(mockHolograms).showAetheryteToPlayer(player, registeredAetheryte);
        verify(mockModels).showAetheryteToPlayer(player, registeredAetheryte);
    }

    @Test
    void playerChunkLoadIgnoredWhenChunkHasNoAetheryte() {
        final HologramService mockHolograms = mock(HologramService.class);
        service.setHologramService(mockHolograms);
        listener.setHologramService(mockHolograms);

        final Chunk emptyChunk = mock(Chunk.class);
        when(emptyChunk.getWorld()).thenReturn(world);
        when(emptyChunk.getX()).thenReturn(10);
        when(emptyChunk.getZ()).thenReturn(10);

        final Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);

        final PlayerChunkLoadEvent loadEvent = new PlayerChunkLoadEvent(emptyChunk, player);
        listener.onPlayerChunkLoad(loadEvent);

        verify(mockHolograms, never()).showAetheryteToPlayer(any(), any());
    }

    @Test
    void playerChunkUnloadHidesAetheryte() {
        final HologramService mockHolograms = mock(HologramService.class);
        final net.sabafly.clickportals.model.AetheryteModelService mockModels = mock(net.sabafly.clickportals.model.AetheryteModelService.class);
        service.setHologramService(mockHolograms);
        service.setModelService(mockModels);
        listener.setHologramService(mockHolograms);

        final Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getX()).thenReturn(0);
        when(chunk.getZ()).thenReturn(0);

        final Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        final EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(entityScheduler);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            final Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) inv.getArgument(1);
            consumer.accept(mock(ScheduledTask.class));
            return mock(ScheduledTask.class);
        }).when(entityScheduler).run(any(), any(), any());

        final PlayerChunkUnloadEvent unloadEvent = new PlayerChunkUnloadEvent(chunk, player);
        listener.onPlayerChunkUnload(unloadEvent);

        verify(mockHolograms).hideAetheryteFromPlayer(player, registeredAetheryte);
        verify(mockModels).hideAetheryteFromPlayer(player, registeredAetheryte);
    }

    @Test
    void warpToAetheryteRecordsPendingRefundWhenChargeRollbackFails() throws Exception {
        final Server server = mock(Server.class);
        final java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
        try {
            when(server.getWorld(WORLD_ID)).thenReturn(world);
            when(server.isOwnedByCurrentRegion(any(), any(int.class), any(int.class))).thenReturn(true);
            final RegionScheduler regionScheduler = mock(RegionScheduler.class);
            when(server.getRegionScheduler()).thenReturn(regionScheduler);
            doAnswer(inv -> {
                ((Runnable) inv.getArgument(2)).run();
                return null;
            }).when(regionScheduler).execute(any(), any(Location.class), any());

            stubFlatSafeTerrain(world);

            final AetheryteService.WarpCostEvaluator evaluator = mock(AetheryteService.WarpCostEvaluator.class);
            when(evaluator.evaluate(any(), any(), any())).thenReturn(WarpEvaluation.permitted(Component.text("$70.00")));
            when(evaluator.charge(any(), any(), any())).thenReturn(false);
            when(evaluator.popFailedChargeUnrefunded(any(), any())).thenReturn(
                    List.of(new net.sabafly.clickportals.cost.CostItemQuote(
                            net.sabafly.clickportals.cost.CostType.VAULT, 40.0, true, true, 40.0, false))
            );

            when(plugin.getServer()).thenReturn(server);
            when(plugin.isEnabled()).thenReturn(true);
            final io.papermc.paper.threadedregions.scheduler.AsyncScheduler asyncScheduler =
                    mock(io.papermc.paper.threadedregions.scheduler.AsyncScheduler.class);
            when(server.getAsyncScheduler()).thenReturn(asyncScheduler);
            doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                final Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) inv.getArgument(1);
                consumer.accept(mock(ScheduledTask.class));
                return mock(ScheduledTask.class);
            }).when(asyncScheduler).runNow(eq(plugin), any());

            final net.sabafly.clickportals.service.PendingRefundStorage storage =
                    mock(net.sabafly.clickportals.service.PendingRefundStorage.class);
            when(storage.load()).thenReturn(java.util.Map.of());
            when(storage.save(any())).thenReturn(true);

            final AetheryteService customService = new AetheryteService(
                    plugin,
                    registry,
                    VariationRegistry.defaults(),
                    NoopHologramService.INSTANCE,
                    net.sabafly.clickportals.model.NoopAetheryteModelService.INSTANCE,
                    Messages.defaults(),
                    () -> -1,
                    () -> 0,
                    () -> 0.0,
                    p -> 0L,
                    (p, t) -> {},
                    evaluator,
                    storage
            );

            final Player player = mock(Player.class);
            final UUID playerId = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.hasPermission("clickportals.use")).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 5, 64, 5));

            final EntityScheduler entityScheduler = mock(EntityScheduler.class);
            when(player.getScheduler()).thenReturn(entityScheduler);
            doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                final Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) inv.getArgument(1);
                consumer.accept(mock(ScheduledTask.class));
                return mock(ScheduledTask.class);
            }).when(entityScheduler).run(any(), any(), any());

            customService.warpToAetheryte(player, registeredAetheryte, registeredAetheryte.id());

            assertThat(customService.pendingRefundPlayerCount()).isEqualTo(1);
            assertThat(customService.pendingRefundItemCount()).isEqualTo(1);
            verify(storage).save(any());
        } finally {
            serverField.set(null, null);
        }
    }

    @Test
    void castListenersCancelOnCoordinateMoveDamageAndDeathButAllowHeadRotation() {
        final AetheryteService mockService = mock(AetheryteService.class);
        final AetheryteListener listener = new AetheryteListener(
                plugin,
                registry,
                mockService,
                mock(AetheryteDialogFactory.class),
                NoopHologramService.INSTANCE,
                Messages.defaults()
        );

        final Player player = mock(Player.class);
        final UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(mockService.isCasting(playerId)).thenReturn(true);

        final Location from = new Location(world, 10.0, 64.0, 10.0, 0.0f, 0.0f);
        final Location rotatedOnly = new Location(world, 10.0, 64.0, 10.0, 90.0f, -20.0f);
        final Location moved = new Location(world, 10.5, 64.0, 10.0, 0.0f, 0.0f);

        listener.onPlayerMove(new org.bukkit.event.player.PlayerMoveEvent(player, from, rotatedOnly));
        verify(mockService, never()).onPlayerMoveDuringCast(player);

        listener.onPlayerMove(new org.bukkit.event.player.PlayerMoveEvent(player, from, moved));
        verify(mockService).onPlayerMoveDuringCast(player);

        final org.bukkit.event.entity.EntityDamageEvent damageEvent = mock(org.bukkit.event.entity.EntityDamageEvent.class);
        when(damageEvent.getEntity()).thenReturn(player);
        listener.onEntityDamage(damageEvent);
        verify(mockService).onPlayerDamageDuringCast(player);

        final org.bukkit.event.entity.PlayerDeathEvent deathEvent = mock(org.bukkit.event.entity.PlayerDeathEvent.class);
        when(deathEvent.getEntity()).thenReturn(player);
        listener.onPlayerDeath(deathEvent);
        verify(mockService).cancelCast(player, AetheryteService.CastCancelReason.DIED);
    }

    @Test
    void dimensionDisablePlaceForbidsNewPlacementWhileAllowingRemovalAndBypass() {
        service.setDimensionConfigSupplier(() -> new net.sabafly.clickportals.route.DimensionRoutesConfig(
                List.of(),
                java.util.Map.of("world", 0.0),
                List.of(),
                java.util.Map.of(
                        "world",
                        new net.sabafly.clickportals.route.DimensionWorldConfig("<aqua>Disabled Place World</aqua>", false, false)
                )
        ));

        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(registeredAetheryte.ownerId());
        when(player.hasPermission(Permissions.PLACE)).thenReturn(true);
        when(player.hasPermission(Permissions.USE)).thenReturn(true);
        when(player.getWorld()).thenReturn(world);

        // Placement check fails with formatted dimension display name
        assertThat(service.checkPlacementDimension(player, world)).isPresent();
        service.placeAetheryte(player, world, 10, 64, 10, "BlockedGate", Aetheryte.DEFAULT_VARIATION);

        final ArgumentCaptor<Component> msgCaptor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(msgCaptor.capture());
        assertThat(PlainTextComponentSerializer.plainText().serialize(msgCaptor.getValue()))
                .contains("Disabled Place World");
        assertThat(registry.getByPosition(world, 10, 64, 10)).isEmpty();
        verify(player, never()).getScheduler();

        // Warp use in this dimension is denied, yet owner removal remains permitted
        assertThat(service.isDimensionUseAllowed(player, "world")).isFalse();
        assertThat(AetheryteService.canRemove(player, registeredAetheryte)).isTrue();

        // Bypass permission allows both placement check and dimension use
        when(player.hasPermission(Permissions.BYPASS_DIMENSION)).thenReturn(true);
        assertThat(service.checkPlacementDimension(player, world)).isEmpty();
        assertThat(service.isDimensionUseAllowed(player, "world")).isTrue();
    }

    private static void stubFlatSafeTerrain(final World mockWorld) {
        final Block safeFloor = mock(Block.class);
        when(safeFloor.isSolid()).thenReturn(true);
        when(safeFloor.getType()).thenReturn(Material.STONE);
        final Block safeAir = mock(Block.class);
        when(safeAir.isPassable()).thenReturn(true);
        when(safeAir.getType()).thenReturn(Material.AIR);

        when(mockWorld.getBlockAt(any(int.class), any(int.class), any(int.class))).thenAnswer(inv -> {
            final int y = inv.getArgument(1);
            return y <= 63 ? safeFloor : safeAir;
        });
    }
}
