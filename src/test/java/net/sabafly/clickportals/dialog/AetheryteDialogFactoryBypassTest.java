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
package net.sabafly.clickportals.dialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sabafly.clickportals.ClickPortals;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteNetwork;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.aetheryte.AetheryteVisibility;
import net.sabafly.clickportals.dialog.WarpDestinationQuery.Candidate;
import net.sabafly.clickportals.dialog.WarpDestinationQuery.NetworkGroup;
import net.sabafly.clickportals.dialog.WarpDestinationQuery.WorldGroup;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import net.sabafly.clickportals.route.DimensionRoutesConfig;
import net.sabafly.clickportals.route.DimensionWorldConfig;
import net.sabafly.clickportals.service.AetheryteService;
import net.sabafly.clickportals.service.AetheryteService.WarpCostEvaluator;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation;
import net.sabafly.clickportals.variation.VariationRegistry;
import net.sabafly.clickportals.visual.NoopHologramService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AetheryteDialogFactoryBypassTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final UUID SOURCE_WORLD_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID DEST_WORLD_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    @Test
    void destinationOnlyDimensionBypassAppearsInWorldCategoryAndDestinationTooltip() {
        final ClickPortals plugin = mock(ClickPortals.class);
        final AetheryteRegistry registry = new AetheryteRegistry();
        final Messages messages = Messages.defaults();
        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                VariationRegistry.defaults(),
                NoopHologramService.INSTANCE,
                messages,
                () -> -1,
                (p, s, d) -> WarpEvaluation.permitted(Component.text("Free"))
        );
        service.setDimensionConfigSupplier(() -> new DimensionRoutesConfig(
                List.of(),
                Map.of(),
                List.of(),
                Map.of(
                        "world", new DimensionWorldConfig("<green>Origin</green>", true, true),
                        "world_restricted", new DimensionWorldConfig("<red>RestrictedDest</red>", true, false)
                )
        ));
        final AetheryteDialogFactory factory = new AetheryteDialogFactory(service, messages);

        final Aetheryte source = new Aetheryte(
                UUID.randomUUID(), "OriginGate", SOURCE_WORLD_ID, "world", 0, 64, 0, UUID.randomUUID(), 1L
        );
        final Aetheryte restrictedDest = new Aetheryte(
                UUID.randomUUID(), "RestrictedGate", DEST_WORLD_ID, "world_restricted", 10, 64, 10, UUID.randomUUID(), 2L
        );
        final Aetheryte allowedDest = new Aetheryte(
                UUID.randomUUID(), "NormalGate", SOURCE_WORLD_ID, "world", 20, 64, 20, UUID.randomUUID(), 3L
        );

        final Player player = mock(Player.class);
        when(player.locale()).thenReturn(Locale.US);
        when(player.hasPermission(Permissions.USE)).thenReturn(true);
        when(player.hasPermission(Permissions.BYPASS_DIMENSION)).thenReturn(true);

        final Component rootBody = factory.buildRootMenuBody(
                player,
                source,
                "Alice",
                Component.text("Origin"),
                "world",
                3,
                2,
                List.of(restrictedDest, allowedDest)
        );
        assertThat(PLAIN.serialize(rootBody)).doesNotContain("Using Aetherytes in");

        final WorldGroup restrictedGroup = new WorldGroup(
                DEST_WORLD_ID, "world_restricted", "RestrictedDest", World.Environment.CUSTOM, 1, false
        );
        final Component worldTooltip = factory.buildWorldCategoryTooltip(
                player,
                service.resolveDimensionDisplayComponent("world_restricted"),
                restrictedGroup
        );
        assertThat(PLAIN.serialize(worldTooltip))
                .contains("Bypassed:")
                .contains("dimension usage (RestrictedDest)");

        final Component destTooltip = factory.buildDestinationTooltip(
                player,
                source,
                WarpDialogState.root(WarpSortOrder.NAME).toWorld(DEST_WORLD_ID),
                new Candidate(restrictedDest, "Bob", false, OptionalDouble.empty()),
                WarpEvaluation.permitted(Component.text("Free"))
        );
        assertThat(PLAIN.serialize(destTooltip))
                .contains("Click to teleport")
                .contains("Bypassed:")
                .contains("dimension usage (RestrictedDest)");

        final Component allowedDestTooltip = factory.buildDestinationTooltip(
                player,
                source,
                WarpDialogState.root(WarpSortOrder.NAME).toWorld(SOURCE_WORLD_ID),
                new Candidate(allowedDest, "Bob", false, OptionalDouble.of(20.0)),
                WarpEvaluation.permitted(Component.text("Free"))
        );
        assertThat(PLAIN.serialize(allowedDestTooltip)).doesNotContain("Bypassed:");

        final Component deniedDestTooltip = factory.buildDestinationTooltip(
                player,
                source,
                WarpDialogState.root(WarpSortOrder.NAME).toWorld(DEST_WORLD_ID),
                new Candidate(restrictedDest, "Bob", false, OptionalDouble.empty()),
                WarpEvaluation.denied(
                        Component.text("100 Coins"),
                        Component.text("Insufficient funds"),
                        WarpEvaluation.DenyReason.INSUFFICIENT_COST
                )
        );
        assertThat(PLAIN.serialize(deniedDestTooltip)).doesNotContain("Bypassed:");
    }

    @Test
    void warpCostBypassAppearsInRootDialogBodyAndTeleportChat() throws Exception {
        final Server server = mock(Server.class);
        final Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
        try {
            final World world = mock(World.class);
            when(world.getUID()).thenReturn(SOURCE_WORLD_ID);
            when(world.getName()).thenReturn("world");
            when(world.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(server.getWorld(SOURCE_WORLD_ID)).thenReturn(world);
            when(server.isOwnedByCurrentRegion(any(), any(int.class), any(int.class))).thenReturn(true);

            final RegionScheduler regionScheduler = mock(RegionScheduler.class);
            when(server.getRegionScheduler()).thenReturn(regionScheduler);
            org.mockito.Mockito.doAnswer(inv -> {
                ((Runnable) inv.getArgument(2)).run();
                return null;
            }).when(regionScheduler).execute(any(), any(Location.class), any());

            final Block safeFloor = mock(Block.class);
            when(safeFloor.isSolid()).thenReturn(true);
            when(safeFloor.getType()).thenReturn(Material.STONE);
            final Block safeAir = mock(Block.class);
            when(safeAir.isPassable()).thenReturn(true);
            when(safeAir.getType()).thenReturn(Material.AIR);
            when(world.getBlockAt(any(int.class), any(int.class), any(int.class))).thenAnswer(inv -> {
                final int y = inv.getArgument(1);
                return y <= 63 ? safeFloor : safeAir;
            });

            final ClickPortals plugin = mock(ClickPortals.class);
            final AetheryteRegistry registry = new AetheryteRegistry();
            final Aetheryte source = new Aetheryte(
                    UUID.randomUUID(), "Origin", SOURCE_WORLD_ID, "world", 0, 64, 0, UUID.randomUUID(), 1L
            );
            final Aetheryte dest = new Aetheryte(
                    UUID.randomUUID(), "Remote", SOURCE_WORLD_ID, "world", 50, 64, 50, UUID.randomUUID(), 2L
            );
            registry.register(world, source);
            registry.register(world, dest);

            final WarpCostEvaluator evaluator = (p, src, dst) -> WarpEvaluation.permitted(Component.text("100 Coins"));
            final Messages messages = Messages.defaults();
            final AetheryteService service = new AetheryteService(
                    plugin,
                    registry,
                    VariationRegistry.defaults(),
                    NoopHologramService.INSTANCE,
                    messages,
                    () -> -1,
                    evaluator
            );
            final AetheryteDialogFactory factory = new AetheryteDialogFactory(service, messages);

            final Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.locale()).thenReturn(Locale.US);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, 0.0, 64.0, 0.0));
            when(player.hasPermission(Permissions.USE)).thenReturn(true);
            when(player.hasPermission(Permissions.BYPASS_COST_WARP)).thenReturn(true);
            when(player.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));

            final EntityScheduler entityScheduler = mock(EntityScheduler.class);
            when(player.getScheduler()).thenReturn(entityScheduler);
            org.mockito.Mockito.doAnswer(inv -> {
                @SuppressWarnings("unchecked")
                final Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) inv.getArgument(1);
                consumer.accept(mock(ScheduledTask.class));
                return mock(ScheduledTask.class);
            }).when(entityScheduler).run(any(), any(), any());

            final Component rootBody = factory.buildRootMenuBody(
                    player,
                    source,
                    "Alice",
                    Component.text("world"),
                    "world",
                    2,
                    1,
                    List.of(dest)
            );
            assertThat(PLAIN.serialize(rootBody))
                    .contains("Bypassing warp cost (100 Coins)");

            service.warpToAetheryte(player, source, dest.id());
            final ArgumentCaptor<Component> chatCaptor = ArgumentCaptor.forClass(Component.class);
            verify(player, org.mockito.Mockito.times(2)).sendMessage(chatCaptor.capture());
            assertThat(PLAIN.serialize(chatCaptor.getAllValues().get(1)))
                    .contains("Bypassed restrictions:")
                    .contains("warp cost (100 Coins)");

            // Free destination or missing permission produces no cost-bypass line in dialog body
            when(player.hasPermission(Permissions.BYPASS_COST_WARP)).thenReturn(false);
            final Component withoutBypassPerm = factory.buildRootMenuBody(
                    player,
                    source,
                    "Alice",
                    Component.text("world"),
                    "world",
                    2,
                    1,
                    List.of(dest)
            );
            assertThat(PLAIN.serialize(withoutBypassPerm)).doesNotContain("warp cost");

            final AetheryteService freeService = new AetheryteService(
                    plugin,
                    registry,
                    VariationRegistry.defaults(),
                    NoopHologramService.INSTANCE,
                    messages,
                    () -> -1,
                    (p, src, dst) -> WarpEvaluation.permitted(messages.freeCostLabel(p))
            );
            final AetheryteDialogFactory freeFactory = new AetheryteDialogFactory(freeService, messages);
            when(player.hasPermission(Permissions.BYPASS_COST_WARP)).thenReturn(true);
            final Component freeBody = freeFactory.buildRootMenuBody(
                    player,
                    source,
                    "Alice",
                    Component.text("world"),
                    "world",
                    2,
                    1,
                    List.of(dest)
            );
            assertThat(PLAIN.serialize(freeBody)).doesNotContain("warp cost");
        } finally {
            serverField.set(null, null);
        }
    }

    @Test
    void hubRootPlanSeparatesHostedNetworksAndHidesEmptyOrNonHubNetworkCategories() throws Exception {
        final Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        try {
            final Server server = mock(Server.class);
            final World world = mock(World.class);
            when(world.getUID()).thenReturn(SOURCE_WORLD_ID);
            when(world.getName()).thenReturn("world");
            when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
            when(world.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(server.getWorld(SOURCE_WORLD_ID)).thenReturn(world);
            serverField.set(null, server);

            final ClickPortals plugin = mock(ClickPortals.class);
            final AetheryteRegistry registry = new AetheryteRegistry();
            final Messages messages = Messages.defaults();
            final AetheryteService service = new AetheryteService(
                    plugin,
                    registry,
                    VariationRegistry.defaults(),
                    NoopHologramService.INSTANCE,
                    messages,
                    () -> -1,
                    (p, s, d) -> WarpEvaluation.permitted(Component.text("Free"))
            );
            final AetheryteDialogFactory factory = new AetheryteDialogFactory(service, messages);

            final UUID ownerId = UUID.randomUUID();
            final UUID otherId = UUID.randomUUID();
            final UUID net1Id = UUID.randomUUID();
            final UUID emptyNetId = UUID.randomUUID();

            final Aetheryte hub = new Aetheryte(
                    UUID.randomUUID(), "CapitalHub", SOURCE_WORLD_ID, "world", 0, 64, 0, ownerId, 1L
            );
            final Aetheryte childAccessible = new Aetheryte(
                    UUID.randomUUID(), "Plaza", SOURCE_WORLD_ID, "world", 10, 64, 0, ownerId, 2L,
                    Aetheryte.DEFAULT_VARIATION, "Alice", AetheryteVisibility.PUBLIC, Set.of(), net1Id
            );
            final Aetheryte childPrivateOther = new Aetheryte(
                    UUID.randomUUID(), "Vault", SOURCE_WORLD_ID, "world", 20, 64, 0, otherId, 3L,
                    Aetheryte.DEFAULT_VARIATION, "Bob", AetheryteVisibility.OWNER_ONLY, Set.of(), emptyNetId
            );
            final Aetheryte normalDest = new Aetheryte(
                    UUID.randomUUID(), "Outpost", SOURCE_WORLD_ID, "world", 30, 64, 0, ownerId, 4L
            );

            final AetheryteNetwork net1 = new AetheryteNetwork(
                    net1Id, "Capital Transit", SOURCE_WORLD_ID, "world", ownerId, "Alice", 1L,
                    AetheryteVisibility.PUBLIC, Set.of(), hub.id()
            );
            final AetheryteNetwork emptyNet = new AetheryteNetwork(
                    emptyNetId, "Private Net", SOURCE_WORLD_ID, "world", otherId, "Bob", 2L,
                    AetheryteVisibility.OWNER_ONLY, Set.of(), hub.id()
            );

            registry.register(world, hub);
            registry.register(world, childAccessible);
            registry.register(world, childPrivateOther);
            registry.register(world, normalDest);
            registry.registerNetwork(world, net1);
            registry.registerNetwork(world, emptyNet);

            final Player viewer = mock(Player.class);
            when(viewer.getUniqueId()).thenReturn(ownerId);
            when(viewer.locale()).thenReturn(Locale.US);
            when(viewer.hasPermission(Permissions.USE)).thenReturn(true);

            final List<Aetheryte> all = registry.getAll();
            final AetheryteDialogFactory.RootMenuPlan hubPlan = factory.planRootMenu(viewer, hub, all);
            assertThat(hubPlan.networkGroups()).containsExactly(
                    new NetworkGroup(net1Id, "Capital Transit", 1)
            );
            assertThat(hubPlan.normalReachableCount()).isEqualTo(1);
            assertThat(hubPlan.totalReachableCount()).isEqualTo(2);
            assertThat(hubPlan.worldGroups()).hasSize(1);
            assertThat(hubPlan.worldGroups().get(0).destinationCount()).isEqualTo(1);

            final List<Candidate> networkList = factory.queryDestinationCandidates(
                    viewer, hub, WarpDialogState.root(WarpSortOrder.NAME).toNetwork(net1Id), all
            );
            assertThat(networkList).extracting(c -> c.aetheryte().name()).containsExactly("Plaza");

            final List<Candidate> worldList = factory.queryDestinationCandidates(
                    viewer, hub, WarpDialogState.root(WarpSortOrder.NAME).toWorld(SOURCE_WORLD_ID), all
            );
            assertThat(worldList).extracting(c -> c.aetheryte().name()).containsExactly("Outpost");

            final List<Candidate> allList = factory.queryDestinationCandidates(
                    viewer, hub, WarpDialogState.root(WarpSortOrder.NAME).toAll(), all
            );
            assertThat(allList).extracting(c -> c.aetheryte().name()).containsExactly("Outpost");

            final Component networkLabel = messages.warpNetworkCategoryLabel(viewer, "Capital Transit", 1);
            final Component networkScope = factory.resolveScopeLabel(
                    viewer, WarpDialogState.root(WarpSortOrder.NAME).toNetwork(net1Id), all
            );
            assertThat(PLAIN.serialize(networkLabel)).contains("Capital Transit").contains("(1)");
            assertThat(PLAIN.serialize(networkScope)).contains("Capital Transit");

            // Non-Hub child Aetheryte must never expose hosted network categories even with hub lookup
            final AetheryteDialogFactory.RootMenuPlan childPlan = factory.planRootMenu(viewer, childAccessible, all);
            assertThat(childPlan.networkGroups()).isEmpty();

            // Root button ordering: Hosted Network -> Favorites -> Worlds -> All Worlds -> Operations
            final List<AetheryteDialogFactory.RootButtonSpec> rootButtons = AetheryteDialogFactory.planRootButtons(hubPlan);
            assertThat(rootButtons).containsExactly(
                    new AetheryteDialogFactory.RootButtonSpec.NetworkCategory(new NetworkGroup(net1Id, "Capital Transit", 1)),
                    new AetheryteDialogFactory.RootButtonSpec.FavoritesCategory(0),
                    new AetheryteDialogFactory.RootButtonSpec.WorldCategory(hubPlan.worldGroups().get(0)),
                    new AetheryteDialogFactory.RootButtonSpec.AllWorldsCategory(1),
                    new AetheryteDialogFactory.RootButtonSpec.Operations()
            );
        } finally {
            serverField.set(null, null);
        }
    }

    @Test
    void rootButtonPlanHandlesMultipleNetworksPartialAccessBlankNameAndAllWorldsSuppression() throws Exception {
        final Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        try {
            final Server server = mock(Server.class);
            final World world = mock(World.class);
            when(world.getUID()).thenReturn(SOURCE_WORLD_ID);
            when(world.getName()).thenReturn("world");
            when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
            when(world.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(server.getWorld(SOURCE_WORLD_ID)).thenReturn(world);
            serverField.set(null, server);

            final ClickPortals plugin = mock(ClickPortals.class);
            final AetheryteRegistry registry = new AetheryteRegistry();
            final Messages messages = Messages.defaults();
            final AetheryteService service = new AetheryteService(
                    plugin,
                    registry,
                    VariationRegistry.defaults(),
                    NoopHologramService.INSTANCE,
                    messages,
                    () -> -1,
                    (p, s, d) -> WarpEvaluation.permitted(Component.text("Free"))
            );
            final AetheryteDialogFactory factory = new AetheryteDialogFactory(service, messages);

            final UUID ownerId = UUID.randomUUID();
            final UUID otherId = UUID.randomUUID();
            final UUID betaNetId = UUID.randomUUID();
            final UUID alphaNetId = UUID.randomUUID();
            final UUID blankNetId = UUID.randomUUID();

            final Aetheryte hub = new Aetheryte(
                    UUID.randomUUID(), "CentralHub", SOURCE_WORLD_ID, "world", 0, 64, 0, ownerId, 1L
            );
            final Aetheryte betaPublic = new Aetheryte(
                    UUID.randomUUID(), "BetaPublic", SOURCE_WORLD_ID, "world", 10, 64, 0, otherId, 2L,
                    Aetheryte.DEFAULT_VARIATION, "Bob", AetheryteVisibility.PUBLIC, Set.of(), betaNetId
            );
            final Aetheryte betaPrivate = new Aetheryte(
                    UUID.randomUUID(), "BetaPrivate", SOURCE_WORLD_ID, "world", 20, 64, 0, otherId, 3L,
                    Aetheryte.DEFAULT_VARIATION, "Bob", AetheryteVisibility.OWNER_ONLY, Set.of(), blankNetId
            );
            final Aetheryte alphaPublic = new Aetheryte(
                    UUID.randomUUID(), "AlphaPublic", SOURCE_WORLD_ID, "world", 30, 64, 0, ownerId, 4L,
                    Aetheryte.DEFAULT_VARIATION, "Alice", AetheryteVisibility.PUBLIC, Set.of(), alphaNetId
            );
            // Alpha & Beta networks are PUBLIC while blankNet is OWNER_ONLY for otherId,
            // so blankNet's child is excluded and normalReachableCount stays 0.
            final AetheryteNetwork betaNet = new AetheryteNetwork(
                    betaNetId, "Beta Line", SOURCE_WORLD_ID, "world", ownerId, "Alice", 1L,
                    AetheryteVisibility.PUBLIC, Set.of(), hub.id()
            );
            final AetheryteNetwork alphaNet = new AetheryteNetwork(
                    alphaNetId, "Alpha Line", SOURCE_WORLD_ID, "world", ownerId, "Alice", 2L,
                    AetheryteVisibility.PUBLIC, Set.of(), hub.id()
            );
            final AetheryteNetwork blankNet = new AetheryteNetwork(
                    blankNetId, "   ", SOURCE_WORLD_ID, "world", otherId, "Bob", 3L,
                    AetheryteVisibility.OWNER_ONLY, Set.of(), hub.id()
            );

            registry.register(world, hub);
            registry.register(world, betaPublic);
            registry.register(world, betaPrivate);
            registry.register(world, alphaPublic);
            registry.registerNetwork(world, betaNet);
            registry.registerNetwork(world, alphaNet);
            registry.registerNetwork(world, blankNet);

            final Player viewer = mock(Player.class);
            when(viewer.getUniqueId()).thenReturn(ownerId);
            when(viewer.locale()).thenReturn(Locale.US);
            when(viewer.hasPermission(Permissions.USE)).thenReturn(true);

            final List<Aetheryte> all = registry.getAll();
            final AetheryteDialogFactory.RootMenuPlan plan = factory.planRootMenu(viewer, hub, all);
            assertThat(plan.networkGroups()).containsExactly(
                    new NetworkGroup(alphaNetId, "Alpha Line", 1),
                    new NetworkGroup(betaNetId, "Beta Line", 1)
            );
            assertThat(plan.normalReachableCount()).isZero();
            assertThat(plan.totalReachableCount()).isEqualTo(2);

            final List<AetheryteDialogFactory.RootButtonSpec> buttons = AetheryteDialogFactory.planRootButtons(plan);
            assertThat(buttons).containsExactly(
                    new AetheryteDialogFactory.RootButtonSpec.NetworkCategory(new NetworkGroup(alphaNetId, "Alpha Line", 1)),
                    new AetheryteDialogFactory.RootButtonSpec.NetworkCategory(new NetworkGroup(betaNetId, "Beta Line", 1)),
                    new AetheryteDialogFactory.RootButtonSpec.FavoritesCategory(0),
                    new AetheryteDialogFactory.RootButtonSpec.Operations()
            );

            // Blank network name falls back to UUID string in scope label
            final Component blankScope = factory.resolveScopeLabel(
                    viewer, WarpDialogState.root(WarpSortOrder.NAME).toNetwork(blankNetId), all
            );
            assertThat(PLAIN.serialize(blankScope)).contains(blankNetId.toString());
        } finally {
            serverField.set(null, null);
        }
    }
}
