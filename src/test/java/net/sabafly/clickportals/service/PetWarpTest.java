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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.entity.Leashable;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.config.CastSection;
import net.sabafly.clickportals.config.DistanceSection;
import net.sabafly.clickportals.config.PetsWarpSection;
import net.sabafly.clickportals.config.PluginConfig;
import net.sabafly.clickportals.config.PluginConfig.WarpSection;
import net.sabafly.clickportals.config.VisibilityCostSection;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.CostType;
import net.sabafly.clickportals.economy.EconomyProvider;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.model.AetheryteModelService;
import net.sabafly.clickportals.route.DimensionRouter;
import net.sabafly.clickportals.route.DimensionRoutesConfig;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation;
import net.sabafly.clickportals.sound.SoundsConfig.SoundsSection;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.BetterModelSection;
import net.sabafly.clickportals.variation.AetheryteVariation.HitboxSection;
import net.sabafly.clickportals.variation.AetheryteVariation.ItemSection;
import net.sabafly.clickportals.variation.AetheryteVariation.LimitSection;
import net.sabafly.clickportals.variation.AetheryteVariation.ModelSection;
import net.sabafly.clickportals.variation.AetheryteVariation.NetworkSection;
import net.sabafly.clickportals.variation.AetheryteVariation.PlacementSection;
import net.sabafly.clickportals.variation.AetheryteVariation.RecipeSection;
import net.sabafly.clickportals.variation.AetheryteVariation.TeleportSection;
import net.sabafly.clickportals.variation.AetheryteVariation.VariationType;
import net.sabafly.clickportals.variation.AetheryteVariation.WarpOverrideSection;
import net.sabafly.clickportals.variation.VariationRegistry;
import net.sabafly.clickportals.visual.NoopHologramService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

class PetWarpTest {

    private Player player;
    private UUID playerId;
    private World world;
    private UUID worldId;
    private EconomyProvider economy;
    private DimensionRouter router;
    private VariationRegistry variationRegistry;
    private Messages messages;

    private @org.jspecify.annotations.Nullable Field serverField;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        playerId = UUID.randomUUID();
        world = mock(World.class);
        worldId = UUID.randomUUID();
        economy = mock(EconomyProvider.class);
        router = mock(DimensionRouter.class);
        variationRegistry = mock(VariationRegistry.class);
        messages = Messages.defaults();

