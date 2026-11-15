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
package net.sabafly.clickportals.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteNetwork;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.aetheryte.AetheryteVisibility;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.model.AetheryteModelService;
import net.sabafly.clickportals.permission.Permissions;
import net.sabafly.clickportals.service.AetheryteService;
import net.sabafly.clickportals.service.AetheryteService.ResolvedPlayer;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.NetworkSection;
import net.sabafly.clickportals.variation.AetheryteVariation.VariationType;
import net.sabafly.clickportals.variation.VariationRegistry;
import net.sabafly.clickportals.visual.HologramService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.permissions.Permissible;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.slf4j.LoggerFactory;

class AetheryteAdminCommandTest {

    private Plugin plugin;
    private AetheryteRegistry registry;
    private VariationRegistry variationRegistry;
    private HologramService hologramService;
    private AetheryteModelService modelService;
    private Messages messages;
    private AetheryteService service;
    private World world;
    private World secondWorld;
    private UUID worldId;
    private UUID secondWorldId;
    private UUID aliceId;
    private UUID bobId;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger("AetheryteAdminCommandTest"));
        registry = new AetheryteRegistry();

        final AetheryteVariation def = AetheryteVariation.defaultVariation();
        final AetheryteVariation networkVar = new AetheryteVariation(
                "Network",
                VariationType.NETWORK,
                def.item(),
                def.recipe(),
                def.model(),
                def.hitbox(),
                def.teleport(),
                def.placement(),
                def.limit(),
                def.warp(),
                NetworkSection.defaults(),
                def.bettermodel(),
                def.sounds()
        );
        final AetheryteVariation noHubVar = new AetheryteVariation(
                "Solo",
                VariationType.NORMAL,
                def.item(),
                def.recipe(),
                def.model(),
                def.hitbox(),
                def.teleport(),
                def.placement(),
                def.limit(),
                def.warp(),
                new NetworkSection(NetworkSection.defaults().limit(), NetworkSection.defaults().cost(), false),
                def.bettermodel(),
                def.sounds()
        );
        variationRegistry = new VariationRegistry(
                Map.of(Aetheryte.DEFAULT_VARIATION, def, "network", networkVar, "nohub", noHubVar),
                null
        );

        hologramService = mock(HologramService.class);
        modelService = mock(AetheryteModelService.class);
        messages = Messages.defaults();
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
        service.setModelService(modelService);

        aliceId = UUID.randomUUID();
        bobId = UUID.randomUUID();
        service.setPlayerByNameLookup(name -> {
            if ("Alice".equalsIgnoreCase(name)) {
                return new ResolvedPlayer(aliceId, "Alice");
            }
            if ("Bob".equalsIgnoreCase(name)) {
                return new ResolvedPlayer(bobId, "Bob");
            }
            return null;
        });
        service.setOwnerNameLookup(uuid -> {
            if (aliceId.equals(uuid)) {
                return "Alice";
            }
            if (bobId.equals(uuid)) {
                return "Bob";
            }
            return null;
        });

        worldId = UUID.randomUUID();
        world = mockWorld(worldId, "world");
        secondWorldId = UUID.randomUUID();
        secondWorld = mockWorld(secondWorldId, "world_nether");
    }

    @Test
    void permissionsAllowAetheryteCommandWhenPermissionGranted() {
        final Permissible permitted = mock(Permissible.class);
        when(permitted.hasPermission(Permissions.COMMAND_AETHERYTE)).thenReturn(true);
        assertThat(Permissions.canManageAetherytes(permitted)).isTrue();
        assertThat(Permissions.canUseCommand(permitted)).isTrue();

        final Permissible denied = mock(Permissible.class);
        assertThat(Permissions.canManageAetherytes(denied)).isFalse();
        assertThat(Permissions.canUseCommand(denied)).isFalse();
    }

    @Test
    void parseVisibilityMatchesCaseAndSeparatorsStrictly() {
        assertThat(AetheryteAdminCommand.parseVisibility("PUBLIC")).isEqualTo(AetheryteVisibility.PUBLIC);
        assertThat(AetheryteAdminCommand.parseVisibility("invite_only")).isEqualTo(AetheryteVisibility.INVITE_ONLY);
        assertThat(AetheryteAdminCommand.parseVisibility("invite-only")).isEqualTo(AetheryteVisibility.INVITE_ONLY);
        assertThat(AetheryteAdminCommand.parseVisibility("OWNER_ONLY")).isEqualTo(AetheryteVisibility.OWNER_ONLY);
        assertThat(AetheryteAdminCommand.parseVisibility("private")).isEqualTo(AetheryteVisibility.OWNER_ONLY);
        assertThat(AetheryteAdminCommand.parseVisibility("unknown")).isNull();
        assertThat(AetheryteAdminCommand.parseVisibility(" ")).isNull();
    }

    @Test
    void adminListFiltersByWorldOwnerWildcardAndPaginates() {
        final CommandSender sender = mock(CommandSender.class);
        service.adminListAetherytes(sender, null, null, 1);
        assertThat(lastPlainMessages(sender)).singleElement()
                .satisfies(msg -> assertThat(msg).contains("No matching Aetherytes found."));

        for (int i = 0; i < 12; i++) {
            final UUID id = UUID.randomUUID();
            registry.register(world, new Aetheryte(
                    id, "Over" + i, worldId, "world", i * 10, 64, 0,
                    aliceId, 100L + i, Aetheryte.DEFAULT_VARIATION, "Alice"
            ));
        }
        registry.register(secondWorld, new Aetheryte(
                UUID.randomUUID(), "NetherOne", secondWorldId, "world_nether", 0, 64, 0,
                bobId, 500L, Aetheryte.DEFAULT_VARIATION, "Bob"
        ));

        final CommandSender page2Sender = mock(CommandSender.class);
        service.adminListAetherytes(page2Sender, "*", "*", 2);
        final List<String> page2 = lastPlainMessages(page2Sender);
        assertThat(page2.getFirst()).contains("13 total", "page 2/2");
        assertThat(page2).hasSize(4); // 1 header + 3 entries

        final CommandSender filteredSender = mock(CommandSender.class);
        service.adminListAetherytes(filteredSender, "world_nether", "Bob", 1);
        final List<String> filtered = lastPlainMessages(filteredSender);
        assertThat(filtered.getFirst()).contains("1 total", "page 1/1");
        assertThat(filtered.get(1)).contains("NetherOne", "world_nether", "Bob");
    }

    @Test
    void adminGetResolvesExactUuidAndUniquePrefixAndReportsMissing() {
        final UUID id = UUID.fromString("12345678-1111-2222-3333-444444444444");
        final UUID netId = UUID.randomUUID();
        registry.registerNetwork(world, new AetheryteNetwork(
                netId, "HubNet", worldId, "world", aliceId, "Alice", 100L,
                AetheryteVisibility.INVITE_ONLY, Set.of()
        ));
        registry.register(world, new Aetheryte(
                id, "Hub", worldId, "world", 10, 64, 20, aliceId, 100L,
                "network", "Alice", AetheryteVisibility.INVITE_ONLY, Set.of(), netId
        ));

        final CommandSender sender = mock(CommandSender.class);
        service.adminGetAetheryte(sender, "12345678");
        final String output = String.join("\n", lastPlainMessages(sender));
        assertThat(output).contains(id.toString(), "Hub", "network", "Alice", "Invite Only", netId.toString());

        final CommandSender missingSender = mock(CommandSender.class);
        service.adminGetAetheryte(missingSender, "ffffffff");
        assertThat(lastPlainMessages(missingSender).getFirst()).contains("not found");
    }

    @Test
    void adminPlaceAndRemoveWorkForNormalAndNetworkAndRejectDuplicates() throws Exception {
        withMockServer(() -> {
            final CommandSender sender = mock(CommandSender.class);
            service.adminPlaceAetheryte(
                    sender, "default", world, 10, 64, 20, "Spawn", "Alice",
                    AetheryteVisibility.OWNER_ONLY, null
            );
            assertThat(registry.getAll()).hasSize(1);
            final Aetheryte normal = registry.getByPosition(world, 10, 64, 20).orElseThrow();
            assertThat(normal.name()).isEqualTo("Spawn");
            assertThat(normal.ownerId()).isEqualTo(aliceId);
            assertThat(normal.visibility()).isEqualTo(AetheryteVisibility.OWNER_ONLY);
            assertThat(normal.networkId()).isNull();

            // Duplicate position rejected
            service.adminPlaceAetheryte(sender, "default", world, 10, 64, 20, "Dup", "Alice", null, null);
            assertThat(registry.getAll()).hasSize(1);

            // Unknown variation rejected
            service.adminPlaceAetheryte(sender, "does_not_exist", world, 30, 64, 40, "X", "Alice", null, null);
            assertThat(registry.getAll()).hasSize(1);

            // Standalone network placement without Hub network rejected
            service.adminPlaceAetheryte(
                    sender, "network", world, 50, 64, 60, "OrphanNet", "Bob",
                    AetheryteVisibility.INVITE_ONLY, null
            );
            assertThat(registry.getByPosition(world, 50, 64, 60)).isEmpty();
            assertThat(registry.getAllNetworks()).isEmpty();

            // Create Hub-backed network via adminCreateNetwork, then place child onto it
            service.adminCreateNetwork(sender, normal.id().toString(), "SpawnNet", "Alice", AetheryteVisibility.INVITE_ONLY);
            final AetheryteNetwork hubNet = registry.getNetworksByHub(normal.id()).getFirst();

            service.adminPlaceAetheryte(
                    sender, "network", world, 50, 64, 60, "NetOne", "Bob",
                    AetheryteVisibility.PUBLIC, hubNet.id().toString()
            );
            final Aetheryte netPlaced = registry.getByPosition(world, 50, 64, 60).orElseThrow();
            assertThat(netPlaced.networkId()).isEqualTo(hubNet.id());
            // Inherited visibility from Hub network
            assertThat(netPlaced.visibility()).isEqualTo(AetheryteVisibility.INVITE_ONLY);

            // Cross-world network placement rejected cleanly
            service.adminPlaceAetheryte(
                    sender, "network", secondWorld, 50, 64, 60, "BadCross", "Bob",
                    AetheryteVisibility.PUBLIC, hubNet.id().toString()
            );
            assertThat(registry.getByPosition(secondWorld, 50, 64, 60)).isEmpty();

            // Non-cascade Hub remove refuses while children exist
            service.adminRemoveAetheryte(sender, normal.id().toString(), false);
            assertThat(registry.getById(normal.id())).isPresent();
            assertThat(registry.getById(netPlaced.id())).isPresent();

            // Cascade Hub remove cleans up Hub, network, and all children
            service.adminRemoveAetheryte(sender, normal.id().toString(), true);
            assertThat(registry.getById(normal.id())).isEmpty();
            assertThat(registry.getById(netPlaced.id())).isEmpty();
            assertThat(registry.getAllNetworks()).isEmpty();
            verify(modelService, atLeastOnce()).unregisterAetheryte(normal.id());
            verify(modelService, atLeastOnce()).unregisterAetheryte(netPlaced.id());
            verify(hologramService, atLeastOnce()).hideAetheryte(netPlaced.id());
        });
    }

    @Test
    void adminSetSubcommandsEnforceHubModelAndCrossWorldGuards() throws Exception {
        withMockServer(() -> {
            final CommandSender sender = mock(CommandSender.class);
            final Aetheryte hub = new Aetheryte(
                    UUID.randomUUID(), "OldName", worldId, "world", 5, 64, 5,
                    aliceId, 100L, Aetheryte.DEFAULT_VARIATION, "Alice"
            );
            registry.register(world, hub);
            final String hubIdStr = hub.id().toString();

            service.adminSetName(sender, hubIdStr, "New Name");
            assertThat(registry.getById(hub.id())).get().extracting(Aetheryte::name).isEqualTo("New Name");

            service.adminSetOwner(sender, hubIdStr, "Bob");
            assertThat(registry.getById(hub.id())).get().extracting(Aetheryte::ownerId).isEqualTo(bobId);

            // Standalone normal -> network transition refused without Hub network
            service.adminSetVariation(sender, hubIdStr, "network");
            assertThat(registry.getById(hub.id()).orElseThrow().variation()).isEqualTo("default");

            // Calling set network on normal variation rejects gracefully
            final CommandSender errSender = mock(CommandSender.class);
            service.adminSetNetwork(errSender, hubIdStr, "new");
            assertThat(lastPlainMessages(errSender).getFirst()).contains("does not support networks");

            // Host a network on the Hub and attach a child Aetheryte
            service.adminCreateNetwork(sender, hubIdStr, "MainNet", "Bob", AetheryteVisibility.PUBLIC);
            final AetheryteNetwork net = registry.getNetworksByHub(hub.id()).getFirst();
            service.adminPlaceAetheryte(
                    sender, "network", world, 30, 64, 30, "ChildOne", "Alice",
                    AetheryteVisibility.PUBLIC, net.id().toString()
            );
            final Aetheryte child = registry.getByPosition(world, 30, 64, 30).orElseThrow();
            final String childIdStr = child.id().toString();

            // Hub hosting networks cannot switch to allow-hub: false or network variation
            service.adminSetVariation(sender, hubIdStr, "nohub");
            assertThat(registry.getById(hub.id()).orElseThrow().variation()).isEqualTo("default");
            service.adminSetVariation(sender, hubIdStr, "network");
            assertThat(registry.getById(hub.id()).orElseThrow().variation()).isEqualTo("default");

            // Hub and child cannot move across worlds while bound to a world-scoped network
            service.adminSetLocation(sender, hubIdStr, secondWorld, 10, 70, 10);
            assertThat(registry.getById(hub.id()).orElseThrow().worldId()).isEqualTo(worldId);
            service.adminSetLocation(sender, childIdStr, secondWorld, 20, 70, 20);
            assertThat(registry.getById(child.id()).orElseThrow().worldId()).isEqualTo(worldId);

            // Set visibility updates owning network
            service.adminSetVisibility(sender, childIdStr, AetheryteVisibility.OWNER_ONLY);
            assertThat(registry.getNetworkById(net.id())).get()
                    .extracting(AetheryteNetwork::visibility)
                    .isEqualTo(AetheryteVisibility.OWNER_ONLY);

            // "new" is rejected by adminSetNetwork under Hub model
            service.adminSetNetwork(sender, childIdStr, "new");
            assertThat(registry.getById(child.id()).orElseThrow().networkId()).isEqualTo(net.id());

            // Detach with "none" leaves Hub-hosted network intact; re-attach works
            service.adminSetNetwork(sender, childIdStr, "none");
            assertThat(registry.getById(child.id()).orElseThrow().networkId()).isNull();
            assertThat(registry.getNetworkById(net.id())).isPresent();
            service.adminSetNetwork(sender, childIdStr, net.id().toString());
            assertThat(registry.getById(child.id()).orElseThrow().networkId()).isEqualTo(net.id());

            // Transition child network -> normal detaches network without deleting Hub network
            service.adminSetVariation(sender, childIdStr, "default");
            final Aetheryte backToNormal = registry.getById(child.id()).orElseThrow();
            assertThat(backToNormal.variation()).isEqualTo("default");
            assertThat(backToNormal.networkId()).isNull();
            assertThat(registry.getNetworkById(net.id())).isPresent();

            // Unlinked normal Aetheryte CAN move across worlds cleanly
            service.adminSetLocation(sender, childIdStr, secondWorld, 100, 70, 200);
            final Aetheryte moved = registry.getById(child.id()).orElseThrow();
            assertThat(moved.worldId()).isEqualTo(secondWorldId);
            assertThat(moved.worldName()).isEqualTo("world_nether");
            assertThat(moved.blockX()).isEqualTo(100);
            assertThat(moved.blockY()).isEqualTo(70);
            assertThat(moved.blockZ()).isEqualTo(200);
            assertThat(registry.getByWorld(secondWorldId)).containsExactly(moved);
            assertThat(secondWorld.getPersistentDataContainer().has(AetheryteRegistry.WORLD_AETHERYTES_KEY)).isTrue();
        });
    }

    @Test
    void adminNetworkSubcommandsManageLifecycleHubAssignmentInvitesAndCascadeDelete() throws Exception {
        withMockServer(() -> {
            final CommandSender sender = mock(CommandSender.class);
            final Aetheryte hub1 = new Aetheryte(
                    UUID.randomUUID(), "HubAlpha", worldId, "world", 0, 64, 0,
                    aliceId, 100L, Aetheryte.DEFAULT_VARIATION, "Alice"
            );
            final Aetheryte hub2 = new Aetheryte(
                    UUID.randomUUID(), "HubBeta", worldId, "world", 40, 64, 0,
                    bobId, 110L, Aetheryte.DEFAULT_VARIATION, "Bob"
            );
            final Aetheryte noHub = new Aetheryte(
                    UUID.randomUUID(), "NoHub", worldId, "world", 80, 64, 0,
                    aliceId, 120L, "nohub", "Alice"
            );
            final Aetheryte netherHub = new Aetheryte(
                    UUID.randomUUID(), "NetherHub", secondWorldId, "world_nether", 0, 64, 0,
                    aliceId, 130L, Aetheryte.DEFAULT_VARIATION, "Alice"
            );
            registry.register(world, hub1);
            registry.register(world, hub2);
            registry.register(world, noHub);
            registry.register(secondWorld, netherHub);

            // Reject non-hub variation
            service.adminCreateNetwork(sender, noHub.id().toString(), "Rejected", "Alice", null);
            assertThat(registry.getAllNetworks()).isEmpty();

            // Create network on hub1
            service.adminCreateNetwork(sender, hub1.id().toString(), "AlphaNet", "Alice", AetheryteVisibility.INVITE_ONLY);
            assertThat(registry.getAllNetworks()).hasSize(1);
            final AetheryteNetwork created = registry.getAllNetworks().getFirst();
            final String netPrefix = created.id().toString().substring(0, 8);

            // Place child into AlphaNet
            service.adminPlaceAetheryte(
                    sender, "network", world, 15, 64, 15, "Outpost", "Bob", null, netPrefix);
            final Aetheryte outpost = registry.getByPosition(world, 15, 64, 15).orElseThrow();

            // List & Get
            final CommandSender listSender = mock(CommandSender.class);
            service.adminListNetworks(listSender, "world", "Alice", 1);
            final List<String> listMsgs = lastPlainMessages(listSender);
            assertThat(listMsgs.getFirst()).contains("1 total");
            assertThat(listMsgs.get(1)).contains("AlphaNet", "HubAlpha", "1");

            service.adminUpdateNetworkInvite(sender, netPrefix, "Bob", true);
            service.adminUpdateNetworkInvite(sender, netPrefix, "Alice", true); // self-invite ignored

            final CommandSender getSender = mock(CommandSender.class);
            service.adminGetNetwork(getSender, netPrefix);
            final String getOut = String.join("\n", lastPlainMessages(getSender));
            assertThat(getOut).contains("AlphaNet", "HubAlpha", "Bob", "Alice", "Invite Only");

            // Remove invite
            service.adminUpdateNetworkInvite(sender, netPrefix, "Bob", false);
            assertThat(registry.getNetworkById(created.id()).orElseThrow().invitedPlayers()).isEmpty();

            // Rename, set owner, set visibility, set hub
            service.adminRenameNetwork(sender, netPrefix, "Renamed Net");
            service.adminSetNetworkOwner(sender, netPrefix, "Bob");
            service.adminSetNetworkVisibility(sender, netPrefix, AetheryteVisibility.OWNER_ONLY);
            service.adminSetNetworkHub(sender, netPrefix, netherHub.id().toString()); // cross-world hub rejected
            assertThat(registry.getNetworkById(created.id()).orElseThrow().hubAetheryteId()).isEqualTo(hub1.id());
            service.adminSetNetworkHub(sender, netPrefix, hub2.id().toString());

            final AetheryteNetwork updated = registry.getNetworkById(created.id()).orElseThrow();
            assertThat(updated.name()).isEqualTo("Renamed Net");
            assertThat(updated.ownerId()).isEqualTo(bobId);
            assertThat(updated.visibility()).isEqualTo(AetheryteVisibility.OWNER_ONLY);
            assertThat(updated.hubAetheryteId()).isEqualTo(hub2.id());

            // Non-cascade delete blocked while child exists
            service.adminDeleteNetwork(sender, netPrefix, false);
            assertThat(registry.getNetworkById(created.id())).isPresent();
            assertThat(registry.getById(outpost.id())).isPresent();

            // Cascade delete removes child + network cleanly while preserving Hub
            service.adminDeleteNetwork(sender, netPrefix, true);
            assertThat(registry.getNetworkById(created.id())).isEmpty();
            assertThat(registry.getById(outpost.id())).isEmpty();
            assertThat(registry.getById(hub2.id())).isPresent();
        });
    }

    private static List<String> lastPlainMessages(final CommandSender sender) {
        final ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(sender, atLeastOnce()).sendMessage(captor.capture());
        final List<String> out = new ArrayList<>();
        for (final Component comp : captor.getAllValues()) {
            out.add(PlainTextComponentSerializer.plainText().serialize(comp));
        }
        return out;
    }

    private World mockWorld(final UUID id, final String name) {
        final World w = mock(World.class);
        final PersistentDataContainer pdc = createInMemoryPdc();
        when(w.getUID()).thenReturn(id);
        when(w.getName()).thenReturn(name);
        when(w.getPersistentDataContainer()).thenReturn(pdc);
        final Chunk chunk = mock(Chunk.class);
        when(chunk.getEntities()).thenReturn(new Entity[0]);
        when(w.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);
        return w;
    }

    private void withMockServer(final Runnable action) throws Exception {
        final Server server = mock(Server.class);
        when(server.getWorld(worldId)).thenReturn(world);
        when(server.getWorld("world")).thenReturn(world);
        when(server.getWorld(secondWorldId)).thenReturn(secondWorld);
        when(server.getWorld("world_nether")).thenReturn(secondWorld);
        when(server.getWorlds()).thenReturn(List.of(world, secondWorld));
        when(server.getOnlinePlayers()).thenReturn(List.of());
        final RegionScheduler regionScheduler = mock(RegionScheduler.class);
        when(server.getRegionScheduler()).thenReturn(regionScheduler);
        doAnswer(inv -> {
            final Runnable task = inv.getArgument(2);
            task.run();
            return null;
        }).when(regionScheduler).execute(any(), any(Location.class), any());
        doAnswer(inv -> {
            final Runnable task = inv.getArgument(4);
            task.run();
            return null;
        }).when(regionScheduler).execute(any(), any(World.class), anyInt(), anyInt(), any());

        final Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        final Object previous = serverField.get(null);
        serverField.set(null, server);
        try {
            action.run();
        } finally {
            serverField.set(null, previous);
        }
    }

    private static PersistentDataContainer createInMemoryPdc() {
        final Map<NamespacedKey, Object> map = new HashMap<>();
        final PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(any(NamespacedKey.class), ArgumentMatchers.<PersistentDataType<Object, Object>>any()))
                .thenAnswer(inv -> map.containsKey(inv.getArgument(0)));
        when(pdc.has(any(NamespacedKey.class)))
                .thenAnswer(inv -> map.containsKey(inv.getArgument(0)));
        when(pdc.get(any(NamespacedKey.class), ArgumentMatchers.<PersistentDataType<Object, Object>>any()))
                .thenAnswer(inv -> {
                    final NamespacedKey key = inv.getArgument(0);
                    final PersistentDataType<?, ?> type = inv.getArgument(1);
                    final Object value = map.get(key);
                    if (value == null) {
                        return null;
                    }
                    return type.getComplexType().isInstance(value) ? type.getComplexType().cast(value) : null;
                });
        doAnswer(inv -> {
            map.put(inv.getArgument(0), inv.getArgument(2));
            return null;
        }).when(pdc).set(any(NamespacedKey.class), ArgumentMatchers.<PersistentDataType<Object, Object>>any(), any());
        doAnswer(inv -> {
            map.remove(inv.getArgument(0));
            return null;
        }).when(pdc).remove(any(NamespacedKey.class));
        return pdc;
    }
}
