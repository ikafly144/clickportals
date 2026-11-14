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
import java.util.Objects;
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
        variationRegistry = new VariationRegistry(
                Map.of(Aetheryte.DEFAULT_VARIATION, def, "network", networkVar),
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

            // Network placement creates network automatically
            service.adminPlaceAetheryte(
                    sender, "network", world, 50, 64, 60, "NetOne", "Bob",
                    AetheryteVisibility.INVITE_ONLY, null
            );
            final Aetheryte netPlaced = registry.getByPosition(world, 50, 64, 60).orElseThrow();
            final UUID placedNetId = Objects.requireNonNull(netPlaced.networkId());
            assertThat(registry.getNetworkById(placedNetId)).isPresent();

            // Admin remove deletes without item drop and cleans up empty network
            service.adminRemoveAetheryte(sender, netPlaced.id().toString());
            assertThat(registry.getById(netPlaced.id())).isEmpty();
            assertThat(registry.getAllNetworks()).isEmpty();
            verify(modelService, atLeastOnce()).unregisterAetheryte(netPlaced.id());
            verify(hologramService, atLeastOnce()).hideAetheryte(netPlaced.id());
        });
    }

    @Test
    void adminSetSubcommandsUpdatePropertiesAndMoveAcrossWorlds() throws Exception {
        withMockServer(() -> {
            final CommandSender sender = mock(CommandSender.class);
            final Aetheryte initial = new Aetheryte(
                    UUID.randomUUID(), "OldName", worldId, "world", 5, 64, 5,
                    aliceId, 100L, Aetheryte.DEFAULT_VARIATION, "Alice"
            );
            registry.register(world, initial);
            final String idStr = initial.id().toString();

            service.adminSetName(sender, idStr, "New Name");
            assertThat(registry.getById(initial.id())).get().extracting(Aetheryte::name).isEqualTo("New Name");

            service.adminSetOwner(sender, idStr, "Bob");
            assertThat(registry.getById(initial.id())).get().extracting(Aetheryte::ownerId).isEqualTo(bobId);

            // Transition normal -> network creates network
            service.adminSetVariation(sender, idStr, "network");
            final Aetheryte asNetwork = registry.getById(initial.id()).orElseThrow();
            assertThat(asNetwork.variation()).isEqualTo("network");
            assertThat(asNetwork.networkId()).isNotNull();
            final UUID createdNetId = Objects.requireNonNull(asNetwork.networkId());
            assertThat(registry.getNetworkById(createdNetId)).isPresent();

            // Set visibility updates the owning network
            service.adminSetVisibility(sender, idStr, AetheryteVisibility.OWNER_ONLY);
            assertThat(registry.getNetworkById(createdNetId)).get()
                    .extracting(AetheryteNetwork::visibility)
                    .isEqualTo(AetheryteVisibility.OWNER_ONLY);

            // Detach network with "none" and clean up empty network
            service.adminSetNetwork(sender, idStr, "none");
            assertThat(registry.getById(initial.id()).orElseThrow().networkId()).isNull();
            assertThat(registry.getNetworkById(createdNetId)).isEmpty();

            // Re-attach to a new network
            service.adminSetNetwork(sender, idStr, "new");
            final UUID secondNetId = Objects.requireNonNull(registry.getById(initial.id()).orElseThrow().networkId());
            assertThat(secondNetId).isNotNull();

            // Cross-world location move saves both worlds and recreates network in destination world
            service.adminSetLocation(sender, idStr, secondWorld, 100, 70, 200);
            final Aetheryte moved = registry.getById(initial.id()).orElseThrow();
            assertThat(moved.worldId()).isEqualTo(secondWorldId);
            assertThat(moved.worldName()).isEqualTo("world_nether");
            assertThat(moved.blockX()).isEqualTo(100);
            assertThat(moved.blockY()).isEqualTo(70);
            assertThat(moved.blockZ()).isEqualTo(200);
            assertThat(moved.networkId()).isNotNull().isNotEqualTo(secondNetId);
            assertThat(registry.getNetworkById(secondNetId)).isEmpty();
            assertThat(registry.getByWorld(worldId)).isEmpty();
            assertThat(registry.getByWorld(secondWorldId)).containsExactly(moved);
            assertThat(world.getPersistentDataContainer().has(AetheryteRegistry.WORLD_AETHERYTES_KEY)).isFalse();
            assertThat(secondWorld.getPersistentDataContainer().has(AetheryteRegistry.WORLD_AETHERYTES_KEY)).isTrue();

            // Transition network -> normal detaches network
            final UUID netherNetId = Objects.requireNonNull(moved.networkId());
            service.adminSetVariation(sender, idStr, "default");
            final Aetheryte backToNormal = registry.getById(initial.id()).orElseThrow();
            assertThat(backToNormal.variation()).isEqualTo("default");
            assertThat(backToNormal.networkId()).isNull();
            assertThat(registry.getNetworkById(netherNetId)).isEmpty();

            // Calling set network on normal variation rejects gracefully
            final CommandSender errSender = mock(CommandSender.class);
            service.adminSetNetwork(errSender, idStr, "new");
            assertThat(lastPlainMessages(errSender).getFirst()).contains("does not support networks");
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