        when(world.getUID()).thenReturn(worldId);
        when(world.getName()).thenReturn("world");

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("PetOwner");
        when(player.isOnline()).thenReturn(true);
        when(player.isDead()).thenReturn(false);
        when(player.hasPermission("clickportals.use")).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 5.5, 64.0, 5.5));
        when(player.calculateTotalExperiencePoints()).thenReturn(100);
        when(player.getLevel()).thenReturn(10);
        when(player.getFoodLevel()).thenReturn(20);

        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(player)).thenReturn(1000.0);
        when(economy.has(eq(player), anyDouble())).thenAnswer(inv -> (double) inv.getArgument(1) <= 1000.0);
        when(economy.withdraw(eq(player), anyDouble())).thenReturn(true);
        when(economy.deposit(eq(player), anyDouble())).thenReturn(true);
        when(economy.format(anyDouble())).thenAnswer(inv -> String.format("$%.2f", (double) inv.getArgument(0)));

        when(variationRegistry.getOrDefault(any())).thenReturn(AetheryteVariation.defaultVariation());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (serverField != null) {
            serverField.set(null, null);
        }
    }

    private Wolf mockLeashedWolf(final @org.jspecify.annotations.Nullable Entity holder, final boolean valid, final boolean dead) {
        final Wolf wolf = mock(Wolf.class);
        when(wolf.isValid()).thenReturn(valid);
        when(wolf.isDead()).thenReturn(dead);
        when(wolf.getWorld()).thenReturn(world);
        when(wolf.getLocation()).thenReturn(new Location(world, 6.0, 64.0, 6.0));
        when(wolf.isLeashed()).thenReturn(holder != null);
        when(wolf.getLeashHolder()).thenReturn(holder);
        return wolf;
    }

    private PluginConfig configWithPets(final PetsWarpSection pets) {
        return new PluginConfig(
                PluginConfig.LanguageSection.defaults(),
                PluginConfig.HologramSection.defaults(),
                PluginConfig.LimitsSection.defaults(),
                new WarpSection(
                        new DistanceSection(0.0, -1.0, true),
                        List.of(),
                        true,
                        CastSection.defaults(),
                        VisibilityCostSection.defaults(),
                        pets
                ),
                DimensionRoutesConfig.defaults()
        );
    }

    private ConfiguredWarpCostEvaluator createEvaluator(final PluginConfig config) {
        return new ConfiguredWarpCostEvaluator(
                () -> config,
                variationRegistry,
                () -> economy,
                () -> router,
                messages,
                id -> id.equals(worldId) ? world : null,
                null
        );
    }

    @Test
    void findLeashedEntitiesFiltersOnlyValidEntitiesLeashedToPlayer() {
        final Wolf mine = mockLeashedWolf(player, true, false);
        final Player otherPlayer = mock(Player.class);
        when(otherPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        final Wolf others = mockLeashedWolf(otherPlayer, true, false);
        final Wolf unleashed = mockLeashedWolf(null, true, false);
        final Wolf deadWolf = mockLeashedWolf(player, true, true);
        final Wolf invalidWolf = mockLeashedWolf(player, false, false);
        final ArmorStand stand = mock(ArmorStand.class);
        when(stand.isValid()).thenReturn(true);

        when(player.getNearbyEntities(PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS))
                .thenReturn(List.of(mine, others, unleashed, deadWolf, invalidWolf, stand));

        final List<Leashable> found = PetWarpHelper.findLeashedEntities(player);
        assertThat(found).containsExactly(mine);
    }

    @Test
    void petCostUsesPetCountExpressionVariableInEvaluateAndBatch() {
        final Wolf pet1 = mockLeashedWolf(player, true, false);
        final Wolf pet2 = mockLeashedWolf(player, true, false);
        when(player.getNearbyEntities(PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS))
                .thenReturn(List.of(pet1, pet2));

        final PluginConfig config = configWithPets(new PetsWarpSection(
                true,
                5,
                List.of(CostEntry.of(CostType.VAULT, "25 * $pet_count"))
        ));
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator(config);
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 5, 64, 5, playerId, 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 100, 64, 100, playerId, 0L);

        final WarpEvaluation single = evaluator.evaluate(player, src, dest);
        assertThat(single.allowed()).isTrue();
        assertThat(PlainTextComponentSerializer.plainText().serialize(single.costDisplay())).contains("$50.00");

        final List<WarpEvaluation> batch = evaluator.evaluateBatch(player, src, List.of(dest));
        assertThat(batch).hasSize(1);
        assertThat(batch.getFirst().allowed()).isTrue();
        assertThat(PlainTextComponentSerializer.plainText().serialize(batch.getFirst().costDisplay())).contains("$50.00");

        // No pets => pet fee does not apply
        when(player.getNearbyEntities(PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS))
                .thenReturn(List.of());
        final WarpEvaluation noPets = evaluator.evaluate(player, src, dest);
        assertThat(noPets.allowed()).isTrue();
        assertThat(PlainTextComponentSerializer.plainText().serialize(noPets.costDisplay())).doesNotContain("$");
    }

    @Test
    void tooManyPetsDeniesEvaluateAndEvaluateBatch() {
        final Wolf pet1 = mockLeashedWolf(player, true, false);
        final Wolf pet2 = mockLeashedWolf(player, true, false);
        final Wolf pet3 = mockLeashedWolf(player, true, false);
        when(player.getNearbyEntities(PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS))
                .thenReturn(List.of(pet1, pet2, pet3));

        final PluginConfig config = configWithPets(new PetsWarpSection(true, 2, List.of()));
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator(config);
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 5, 64, 5, playerId, 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 100, 64, 100, playerId, 0L);

        final WarpEvaluation single = evaluator.evaluate(player, src, dest);
        assertThat(single.allowed()).isFalse();
        assertThat(single.denyReasonCode()).isEqualTo(WarpEvaluation.DenyReason.TOO_MANY_PETS);
        final net.kyori.adventure.text.Component denyReason =
                java.util.Objects.requireNonNull(single.denyReason());
        assertThat(PlainTextComponentSerializer.plainText().serialize(denyReason)).contains("2");

        final List<WarpEvaluation> batch = evaluator.evaluateBatch(player, src, List.of(dest));
        assertThat(batch).hasSize(1);
        assertThat(batch.getFirst().allowed()).isFalse();
        assertThat(batch.getFirst().denyReasonCode()).isEqualTo(WarpEvaluation.DenyReason.TOO_MANY_PETS);
    }

    @Test
    void disabledPetsIgnoresLeashedEntitiesAndCost() {
        final Wolf pet1 = mockLeashedWolf(player, true, false);
        final Wolf pet2 = mockLeashedWolf(player, true, false);
        when(player.getNearbyEntities(PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS))
                .thenReturn(List.of(pet1, pet2));

        final PluginConfig config = configWithPets(new PetsWarpSection(
                false,
                1,
                List.of(CostEntry.of(CostType.VAULT, "50 * $pet_count"))
        ));
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator(config);
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 5, 64, 5, playerId, 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 100, 64, 100, playerId, 0L);

        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isTrue();
        assertThat(PlainTextComponentSerializer.plainText().serialize(eval.costDisplay())).doesNotContain("$");
    }

    @Test
    void variationPetsOverrideGlobalLimitsAndCost() {
        final Wolf pet1 = mockLeashedWolf(player, true, false);
        final Wolf pet2 = mockLeashedWolf(player, true, false);
        when(player.getNearbyEntities(PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS))
                .thenReturn(List.of(pet1, pet2));

        final AetheryteVariation customVar = new AetheryteVariation(
                "<aqua>Pet Shrine</aqua>",
                VariationType.NORMAL,
                ItemSection.defaults(),
                RecipeSection.defaults(),
                ModelSection.defaults(),
                HitboxSection.defaults(),
                TeleportSection.defaults(),
                PlacementSection.defaults(),
                LimitSection.defaults(),
                new WarpOverrideSection(
                        DistanceSection.inherit(),
                        null,
                        CastSection.inherit(),
                        VisibilityCostSection.inherit(),
                        new PetsWarpSection(true, 4, List.of())
                ),
                NetworkSection.defaults(),
                BetterModelSection.defaults(),
                SoundsSection.inherit()
        );
        when(variationRegistry.getOrDefault("pet_shrine")).thenReturn(customVar);

        final PluginConfig config = configWithPets(new PetsWarpSection(
                true,
                1,
                List.of(CostEntry.of(CostType.VAULT, "100"))
        ));
        final ConfiguredWarpCostEvaluator evaluator = createEvaluator(config);
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Shrine", worldId, "world", 5, 64, 5, playerId, 0L, "pet_shrine");
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 100, 64, 100, playerId, 0L);

        final WarpEvaluation eval = evaluator.evaluate(player, src, dest);
        assertThat(eval.allowed()).isTrue();
        assertThat(PlainTextComponentSerializer.plainText().serialize(eval.costDisplay())).doesNotContain("$");
    }

    @Test
    void warpWithLeashedPetsDetachesTeleportsAndReattachesOnArrival() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);

        final Server server = mock(Server.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));

        final Block solidFloor = mock(Block.class);
        when(solidFloor.getType()).thenReturn(Material.STONE);
        when(solidFloor.isPassable()).thenReturn(false);
        when(solidFloor.isSolid()).thenReturn(true);
        final Block airBlock = mock(Block.class);
        when(airBlock.getType()).thenReturn(Material.AIR);
        when(airBlock.isPassable()).thenReturn(true);
        when(airBlock.isSolid()).thenReturn(false);
        when(world.getBlockAt(any(Integer.class), any(Integer.class), any(Integer.class))).thenReturn(airBlock);
        when(world.getBlockAt(100, 63, 100)).thenReturn(solidFloor);

        when(server.getWorld(worldId)).thenReturn(world);
        when(server.isOwnedByCurrentRegion(any(World.class), any(Integer.class), any(Integer.class))).thenReturn(true);
        when(server.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);

        final RegionScheduler regionScheduler = mock(RegionScheduler.class);
        doAnswer(inv -> {
            final Runnable runnable = inv.getArgument(2);
            runnable.run();
            return null;
        }).when(regionScheduler).execute(any(), any(Location.class), any());
        when(server.getRegionScheduler()).thenReturn(regionScheduler);
        serverField.set(null, server);

        final EntityScheduler playerScheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(playerScheduler);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            final Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) inv.getArgument(1);
            consumer.accept(mock(ScheduledTask.class));
            return mock(ScheduledTask.class);
        }).when(playerScheduler).run(any(), any(), any());
        when(player.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));

        final Wolf pet = mockLeashedWolf(player, true, false);
        final AtomicBoolean leashedState = new AtomicBoolean(true);
        when(pet.isLeashed()).thenAnswer(inv -> leashedState.get());
        when(pet.setLeashHolder(any())).thenAnswer(inv -> {
            leashedState.set(inv.getArgument(0) != null);
            return true;
        });

        final EntityScheduler petScheduler = mock(EntityScheduler.class);
        final List<Runnable> delayedTasks = new ArrayList<>();
        when(pet.getScheduler()).thenReturn(petScheduler);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            final Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) inv.getArgument(1);
            delayedTasks.add(() -> consumer.accept(mock(ScheduledTask.class)));
            return mock(ScheduledTask.class);
        }).when(petScheduler).runDelayed(any(), any(), any(), anyLong());
        when(pet.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));

        when(player.getNearbyEntities(PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS, PetWarpHelper.SEARCH_RADIUS))
                .thenReturn(List.of(pet));

        final Plugin plugin = mock(Plugin.class);
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger(PetWarpTest.class));
        final AetheryteRegistry registry = new AetheryteRegistry();
        final Aetheryte src = new Aetheryte(UUID.randomUUID(), "Origin", worldId, "world", 5, 64, 5, playerId, 0L);
        final Aetheryte dest = new Aetheryte(UUID.randomUUID(), "Dest", worldId, "world", 100, 64, 100, playerId, 0L);
        registry.register(world, src);
        registry.register(world, dest);

        final PluginConfig config = configWithPets(PetsWarpSection.defaults());
        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                NoopHologramService.INSTANCE,
                mock(AetheryteModelService.class),
                messages,
                () -> -1,
                () -> 0,
                () -> 0.0,
                p -> 0L,
                (p, t) -> {},
                createEvaluator(config)
        );
        service.setGlobalPetsWarpSupplier(() -> config.warp().pets());

        service.warpToAetheryte(player, src, dest.id());

        final InOrder order = Mockito.inOrder(pet, player);
        order.verify(pet).setLeashHolder(null);
        order.verify(player).teleportAsync(any(Location.class));
        order.verify(pet).teleportAsync(any(Location.class));

        assertThat(delayedTasks).hasSize(1);
        delayedTasks.getFirst().run();
        verify(pet).setLeashHolder(player);
    }

    @Test
    void crossRegionDetachCompletesBeforePlayerTeleportStarts() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);

        final Server server = mock(Server.class);
        final Wolf pet = mockLeashedWolf(player, true, false);
        when(server.isOwnedByCurrentRegion(pet)).thenReturn(false);
        serverField.set(null, server);

        final EntityScheduler petScheduler = mock(EntityScheduler.class);
        final List<Runnable> queuedPetTasks = new ArrayList<>();
        when(pet.getScheduler()).thenReturn(petScheduler);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            final Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) inv.getArgument(1);
            queuedPetTasks.add(() -> consumer.accept(mock(ScheduledTask.class)));
            return mock(ScheduledTask.class);
        }).when(petScheduler).run(any(), any(), any());

        final Plugin plugin = mock(Plugin.class);
        final CompletableFuture<List<Leashable>> future = PetWarpHelper.detachLeashedEntities(plugin, player, List.of(pet));
        assertThat(future).isNotDone();
        verify(pet, never()).setLeashHolder(any());

        assertThat(queuedPetTasks).hasSize(1);
        queuedPetTasks.getFirst().run();
        assertThat(future).isCompletedWithValue(List.of(pet));
        verify(pet).setLeashHolder(null);
    }
}
