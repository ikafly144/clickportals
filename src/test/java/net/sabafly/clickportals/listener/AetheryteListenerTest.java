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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.dialog.AetheryteDialogFactory;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import net.sabafly.clickportals.service.AetheryteService;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation;
import net.sabafly.clickportals.variation.VariationRegistry;
import net.sabafly.clickportals.visual.HologramService;
import net.sabafly.clickportals.visual.NoopHologramService;
import org.bukkit.Bukkit;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AetheryteListenerTest {

    private static final UUID WORLD_ID = UUID.fromString("12345678-1234-1234-1234-1234567890ab");

    private Plugin plugin;
    private AetheryteRegistry registry;
    private AetheryteListener listener;
    private World world;
    private Block aetheryteBlock;
    private Block normalBlock;
    private Aetheryte registeredAetheryte;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        registry = new AetheryteRegistry();
        final AetheryteService service = new AetheryteService(plugin, registry, NoopHologramService.INSTANCE);
        final AetheryteDialogFactory dialogFactory = new AetheryteDialogFactory(service);
        listener = new AetheryteListener(plugin, registry, dialogFactory, NoopHologramService.INSTANCE);

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
            final RegionScheduler regionScheduler = mock(RegionScheduler.class);
            when(server.getRegionScheduler()).thenReturn(regionScheduler);
            doAnswer(inv -> {
                ((Runnable) inv.getArgument(2)).run();
                return null;
            }).when(regionScheduler).execute(any(), any(Location.class), any());

            final Block safeFloor = mock(Block.class);
            when(safeFloor.isSolid()).thenReturn(true);
            when(safeFloor.getType()).thenReturn(Material.STONE);
            final Block safeAir = mock(Block.class);
            when(safeAir.isPassable()).thenReturn(true);
            when(safeAir.getType()).thenReturn(Material.AIR);

            when(world.getBlockAt(5, 63, 5)).thenReturn(safeFloor);
            when(world.getBlockAt(5, 64, 5)).thenReturn(safeAir);
            when(world.getBlockAt(5, 65, 5)).thenReturn(safeAir);

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
    void playerQuitRemovesPlayerFromHologramService() {
        final HologramService mockHologramService = mock(HologramService.class);
        final AetheryteService service = new AetheryteService(plugin, registry, mockHologramService);
        final AetheryteDialogFactory dialogFactory = new AetheryteDialogFactory(service);
        final AetheryteListener customListener = new AetheryteListener(plugin, registry, dialogFactory, mockHologramService);

        final UUID playerId = UUID.randomUUID();
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        final PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED);

        customListener.onPlayerQuit(quitEvent);

        verify(mockHologramService).removePlayer(playerId);
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
}
