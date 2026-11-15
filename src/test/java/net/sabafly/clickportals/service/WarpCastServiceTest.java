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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteNetwork;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.aetheryte.AetheryteVisibility;
import net.sabafly.clickportals.config.CastSection;
import net.sabafly.clickportals.config.CastSection.CastCancelSection;
import net.sabafly.clickportals.config.DistanceSection;
import net.sabafly.clickportals.config.PetsWarpSection;
import net.sabafly.clickportals.config.VisibilityCostSection;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.model.AetheryteModelService;
import net.sabafly.clickportals.model.AetheryteModelService.PlayerAnimationMode;
import net.sabafly.clickportals.service.AetheryteService.CastCancelReason;
import net.sabafly.clickportals.service.AetheryteService.WarpCostEvaluator;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.WarpOverrideSection;
import net.sabafly.clickportals.variation.VariationRegistry;
import net.sabafly.clickportals.visual.CastImmobilizeService;
import net.sabafly.clickportals.visual.NoopHologramService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WarpCastServiceTest {

    private Field serverField;
    private World world;
    private AetheryteRegistry registry;
    private Aetheryte sourceAetheryte;
    private Aetheryte destAetheryte;
    private Aetheryte destAetheryte2;
    private Plugin plugin;
    private AetheryteModelService modelService;
    private Player player;
    private UUID playerId;
    private AtomicReference<Consumer<ScheduledTask>> activeTickConsumer;
    private ScheduledTask activeTickTask;

    @BeforeEach
    void setUp() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);

        final Server server = mock(Server.class);
        world = mock(World.class);
        final UUID worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        final org.bukkit.persistence.PersistentDataContainer worldPdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(world.getPersistentDataContainer()).thenReturn(worldPdc);

        final Block solidFloor = mock(Block.class);
        when(solidFloor.getType()).thenReturn(Material.STONE);
        when(solidFloor.isPassable()).thenReturn(false);
        when(solidFloor.isSolid()).thenReturn(true);
        final Block airBlock = mock(Block.class);
        when(airBlock.getType()).thenReturn(Material.AIR);
        when(airBlock.isPassable()).thenReturn(true);
        when(airBlock.isSolid()).thenReturn(false);

        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(airBlock);
        when(world.getBlockAt(100, 63, 100)).thenReturn(solidFloor);
        when(world.getBlockAt(200, 63, 200)).thenReturn(solidFloor);

        when(server.getWorld(worldId)).thenReturn(world);
        when(server.isOwnedByCurrentRegion(any(World.class), anyInt(), anyInt())).thenReturn(true);
        final RegionScheduler regionScheduler = mock(RegionScheduler.class);
        doAnswer(inv -> {
            final Runnable runnable = inv.getArgument(2);
            runnable.run();
            return null;
        }).when(regionScheduler).execute(any(), any(Location.class), any());
        when(server.getRegionScheduler()).thenReturn(regionScheduler);
        serverField.set(null, server);

        registry = new AetheryteRegistry();
        sourceAetheryte = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 5, 64, 5, UUID.randomUUID(), 0L, "default");
        destAetheryte = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 100, 64, 100, UUID.randomUUID(), 0L, "default");
        destAetheryte2 = new Aetheryte(UUID.randomUUID(), "Dest2", worldId, "world", 200, 64, 200, UUID.randomUUID(), 0L, "default");
        registry.register(world, sourceAetheryte);
        registry.register(world, destAetheryte);
        registry.register(world, destAetheryte2);

        plugin = mock(Plugin.class);
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger(WarpCastServiceTest.class));
        modelService = mock(AetheryteModelService.class);

        player = mock(Player.class);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Caster");
        when(player.isOnline()).thenReturn(true);
        when(player.isDead()).thenReturn(false);
        when(player.hasPermission("clickportals.use")).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 5.5, 64.0, 5.5));
        when(player.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));

        activeTickConsumer = new AtomicReference<>();
        activeTickTask = mock(ScheduledTask.class);
        final EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(entityScheduler);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            final Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) inv.getArgument(1);
            consumer.accept(mock(ScheduledTask.class));
            return mock(ScheduledTask.class);
        }).when(entityScheduler).run(any(), any(), any());
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            final Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) inv.getArgument(1);
            activeTickConsumer.set(consumer);
            return activeTickTask;
        }).when(entityScheduler).runAtFixedRate(any(), any(), any(), anyLong(), anyLong());
    }

    @AfterEach
    void tearDown() throws Exception {
        serverField.set(null, null);
    }

    private AetheryteService createService(
            final CastSection globalCast,
            final VariationRegistry variationRegistry,
            final WarpCostEvaluator evaluator
    ) {
        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                NoopHologramService.INSTANCE,
                modelService,
                Messages.defaults(),
                () -> -1,
                () -> 0,
                () -> 0.0,
                p -> 0L,
                (p, t) -> {},
                evaluator
        );
        service.setGlobalCastSupplier(() -> globalCast);
        return service;
    }

    @Test
    void castStartsPlaysHoldOnLastAndChargesOnlyOnCompletion() {
        final AtomicBoolean affordable = new AtomicBoolean(true);
        final WarpCostEvaluator evaluator = mock(WarpCostEvaluator.class);
        when(evaluator.evaluate(any(), any(), any())).thenAnswer(inv ->
                affordable.get()
                        ? WarpEvaluation.permitted(Component.text("10g"))
                        : WarpEvaluation.denied(Component.text("10g"), Component.text("Poor")));
        when(evaluator.charge(any(), any(), any())).thenReturn(true);

        final CastSection cast = new CastSection(3, "steve", "channel_cast", "warp_arrive", CastCancelSection.defaults());
        final AetheryteService service = createService(cast, VariationRegistry.defaults(), evaluator);

        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());

        assertThat(service.isCasting(playerId)).isTrue();
        verify(modelService).playPlayerAnimation(player, "steve", "channel_cast", PlayerAnimationMode.HOLD_ON_LAST);
        verify(evaluator, never()).charge(any(), any(), any());
        verify(player, never()).teleportAsync(any());

        // Tick 1 and 2: still casting
        activeTickConsumer.get().accept(activeTickTask);
        activeTickConsumer.get().accept(activeTickTask);
        assertThat(service.isCasting(playerId)).isTrue();
        verify(evaluator, never()).charge(any(), any(), any());

        // Tick 3: cast completes -> stop cast animation, re-evaluate, charge, teleport, play arrive animation
        activeTickConsumer.get().accept(activeTickTask);
        assertThat(service.isCasting(playerId)).isFalse();
        verify(activeTickTask).cancel();
        verify(modelService).stopPlayerAnimation(player);
        verify(evaluator).charge(player, sourceAetheryte, destAetheryte);
        verify(evaluator).commit(player, sourceAetheryte, destAetheryte);
        verify(player).teleportAsync(any(Location.class));
        verify(modelService).playPlayerAnimation(player, "steve", "warp_arrive", PlayerAnimationMode.PLAY_ONCE);
        verify(player, atLeastOnce()).sendActionBar(any(Component.class));
    }

    @Test
    void insufficientCostAtCompletionAbortsTeleportWithoutCharging() {
        final AtomicBoolean affordable = new AtomicBoolean(true);
        final WarpCostEvaluator evaluator = mock(WarpCostEvaluator.class);
        when(evaluator.evaluate(any(), any(), any())).thenAnswer(inv ->
                affordable.get()
                        ? WarpEvaluation.permitted(Component.text("10g"))
                        : WarpEvaluation.denied(Component.text("10g"), Component.text("Poor")));
        when(evaluator.charge(any(), any(), any())).thenReturn(true);

        final CastSection cast = new CastSection(2, "steve", "channel_cast", "warp_arrive", CastCancelSection.defaults());
        final AetheryteService service = createService(cast, VariationRegistry.defaults(), evaluator);

        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());
        assertThat(service.isCasting(playerId)).isTrue();

        // Player loses funds during cast
        affordable.set(false);
        activeTickConsumer.get().accept(activeTickTask);
        activeTickConsumer.get().accept(activeTickTask);

        assertThat(service.isCasting(playerId)).isFalse();
        verify(modelService).stopPlayerAnimation(player);
        verify(evaluator, never()).charge(any(), any(), any());
        verify(player, never()).teleportAsync(any());
    }

    @Test
    void moveAndDamageCancelCastWithoutChargingAndRespectCancelFlags() {
        final WarpCostEvaluator evaluator = mock(WarpCostEvaluator.class);
        when(evaluator.evaluate(any(), any(), any())).thenReturn(WarpEvaluation.permitted(Component.empty()));

        final CastSection cancelEnabled = new CastSection(10, "steve", "cast_anim", "", new CastCancelSection(true, true));
        final AetheryteService service = createService(cancelEnabled, VariationRegistry.defaults(), evaluator);

        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());
        assertThat(service.isCasting(playerId)).isTrue();

        service.onPlayerMoveDuringCast(player);
        assertThat(service.isCasting(playerId)).isFalse();
        verify(activeTickTask).cancel();
        verify(modelService).stopPlayerAnimation(player);
        verify(evaluator, never()).charge(any(), any(), any());

        // Now test with on-move: false, on-damage: true
        final CastSection moveAllowed = new CastSection(10, "steve", "cast_anim", "", new CastCancelSection(false, true));
        service.setGlobalCastSupplier(() -> moveAllowed);
        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());
        assertThat(service.isCasting(playerId)).isTrue();

        service.onPlayerMoveDuringCast(player);
        assertThat(service.isCasting(playerId)).isTrue();

        service.onPlayerDamageDuringCast(player);
        assertThat(service.isCasting(playerId)).isFalse();
        verify(modelService, times(2)).stopPlayerAnimation(player);

        // Now test cancel.on-interact: false allows non-Aetheryte interact while Aetheryte interact still cancels
        final CastSection interactAllowed = new CastSection(
                10,
                "steve",
                "cast_anim",
                "",
                new CastCancelSection(true, true, false)
        );
        service.setGlobalCastSupplier(() -> interactAllowed);
        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());
        assertThat(service.isCasting(playerId)).isTrue();

        service.onPlayerInteractDuringCast(player);
        assertThat(service.isCasting(playerId)).isTrue();

        assertThat(service.cancelCast(player, CastCancelReason.INTERACT)).isTrue();
        assertThat(service.isCasting(playerId)).isFalse();

        // Now test cancel.on-interact: true cancels on non-Aetheryte interact
        final CastSection interactCancels = new CastSection(
                10,
                "steve",
                "cast_anim",
                "",
                new CastCancelSection(true, true, true)
        );
        service.setGlobalCastSupplier(() -> interactCancels);
        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());
        assertThat(service.isCasting(playerId)).isTrue();

        service.onPlayerInteractDuringCast(player);
        assertThat(service.isCasting(playerId)).isFalse();
    }

    @Test
    void startingAnotherWarpCancelsPreviousCastAndStartsNewOne() {
        final WarpCostEvaluator evaluator = mock(WarpCostEvaluator.class);
        when(evaluator.evaluate(any(), any(), any())).thenReturn(WarpEvaluation.permitted(Component.empty()));

        final CastSection cast = new CastSection(5, "steve", "cast_anim", "", CastCancelSection.defaults());
        final AetheryteService service = createService(cast, VariationRegistry.defaults(), evaluator);

        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());
        assertThat(service.isCasting(playerId)).isTrue();

        service.warpToAetheryte(player, sourceAetheryte, destAetheryte2.id());
        assertThat(service.isCasting(playerId)).isTrue();
        verify(activeTickTask).cancel();
        verify(modelService).stopPlayerAnimation(player);
        verify(modelService, times(2)).playPlayerAnimation(player, "steve", "cast_anim", PlayerAnimationMode.HOLD_ON_LAST);
    }

    @Test
    void variationCastOverrideAppliesOverGlobalCast() {
        final WarpCostEvaluator evaluator = mock(WarpCostEvaluator.class);
        when(evaluator.evaluate(any(), any(), any())).thenReturn(WarpEvaluation.permitted(Component.empty()));
        when(evaluator.charge(any(), any(), any())).thenReturn(true);

        final AetheryteVariation base = AetheryteVariation.defaultVariation();
        final AetheryteVariation instantVariation = new AetheryteVariation(
                base.displayName(),
                base.item(),
                base.recipe(),
                base.model(),
                base.hitbox(),
                base.teleport(),
                base.placement(),
                base.limit(),
                new WarpOverrideSection(
                        DistanceSection.inherit(),
                        null,
                        new CastSection(0, null, "", "fast_arrive", null)
                ),
                base.bettermodel()
        );
        final VariationRegistry variations = new VariationRegistry(java.util.Map.of("default", instantVariation), null);
        final CastSection globalCast = new CastSection(60, "steve", "slow_cast", "slow_arrive", CastCancelSection.defaults());
        final AetheryteService service = createService(globalCast, variations, evaluator);

        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());

        assertThat(service.isCasting(playerId)).isFalse();
        verify(modelService, never()).playPlayerAnimation(any(), any(), eq("slow_cast"), any());
        verify(player).teleportAsync(any(Location.class));
        verify(modelService).playPlayerAnimation(player, "steve", "fast_arrive", PlayerAnimationMode.PLAY_ONCE);
    }

    @Test
    void cancelCastOnQuitAndDeathStopsAnimation() {
        final WarpCostEvaluator evaluator = mock(WarpCostEvaluator.class);
        when(evaluator.evaluate(any(), any(), any())).thenReturn(WarpEvaluation.permitted(Component.empty()));

        final CastSection cast = new CastSection(10, "steve", "cast_anim", "", CastCancelSection.defaults());
        final AetheryteService service = createService(cast, VariationRegistry.defaults(), evaluator);

        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());
        assertThat(service.isCasting(playerId)).isTrue();

        service.removePlayer(playerId);
        assertThat(service.isCasting(playerId)).isFalse();
        verify(modelService).stopPlayerAnimation(player);

        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());
        assertThat(service.cancelCast(player, CastCancelReason.DIED)).isTrue();
        assertThat(service.isCasting(playerId)).isFalse();
        verify(modelService, times(2)).stopPlayerAnimation(player);
    }

    @Test
    void buildCastBarRendersProgressSegments() {
        final net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer plain =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertThat(plain.serialize(AetheryteService.buildCastBar(20, 20))).isEqualTo("[░░░░░░░░░░░░░░░░]");
        assertThat(plain.serialize(AetheryteService.buildCastBar(10, 20))).isEqualTo("[████████░░░░░░░░]");
        assertThat(plain.serialize(AetheryteService.buildCastBar(0, 20))).isEqualTo("[████████████████]");
    }

    @Test
    void immobilizesOnCastStartAndReleasesOnCompleteAndCancel() {
        final WarpCostEvaluator evaluator = mock(WarpCostEvaluator.class);
        when(evaluator.evaluate(any(), any(), any())).thenReturn(WarpEvaluation.permitted(Component.empty()));
        when(evaluator.charge(any(), any(), any())).thenReturn(true);

        final CastImmobilizeService immobilizeService = mock(CastImmobilizeService.class);
        final CastSection castEnabled = new CastSection(2, "steve", "", "", true, CastCancelSection.defaults());
        final AetheryteService service = createService(castEnabled, VariationRegistry.defaults(), evaluator);
        service.setCastImmobilizeService(immobilizeService);

        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());
        verify(immobilizeService).immobilize(player);
        verify(immobilizeService, never()).release(any());

        activeTickConsumer.get().accept(activeTickTask);
        activeTickConsumer.get().accept(activeTickTask);
        verify(immobilizeService).release(player);

        // Cancel releases immobilization
        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());
        verify(immobilizeService, times(2)).immobilize(player);
        service.cancelCast(player, CastCancelReason.DAMAGED);
        verify(immobilizeService, times(2)).release(player);

        // Disabled immobilize skips immobilizer
        final CastSection castDisabled = new CastSection(2, "steve", "", "", false, CastCancelSection.defaults());
        service.setGlobalCastSupplier(() -> castDisabled);
        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());
        verify(immobilizeService, times(2)).immobilize(player);
    }

    @Test
    void releasesImmobilizeWhenCancelledConcurrentlyDuringStart() {
        final WarpCostEvaluator evaluator = mock(WarpCostEvaluator.class);
        when(evaluator.evaluate(any(), any(), any())).thenReturn(WarpEvaluation.permitted(Component.empty()));

        final CastImmobilizeService immobilizeService = mock(CastImmobilizeService.class);
        final CastSection castEnabled = new CastSection(4, "steve", "cast", "", true, CastCancelSection.defaults());
        final AetheryteService service = createService(castEnabled, VariationRegistry.defaults(), evaluator);
        service.setCastImmobilizeService(immobilizeService);

        doAnswer(invocation -> {
            service.cancelCast(player, CastCancelReason.OTHER);
            return null;
        }).when(immobilizeService).immobilize(player);

        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());

        // cancelCast triggers first release before immobilize completes, then startWarpCast detects
        // session.cancelled and calls release again so the player cannot remain immobilized.
        verify(immobilizeService).immobilize(player);
        verify(immobilizeService, times(2)).release(player);
        assertThat(service.isCasting(playerId)).isFalse();
        assertThat(activeTickConsumer.get()).isNull();
    }

    @Test
    void cancelCastOnInteractReleasesImmobilizeStopsAnimationAndReturnsTrue() {
        final WarpCostEvaluator evaluator = mock(WarpCostEvaluator.class);
        when(evaluator.evaluate(any(), any(), any())).thenReturn(WarpEvaluation.permitted(Component.empty()));

        final CastImmobilizeService immobilizeService = mock(CastImmobilizeService.class);
        final CastSection castEnabled = new CastSection(4, "steve", "cast", "", true, CastCancelSection.defaults());
        final AetheryteService service = createService(castEnabled, VariationRegistry.defaults(), evaluator);
        service.setCastImmobilizeService(immobilizeService);

        assertThat(service.cancelCast(player, CastCancelReason.INTERACT)).isFalse();

        service.warpToAetheryte(player, sourceAetheryte, destAetheryte.id());
        assertThat(service.isCasting(playerId)).isTrue();

        assertThat(service.cancelCast(player, CastCancelReason.INTERACT)).isTrue();
        assertThat(service.isCasting(playerId)).isFalse();
        verify(immobilizeService).release(player);
        verify(modelService).stopPlayerAnimation(player);
    }

    @Test
    void hubToNetworkWarpResolvesFourTierPrecedenceWhileOtherWarpsUseNormalCast() {
        final AetheryteVariation base = AetheryteVariation.defaultVariation();
        final AetheryteVariation hubVar = new AetheryteVariation(
                base.displayName(),
                base.item(),
                base.recipe(),
                base.model(),
                base.hitbox(),
                base.teleport(),
                base.placement(),
                base.limit(),
                new WarpOverrideSection(
                        DistanceSection.inherit(),
                        null,
                        new CastSection(30, "var_norm_model", "var_norm_cast", "var_norm_arrive", false, CastCancelSection.inherit()),
                        new CastSection(5, null, "hub_var_cast", null, true, new CastCancelSection(null, false, null)),
                        VisibilityCostSection.inherit(),
                        PetsWarpSection.inherit()
                ),
                base.bettermodel()
        );
        final AetheryteVariation hubVarNoOverride = new AetheryteVariation(
                base.displayName(),
                base.item(),
                base.recipe(),
                base.model(),
                base.hitbox(),
                base.teleport(),
                base.placement(),
                base.limit(),
                new WarpOverrideSection(
                        DistanceSection.inherit(),
                        null,
                        new CastSection(25, "var_only_model", "var_only_cast", "var_only_arrive", false, CastCancelSection.inherit()),
                        CastSection.inherit(),
                        VisibilityCostSection.inherit(),
                        PetsWarpSection.inherit()
                ),
                base.bettermodel()
        );
        final VariationRegistry variations = new VariationRegistry(Map.of(
                Aetheryte.DEFAULT_VARIATION, base,
                "hub_var", hubVar,
                "hub_no_override", hubVarNoOverride
        ), LoggerFactory.getLogger(WarpCastServiceTest.class));

        final UUID worldId = sourceAetheryte.worldId();
        final UUID networkId = UUID.randomUUID();
        final Aetheryte hub = new Aetheryte(
                UUID.randomUUID(), "CityHub", worldId, "world", 5, 64, 5, playerId, 1L,
                "hub_var", "Caster", AetheryteVisibility.PUBLIC, Set.of(), null
        );
        final Aetheryte hubFallback = new Aetheryte(
                UUID.randomUUID(), "RelayHub", worldId, "world", 15, 64, 15, playerId, 2L,
                "hub_no_override", "Caster", AetheryteVisibility.PUBLIC, Set.of(), null
        );
        final Aetheryte child1 = new Aetheryte(
                UUID.randomUUID(), "District1", worldId, "world", 100, 64, 100, playerId, 3L,
                Aetheryte.DEFAULT_VARIATION, "Caster", AetheryteVisibility.PUBLIC, Set.of(), networkId
        );
        final Aetheryte child2 = new Aetheryte(
                UUID.randomUUID(), "District2", worldId, "world", 200, 64, 200, playerId, 4L,
                Aetheryte.DEFAULT_VARIATION, "Caster", AetheryteVisibility.PUBLIC, Set.of(), networkId
        );
        final Aetheryte otherNetworkChild = new Aetheryte(
                UUID.randomUUID(), "OtherNetChild", worldId, "world", 250, 64, 250, playerId, 5L,
                Aetheryte.DEFAULT_VARIATION, "Caster", AetheryteVisibility.PUBLIC, Set.of(), UUID.randomUUID()
        );

        final AetheryteNetwork net = new AetheryteNetwork(
                networkId, "Capital", worldId, "world", playerId, "Caster", 1L,
                AetheryteVisibility.PUBLIC, Set.of(), hub.id()
        );
        registry.register(world, hub);
        registry.register(world, hubFallback);
        registry.register(world, child1);
        registry.register(world, child2);
        registry.register(world, otherNetworkChild);
        registry.registerNetwork(world, net);

        final CastSection globalNormal = new CastSection(80, "global_model", "global_cast", "global_arrive", false, CastCancelSection.defaults());
        final CastSection globalHubToNet = new CastSection(12, "global_hub_model", "global_hub_cast", null, null, new CastCancelSection(false, true, false));

        final WarpCostEvaluator evaluator = mock(WarpCostEvaluator.class);
        when(evaluator.evaluate(any(), any(), any())).thenReturn(WarpEvaluation.permitted(Component.empty()));
        when(evaluator.charge(any(), any(), any())).thenReturn(true);

        final AetheryteService service = createService(globalNormal, variations, evaluator);
        service.setGlobalHubToNetworkCastSupplier(() -> globalHubToNet);

        // Hub -> child in hosted network uses 4-tier merge:
        // ticks=5 (hubVar.hubToNetworkCast), model=global_hub_model (globalHubToNet), cast=hub_var_cast (hubVar.hubToNetworkCast), arrive=var_norm_arrive (hubVar.cast), immobilize=true (hubVar.hubToNetworkCast), cancelOnMove=false (globalHubToNet), cancelOnDamage=false (hubVar.hubToNetworkCast)
        final CastSection.Resolved hubToChildResolved = service.resolveWarpCast(hub, child1);
        assertThat(hubToChildResolved.ticks()).isEqualTo(5);
        assertThat(hubToChildResolved.playerModel()).isEqualTo("global_hub_model");
        assertThat(hubToChildResolved.castAnimation()).isEqualTo("hub_var_cast");
        assertThat(hubToChildResolved.arriveAnimation()).isEqualTo("var_norm_arrive");
        assertThat(hubToChildResolved.immobilize()).isTrue();
        assertThat(hubToChildResolved.cancelOnMove()).isFalse();
        assertThat(hubToChildResolved.cancelOnDamage()).isFalse();

        // Hub -> child when hub variation has inherit hubToNetworkCast falls back to global hubToNetworkCast then variation cast
        final AetheryteNetwork fallbackNet = new AetheryteNetwork(
                UUID.randomUUID(), "RelayNet", worldId, "world", playerId, "Caster", 6L,
                AetheryteVisibility.PUBLIC, Set.of(), hubFallback.id()
        );
        final Aetheryte fallbackChild = new Aetheryte(
                UUID.randomUUID(), "RelayChild", worldId, "world", 300, 64, 300, playerId, 7L,
                Aetheryte.DEFAULT_VARIATION, "Caster", AetheryteVisibility.PUBLIC, Set.of(), fallbackNet.id()
        );
        registry.register(world, fallbackChild);
        registry.registerNetwork(world, fallbackNet);
        final CastSection.Resolved fallbackResolved = service.resolveWarpCast(hubFallback, fallbackChild);
        assertThat(fallbackResolved.ticks()).isEqualTo(12);
        assertThat(fallbackResolved.playerModel()).isEqualTo("global_hub_model");
        assertThat(fallbackResolved.castAnimation()).isEqualTo("global_hub_cast");
        assertThat(fallbackResolved.arriveAnimation()).isEqualTo("var_only_arrive");

        // Non-hosted / normal / child->hub / child->child warps must ignore hubToNetworkCast
        assertThat(service.resolveWarpCast(hub, destAetheryte).ticks()).isEqualTo(30);
        assertThat(service.resolveWarpCast(hub, otherNetworkChild).ticks()).isEqualTo(30);
        assertThat(service.resolveWarpCast(child1, hub).ticks()).isEqualTo(80);
        assertThat(service.resolveWarpCast(child1, child2).ticks()).isEqualTo(80);

        // Actual warp execution uses the resolved hub->network ticks (5 ticks -> teleports on 5th tick)
        service.warpToAetheryte(player, hub, child1.id());
        assertThat(service.isCasting(playerId)).isTrue();
        verify(modelService).playPlayerAnimation(player, "global_hub_model", "hub_var_cast", PlayerAnimationMode.HOLD_ON_LAST);
        for (int i = 0; i < 4; i++) {
            activeTickConsumer.get().accept(activeTickTask);
        }
        assertThat(service.isCasting(playerId)).isTrue();
        activeTickConsumer.get().accept(activeTickTask);
        assertThat(service.isCasting(playerId)).isFalse();
        verify(player).teleportAsync(any());
    }
}
