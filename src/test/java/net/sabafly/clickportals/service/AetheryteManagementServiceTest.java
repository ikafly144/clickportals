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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.dialog.WarpSortOrder;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.model.AetheryteModelService;
import net.sabafly.clickportals.permission.Permissions;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.VariationRegistry;
import net.sabafly.clickportals.visual.HologramService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class AetheryteManagementServiceTest {

    private Plugin plugin;
    private AetheryteRegistry registry;
    private VariationRegistry variationRegistry;
    private HologramService hologramService;
    private AetheryteModelService modelService;
    private Messages messages;
    private AetheryteService service;
    private World world;
    private UUID worldId;
    private UUID ownerId;
    private UUID otherPlayerId;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        when(plugin.getSLF4JLogger()).thenReturn(org.slf4j.LoggerFactory.getLogger("AetheryteManagementServiceTest"));
        registry = new AetheryteRegistry();
        variationRegistry = mock(VariationRegistry.class);
        when(variationRegistry.getOrDefault(any())).thenReturn(AetheryteVariation.defaultVariation());
        when(variationRegistry.find(Aetheryte.DEFAULT_VARIATION)).thenReturn(Optional.of(AetheryteVariation.defaultVariation()));
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

        worldId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        otherPlayerId = UUID.randomUUID();
        world = mock(World.class);
        final PersistentDataContainer worldPdc = createInMemoryPdc();
        when(world.getUID()).thenReturn(worldId);
        when(world.getName()).thenReturn("world");
        when(world.getPersistentDataContainer()).thenReturn(worldPdc);
        final Chunk chunk = mock(Chunk.class);
        when(chunk.getEntities()).thenReturn(new Entity[0]);
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);
        final Block block = mock(Block.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
    }

    @Test
    void resolveOwnerNameUsesCachedValueLookupAndPerRenderCache() {
        final Aetheryte withStoredName = createAetheryte("Stored", ownerId, "Alice");
        final Player viewer = mockPlayer(otherPlayerId, "Bob");
        final Map<UUID, String> renderCache = new HashMap<>();

        assertThat(service.resolveOwnerName(withStoredName, viewer, renderCache)).isEqualTo("Alice");
        assertThat(renderCache).containsEntry(ownerId, "Alice");

        final UUID lookupId = UUID.randomUUID();
        final AtomicInteger lookups = new AtomicInteger(0);
        service.setOwnerNameLookup(id -> {
            lookups.incrementAndGet();
            return id.equals(lookupId) ? "Charlie" : null;
        });

        final Aetheryte legacyWithoutName = createAetheryte("Legacy", lookupId, null);
        final Map<UUID, String> cache2 = new HashMap<>();
        assertThat(service.resolveOwnerName(legacyWithoutName, viewer, cache2)).isEqualTo("Charlie");
        assertThat(service.resolveOwnerName(legacyWithoutName, viewer, cache2)).isEqualTo("Charlie");
        assertThat(lookups.get()).isEqualTo(1);

        final Aetheryte unknownOwner = createAetheryte("Mystery", UUID.randomUUID(), null);
        final String unknownName = service.resolveOwnerName(unknownOwner, viewer, null);
        assertThat(unknownName).isNotBlank();
    }

    @Test
    void favoritesPersistInPlayerPdcHandleMalformedEntriesAndClearWhenEmpty() {
        final Map<NamespacedKey, Object> backing = new HashMap<>();
        final Player player = mockPlayer(ownerId, "Alice", createInMemoryPdc(backing));
        final UUID id1 = UUID.randomUUID();
        final UUID id2 = UUID.randomUUID();

        backing.put(AetheryteService.FAVORITES_KEY, List.of("not-a-uuid", id1.toString()));
        assertThat(service.getFavorites(player)).containsExactly(id1);
        assertThat(service.isFavorite(player, id1)).isTrue();
        assertThat(service.isFavorite(player, id2)).isFalse();

        assertThat(service.toggleFavorite(player, id2)).isTrue();
        assertThat(service.getFavorites(player)).containsExactlyInAnyOrder(id1, id2);

        assertThat(service.toggleFavorite(player, id1)).isFalse();
        assertThat(service.toggleFavorite(player, id2)).isFalse();
        assertThat(service.getFavorites(player)).isEmpty();
        assertThat(backing).doesNotContainKey(AetheryteService.FAVORITES_KEY);
    }

    @Test
    void toggleFavoriteEnforcesConfiguredLimitWhileAllowingRemovals() {
        final Map<NamespacedKey, Object> backing = new HashMap<>();
        final Player player = mockPlayer(ownerId, "Alice", createInMemoryPdc(backing));
        final UUID id1 = UUID.randomUUID();
        final UUID id2 = UUID.randomUUID();
        final UUID id3 = UUID.randomUUID();

        service.setMaxFavoritesPerPlayerSupplier(() -> 2);

        assertThat(service.toggleFavorite(player, id1)).isTrue();
        assertThat(service.toggleFavorite(player, id2)).isTrue();
        assertThat(service.toggleFavorite(player, id3)).isFalse();
        assertThat(service.getFavorites(player)).containsExactlyInAnyOrder(id1, id2);
        final ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, times(1)).sendMessage(captor.capture());
        assertThat(PlainTextComponentSerializer.plainText().serialize(captor.getValue()))
                .contains("(2)");

        // Removing an existing favorite still succeeds when at the limit
        assertThat(service.toggleFavorite(player, id1)).isFalse();
        assertThat(service.getFavorites(player)).containsExactlyInAnyOrder(id2);

        // Once below the limit, adding succeeds again
        assertThat(service.toggleFavorite(player, id3)).isTrue();
        assertThat(service.getFavorites(player)).containsExactlyInAnyOrder(id2, id3);

        // Setting limit to 0 blocks new additions while existing ones can still be removed
        service.setMaxFavoritesPerPlayerSupplier(() -> 0);
        assertThat(service.toggleFavorite(player, id1)).isFalse();
        verify(player, times(2)).sendMessage(captor.capture());
        assertThat(PlainTextComponentSerializer.plainText().serialize(captor.getValue()))
                .contains("(0)");
        assertThat(service.toggleFavorite(player, id2)).isFalse();
        assertThat(service.getFavorites(player)).containsExactlyInAnyOrder(id3);

        // Setting limit back to -1 allows adding unlimited favorites again
        service.setMaxFavoritesPerPlayerSupplier(() -> -1);
        assertThat(service.toggleFavorite(player, id1)).isTrue();
        assertThat(service.getFavorites(player)).containsExactlyInAnyOrder(id1, id3);
    }

    @Test
    void warpSortOrderPersistsInPlayerPdcAndDefaultsToDistance() {
        final Player player = mockPlayer(ownerId, "Alice");
        assertThat(service.getWarpSortOrder(player)).isEqualTo(WarpSortOrder.DISTANCE);

        service.setWarpSortOrder(player, WarpSortOrder.OWNER);
        assertThat(service.getWarpSortOrder(player)).isEqualTo(WarpSortOrder.OWNER);
    }

    @Test
    void renameAetheryteChecksPermissionsValidatesNameUpdatesRegistryAndRefreshesHologram() throws Exception {
        final Aetheryte target = createAetheryte("OldName", ownerId, "Alice");
        registry.register(world, target);

        final Player unauthorized = mockPlayer(otherPlayerId, "Bob");
        final Player owner = mockPlayer(ownerId, "Alice");
        when(owner.hasPermission(Permissions.PLACE)).thenReturn(true);
        final Player renameAdmin = mockPlayer(otherPlayerId, "Mod");
        when(renameAdmin.hasPermission(Permissions.RENAME_ANY)).thenReturn(true);

        withMockServer(() -> {
            service.renameAetheryte(unauthorized, target.id(), "Hacked");
            assertThat(registry.getById(target.id())).get().extracting(Aetheryte::name).isEqualTo("OldName");

            service.renameAetheryte(owner, target.id(), "   ");
            assertThat(registry.getById(target.id())).get().extracting(Aetheryte::name).isEqualTo("OldName");

            service.renameAetheryte(owner, target.id(), "  Home  ");
            final Aetheryte afterOwnerRename = registry.getById(target.id()).orElseThrow();
            assertThat(afterOwnerRename.name()).isEqualTo("Home");
            verify(hologramService, atLeastOnce()).hideAetheryte(target.id());
            verify(hologramService, atLeastOnce()).showAetheryte(afterOwnerRename);

            service.renameAetheryte(renameAdmin, target.id(), "AdminRenamed");
            assertThat(registry.getById(target.id())).get().extracting(Aetheryte::name).isEqualTo("AdminRenamed");
        });
    }

    @Test
    void forceRemoveWithoutRefundRequiresAdminAndSkipsItemDrop() throws Exception {
        final Aetheryte target = createAetheryte("ToForceRemove", ownerId, "Alice");
        registry.register(world, target);

        final Player owner = mockPlayer(ownerId, "Alice");
        when(owner.hasPermission(Permissions.PLACE)).thenReturn(true);

        withMockServer(() -> {
            // Non-admin owner cannot force-remove (refundItem=false)
            service.removeAetheryte(owner, target.id(), false);
            assertThat(registry.getById(target.id())).isPresent();

            // Admin can force-remove without refunding an item
            final Player admin = mockPlayer(otherPlayerId, "Admin");
            when(admin.hasPermission(Permissions.REMOVE_ANY)).thenReturn(true);
            when(admin.hasPermission(Permissions.ADMIN)).thenReturn(true);

            service.removeAetheryte(admin, target.id(), false);
            assertThat(registry.getById(target.id())).isEmpty();
            verify(world, never()).dropItemNaturally(any(), any());
            verify(hologramService).hideAetheryte(target.id());
            verify(modelService).unregisterAetheryte(target.id());
        });
    }

    @Test
    void transferOwnershipUpdatesOwnerIdAndNameForAdminOnly() throws Exception {
        final Aetheryte target = createAetheryte("SharedHub", ownerId, "Alice");
        registry.register(world, target);

        final UUID newOwnerId = UUID.randomUUID();
        service.setPlayerByNameLookup(name -> "NewOwner".equalsIgnoreCase(name)
                ? new AetheryteService.ResolvedPlayer(newOwnerId, "NewOwner")
                : null);

        final Player nonAdminOwner = mockPlayer(ownerId, "Alice");
        final Player admin = mockPlayer(otherPlayerId, "Admin");
        when(admin.hasPermission(Permissions.ADMIN)).thenReturn(true);

        withMockServer(() -> {
            service.transferOwnership(nonAdminOwner, target.id(), "NewOwner");
            assertThat(registry.getById(target.id())).get().extracting(Aetheryte::ownerId).isEqualTo(ownerId);

            service.transferOwnership(admin, target.id(), "Nobody");
            assertThat(registry.getById(target.id())).get().extracting(Aetheryte::ownerId).isEqualTo(ownerId);

            service.transferOwnership(admin, target.id(), "newowner");
            final Aetheryte updated = registry.getById(target.id()).orElseThrow();
            assertThat(updated.ownerId()).isEqualTo(newOwnerId);
            assertThat(updated.ownerName()).isEqualTo("NewOwner");
            verify(admin, atLeastOnce()).sendMessage(any(Component.class));
        });
    }

    @Test
    void changeVariationAndResyncVisualsValidateAndRefreshVisuals() throws Exception {
        final Aetheryte target = createAetheryte("Crystal", ownerId, "Alice");
        registry.register(world, target);

        final AetheryteVariation customVar = AetheryteVariation.defaultVariation();
        when(variationRegistry.find("crystal_blue")).thenReturn(Optional.of(customVar));
        when(variationRegistry.find("missing_var")).thenReturn(Optional.empty());
        when(variationRegistry.getOrDefault("crystal_blue")).thenReturn(customVar);

        final Player nonAdmin = mockPlayer(ownerId, "Alice");
        final Player admin = mockPlayer(otherPlayerId, "Admin");
        when(admin.hasPermission(Permissions.ADMIN)).thenReturn(true);

        withMockServer(() -> {
            service.changeVariation(nonAdmin, target.id(), "crystal_blue");
            assertThat(registry.getById(target.id())).get()
                    .extracting(Aetheryte::variation)
                    .isEqualTo(Aetheryte.DEFAULT_VARIATION);

            service.changeVariation(admin, target.id(), "missing_var");
            assertThat(registry.getById(target.id())).get()
                    .extracting(Aetheryte::variation)
                    .isEqualTo(Aetheryte.DEFAULT_VARIATION);

            service.changeVariation(admin, target.id(), "crystal_blue");
            final Aetheryte updated = registry.getById(target.id()).orElseThrow();
            assertThat(updated.variation()).isEqualTo("crystal_blue");
            verify(modelService).unregisterAetheryte(target.id());
            verify(modelService).registerAetheryte(updated, customVar, world);
            verify(hologramService).hideAetheryte(target.id());
            verify(hologramService).showAetheryte(updated);

            service.resyncVisuals(admin, target.id());
            final ArgumentCaptor<Aetheryte> syncedCaptor = ArgumentCaptor.forClass(Aetheryte.class);
            verify(hologramService, times(2)).showAetheryte(syncedCaptor.capture());
            assertThat(syncedCaptor.getAllValues().getLast()).isEqualTo(updated);
            verify(modelService, times(2)).unregisterAetheryte(target.id());
        });
    }

    @Test
    void permissionHelpersMatchOwnershipAndAdminNodes() {
        final Aetheryte owned = createAetheryte("Owned", ownerId, "Alice");
        final Player owner = mockPlayer(ownerId, "Alice");
        assertThat(AetheryteService.canRename(owner, owned)).isTrue();

        final Player stranger = mockPlayer(otherPlayerId, "Bob");
        assertThat(AetheryteService.canRename(stranger, owned)).isFalse();

        when(stranger.hasPermission(Permissions.RENAME_ANY)).thenReturn(true);
        assertThat(AetheryteService.canRename(stranger, owned)).isTrue();

        assertThat(AetheryteService.canAdmin(owner)).isFalse();
        when(stranger.hasPermission(Permissions.ADMIN)).thenReturn(true);
        assertThat(AetheryteService.canAdmin(stranger)).isTrue();
    }

    @Test
    void refreshOwnerNameIfNeededSkipsUpdateWhenOwnershipTransferredBeforeRegionCallback() throws Exception {
        final Aetheryte target = createAetheryte("Hub", ownerId, "OldAlice");
        registry.register(world, target);

        final Player renamedOldOwner = mockPlayer(ownerId, "NewAlice");
        final Queue<Runnable> regionTasks = new ArrayDeque<>();

        withMockServer(regionTasks, () -> {
            final Aetheryte preview = service.refreshOwnerNameIfNeeded(renamedOldOwner, target);
            assertThat(preview.ownerName()).isEqualTo("NewAlice");
            assertThat(regionTasks).hasSize(1);

            // Simulate transferOwnership winning before the region callback executes
            registry.update(world, target.id(), a -> a.withOwner(otherPlayerId, "NewOwner"));

            regionTasks.remove().run();

            final Aetheryte current = registry.getById(target.id()).orElseThrow();
            assertThat(current.ownerId()).isEqualTo(otherPlayerId);
            assertThat(current.ownerName()).isEqualTo("NewOwner");
        });
    }

    @Test
    void removeAetheryteRechecksOwnershipAndRefundsRemovedVariationWhenModifiedBeforeRegionCallback() throws Exception {
        final Aetheryte target = createAetheryte("ToRemove", ownerId, "Alice");
        registry.register(world, target);

        final Player originalOwner = mockPlayer(ownerId, "Alice");
        when(originalOwner.hasPermission(Permissions.PLACE)).thenReturn(true);
        when(originalOwner.getGameMode()).thenReturn(GameMode.SURVIVAL);

        final Queue<Runnable> regionTasks = new ArrayDeque<>();
        withMockServer(regionTasks, () -> {
            // 1. Old owner queues removal, but ownership transfers before region callback executes
            service.removeAetheryte(originalOwner, target.id(), true);
            assertThat(regionTasks).hasSize(1);

            registry.update(world, target.id(), a -> a.withOwner(otherPlayerId, "NewOwner"));
            regionTasks.remove().run();

            assertThat(registry.getById(target.id())).isPresent();
            verify(originalOwner, atLeastOnce()).sendMessage(any(Component.class));

            // 2. New owner queues removal, and variation is updated before region callback executes
            final Player newOwner = mockPlayer(otherPlayerId, "NewOwner");
            when(newOwner.hasPermission(Permissions.PLACE)).thenReturn(true);
            when(newOwner.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(variationRegistry.getOrDefault("changed_var")).thenReturn(AetheryteVariation.defaultVariation());

            service.removeAetheryte(newOwner, target.id(), true);
            registry.update(world, target.id(), a -> a.withVariation("changed_var"));
            regionTasks.remove().run();

            assertThat(registry.getById(target.id())).isEmpty();
            verify(variationRegistry).getOrDefault("changed_var");
            verify(variationRegistry, never()).getOrDefault(Aetheryte.DEFAULT_VARIATION);
        });
    }

    @Test
    void resyncVisualsAbortsWhenAetheryteWasRemovedBeforeRegionCallback() throws Exception {
        final Aetheryte target = createAetheryte("Vanishing", ownerId, "Alice");
        registry.register(world, target);

        final Player admin = mockPlayer(otherPlayerId, "Admin");
        when(admin.hasPermission(Permissions.ADMIN)).thenReturn(true);

        final Queue<Runnable> regionTasks = new ArrayDeque<>();
        withMockServer(regionTasks, () -> {
            service.resyncVisuals(admin, target.id());
            assertThat(regionTasks).hasSize(1);

            registry.unregister(world, target.id());
            regionTasks.remove().run();

            verify(modelService, never()).unregisterAetheryte(any());
            verify(modelService, never()).registerAetheryte(any(), any(), any());
            verify(hologramService, never()).showAetheryte(any());
            verify(admin, atLeastOnce()).sendMessage(any(Component.class));
        });
    }

    @Test
    void toggleFavoritePrunesDeletedFavoritesFromLoadedWorldBeforeEnforcingLimit() {
        final Map<NamespacedKey, Object> backing = new HashMap<>();
        final Player player = mockPlayer(ownerId, "Alice", createInMemoryPdc(backing));
        final Aetheryte a1 = new Aetheryte(UUID.randomUUID(), "First", worldId, "world", 0, 64, 0, ownerId, 100L, Aetheryte.DEFAULT_VARIATION, "Alice");
        final Aetheryte a2 = new Aetheryte(UUID.randomUUID(), "Second", worldId, "world", 10, 64, 0, ownerId, 200L, Aetheryte.DEFAULT_VARIATION, "Alice");
        final Aetheryte a3 = new Aetheryte(UUID.randomUUID(), "Third", worldId, "world", 20, 64, 0, ownerId, 300L, Aetheryte.DEFAULT_VARIATION, "Alice");
        registry.register(world, a1);
        registry.register(world, a2);
        registry.register(world, a3);

        service.setMaxFavoritesPerPlayerSupplier(() -> 2);
        assertThat(service.toggleFavorite(player, a1.id())).isTrue();
        assertThat(service.toggleFavorite(player, a2.id())).isTrue();
        assertThat(service.toggleFavorite(player, a3.id())).isFalse();

        // Deleting a1 from the loaded world must free its favorite slot
        registry.unregister(world, a1.id());
        assertThat(service.getFavorites(player)).containsExactlyInAnyOrder(a2.id());
        assertThat(service.toggleFavorite(player, a3.id())).isTrue();
        assertThat(service.getFavorites(player)).containsExactlyInAnyOrder(a2.id(), a3.id());

        // Attempting to favorite a non-existent Aetheryte while worlds are loaded is rejected
        assertThat(service.toggleFavorite(player, UUID.randomUUID())).isFalse();
        assertThat(service.getFavorites(player)).containsExactlyInAnyOrder(a2.id(), a3.id());
    }

    @Test
    void toggleFavoritePreservesFavoritesInUnloadedWorld() {
        final UUID world2Id = UUID.randomUUID();
        final World world2 = mock(World.class);
        final PersistentDataContainer world2Pdc = createInMemoryPdc();
        when(world2.getUID()).thenReturn(world2Id);
        when(world2.getName()).thenReturn("world_nether");
        when(world2.getPersistentDataContainer()).thenReturn(world2Pdc);

        final Aetheryte a1 = new Aetheryte(UUID.randomUUID(), "Overworld", worldId, "world", 0, 64, 0, ownerId, 100L, Aetheryte.DEFAULT_VARIATION, "Alice");
        final Aetheryte a2 = new Aetheryte(UUID.randomUUID(), "Nether", world2Id, "world_nether", 0, 64, 0, ownerId, 200L, Aetheryte.DEFAULT_VARIATION, "Alice");
        final Aetheryte a3 = new Aetheryte(UUID.randomUUID(), "Overworld2", worldId, "world", 10, 64, 0, ownerId, 300L, Aetheryte.DEFAULT_VARIATION, "Alice");
        registry.register(world, a1);
        registry.register(world2, a2);
        registry.register(world, a3);

        final Map<NamespacedKey, Object> backing = new HashMap<>();
        final Player player = mockPlayer(ownerId, "Alice", createInMemoryPdc(backing));
        service.setMaxFavoritesPerPlayerSupplier(() -> 2);

        assertThat(service.toggleFavorite(player, a1.id())).isTrue();
        assertThat(service.toggleFavorite(player, a2.id())).isTrue();

        // Unloading world2 must NOT erase a2 from favorites when interacting in world
        registry.unloadWorld(world2Id);
        assertThat(service.toggleFavorite(player, a3.id())).isFalse();
        assertThat(service.getFavorites(player)).containsExactlyInAnyOrder(a1.id(), a2.id());

        registry.loadWorld(world2);
        assertThat(service.getFavorites(player)).containsExactlyInAnyOrder(a1.id(), a2.id());

        // Deleting a1 (in loaded world) still prunes a1 while preserving unloaded-world a2
        registry.unloadWorld(world2Id);
        registry.unregister(world, a1.id());
        assertThat(service.toggleFavorite(player, a3.id())).isTrue();
        assertThat(service.getFavorites(player)).containsExactlyInAnyOrder(a2.id(), a3.id());
    }

    @Test
    void renameAetheryteRechecksPermissionsInsideRegionCallbackWhenOwnershipTransferred() throws Exception {
        final Aetheryte target = createAetheryte("OldName", ownerId, "Alice");
        registry.register(world, target);

        final Player originalOwner = mockPlayer(ownerId, "Alice");
        final Queue<Runnable> regionTasks = new ArrayDeque<>();
        withMockServer(regionTasks, () -> {
            service.renameAetheryte(originalOwner, target.id(), "HijackedName");
            assertThat(regionTasks).hasSize(1);

            registry.update(world, target.id(), a -> a.withOwner(otherPlayerId, "NewOwner"));
            regionTasks.remove().run();

            assertThat(registry.getById(target.id())).get()
                    .extracting(Aetheryte::name)
                    .isEqualTo("OldName");
            verify(originalOwner, atLeastOnce()).sendMessage(any(Component.class));
        });
    }

    private Aetheryte createAetheryte(final String name, final UUID owner, @Nullable final String ownerName) {
        return new Aetheryte(
                UUID.randomUUID(),
                name,
                worldId,
                "world",
                10,
                64,
                20,
                owner,
                1000L,
                Aetheryte.DEFAULT_VARIATION,
                ownerName
        );
    }

    private Player mockPlayer(final UUID id, final String name) {
        return mockPlayer(id, name, createInMemoryPdc());
    }

    private Player mockPlayer(final UUID id, final String name, final PersistentDataContainer pdc) {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.getPersistentDataContainer()).thenReturn(pdc);
        when(player.getLocation()).thenReturn(new Location(world, 10, 64, 20));
        when(player.getWorld()).thenReturn(world);
        final EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(entityScheduler.run(any(), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            final Consumer<Object> consumer = (Consumer<Object>) inv.getArgument(1);
            consumer.accept(null);
            return null;
        });
        when(player.getScheduler()).thenReturn(entityScheduler);
        return player;
    }

    private void withMockServer(final Runnable action) throws Exception {
        withMockServer(null, action);
    }

    private void withMockServer(@Nullable final Queue<Runnable> deferredRegionTasks, final Runnable action) throws Exception {
        final Server server = mock(Server.class);
        when(server.getWorld(worldId)).thenReturn(world);
        when(server.getOnlinePlayers()).thenReturn(List.of());
        final RegionScheduler regionScheduler = mock(RegionScheduler.class);
        when(server.getRegionScheduler()).thenReturn(regionScheduler);
        doAnswer(inv -> {
            final Runnable task = inv.getArgument(2);
            if (deferredRegionTasks != null) {
                deferredRegionTasks.add(task);
            } else {
                task.run();
            }
            return null;
        }).when(regionScheduler).execute(any(), any(Location.class), any());
        doAnswer(inv -> {
            final Runnable task = inv.getArgument(4);
            if (deferredRegionTasks != null) {
                deferredRegionTasks.add(task);
            } else {
                task.run();
            }
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
        return createInMemoryPdc(new HashMap<>());
    }

    private static PersistentDataContainer createInMemoryPdc(final Map<NamespacedKey, Object> map) {
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
                    if (type.getComplexType().isInstance(value)) {
                        return type.getComplexType().cast(value);
                    }
                    return null;
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
