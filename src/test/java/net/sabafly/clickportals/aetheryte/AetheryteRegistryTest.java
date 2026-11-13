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
package net.sabafly.clickportals.aetheryte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Marker;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AetheryteRegistryTest {

    private static final UUID WORLD_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final String WORLD_NAME = "world";

    private AetheryteRegistry registry;
    private World world;
    private Map<NamespacedKey, Object> worldPdcStore;

    @BeforeEach
    void setUp() {
        registry = new AetheryteRegistry();
        worldPdcStore = new HashMap<>();
        final PersistentDataContainer pdc = AetheryteDataTypeTest.createInMemoryContainer(worldPdcStore);
        world = mock(World.class);
        when(world.getUID()).thenReturn(WORLD_ID);
        when(world.getName()).thenReturn(WORLD_NAME);
        when(world.getPersistentDataContainer()).thenReturn(pdc);
    }

    @Test
    void registerLookupAndUnregisterUpdatesIndexAndWorldPdc() {
        final Aetheryte first = new Aetheryte(
                UUID.randomUUID(),
                "グリダニア",
                WORLD_ID,
                WORLD_NAME,
                10,
                70,
                20,
                UUID.randomUUID(),
                1000L
        );
        final Aetheryte second = new Aetheryte(
                UUID.randomUUID(),
                "リムサ・ロミンサ",
                WORLD_ID,
                WORLD_NAME,
                -50,
                65,
                120,
                UUID.randomUUID(),
                2000L
        );

        registry.register(world, first);
        registry.register(world, second);

        assertThat(registry.getById(first.id())).contains(first);
        assertThat(registry.getByPosition(world, 10, 70, 20)).contains(first);
        assertThat(registry.getByPosition(world, -50, 65, 120)).contains(second);
        assertThat(registry.getAll()).containsExactly(first, second);
        assertThat(worldPdcStore).containsKey(AetheryteRegistry.WORLD_AETHERYTES_KEY);

        // Reload into a fresh registry from the World PDC
        final AetheryteRegistry reloaded = new AetheryteRegistry();
        final List<Aetheryte> loadedList = reloaded.loadWorld(world);
        assertThat(loadedList).containsExactly(first, second);
        assertThat(reloaded.getByPosition(world, 10, 70, 20)).contains(first);

        // Unregister first and verify
        assertThat(registry.unregister(world, first.id())).contains(first);
        assertThat(registry.getById(first.id())).isEmpty();
        assertThat(registry.getByPosition(world, 10, 70, 20)).isEmpty();
        assertThat(registry.getAll()).containsExactly(second);

        // Unregister second and verify PDC key is cleaned up
        assertThat(registry.unregister(world, second.id())).contains(second);
        assertThat(registry.getAll()).isEmpty();
        assertThat(worldPdcStore).doesNotContainKey(AetheryteRegistry.WORLD_AETHERYTES_KEY);
    }

    @Test
    void markerEntityPdcStoresAndReadsAetheryteData() {
        final Marker marker = mock(Marker.class);
        final PersistentDataContainer markerPdc = AetheryteDataTypeTest.createInMemoryContainer(new HashMap<>());
        when(marker.getPersistentDataContainer()).thenReturn(markerPdc);

        final Aetheryte aetheryte = new Aetheryte(
                UUID.randomUUID(),
                "ウルダハ",
                WORLD_ID,
                WORLD_NAME,
                0,
                64,
                0,
                UUID.randomUUID(),
                500L
        );

        assertThat(AetheryteRegistry.isAetheryteEntity(marker)).isFalse();
        AetheryteRegistry.writeMarkerData(marker, aetheryte);
        assertThat(AetheryteRegistry.isAetheryteEntity(marker)).isTrue();
        assertThat(AetheryteRegistry.readMarkerData(marker)).contains(aetheryte);
    }

    @Test
    void distanceToComputesEuclideanDistanceInSameWorldAndEmptyAcrossWorlds() {
        final Aetheryte origin = new Aetheryte(
                UUID.randomUUID(),
                "起点",
                WORLD_ID,
                WORLD_NAME,
                0,
                64,
                0,
                UUID.randomUUID(),
                100L
        );
        final Aetheryte sameWorldTarget = new Aetheryte(
                UUID.randomUUID(),
                "同ワールド先",
                WORLD_ID,
                WORLD_NAME,
                30,
                64,
                40,
                UUID.randomUUID(),
                200L
        );
        final Aetheryte crossWorldTarget = new Aetheryte(
                UUID.randomUUID(),
                "別ワールド先",
                UUID.randomUUID(),
                "world_nether",
                30,
                64,
                40,
                UUID.randomUUID(),
                300L
        );

        assertThat(origin.distanceTo(sameWorldTarget)).hasValueCloseTo(50.0, within(0.001));
        assertThat(origin.distanceTo(crossWorldTarget)).isEmpty();
    }

    @Test
    void unloadWorldRemovesOnlyTargetWorldEntries() {
        final UUID secondWorldId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        final PersistentDataContainer secondPdc = AetheryteDataTypeTest.createInMemoryContainer(new HashMap<>());
        final World secondWorld = mock(World.class);
        when(secondWorld.getUID()).thenReturn(secondWorldId);
        when(secondWorld.getName()).thenReturn("world_nether");
        when(secondWorld.getPersistentDataContainer()).thenReturn(secondPdc);

        final Aetheryte overworldAetheryte = new Aetheryte(
                UUID.randomUUID(),
                "オーバーワールド拠点",
                WORLD_ID,
                WORLD_NAME,
                0,
                64,
                0,
                UUID.randomUUID(),
                100L
        );
        final Aetheryte netherAetheryte = new Aetheryte(
                UUID.randomUUID(),
                "ネザーゲート前",
                secondWorldId,
                "world_nether",
                10,
                80,
                10,
                UUID.randomUUID(),
                200L
        );

        registry.register(world, overworldAetheryte);
        registry.register(secondWorld, netherAetheryte);

        registry.unloadWorld(WORLD_ID);

        assertThat(registry.getById(overworldAetheryte.id())).isEmpty();
        assertThat(registry.getByPosition(world, 0, 64, 0)).isEmpty();
        assertThat(registry.getById(netherAetheryte.id())).contains(netherAetheryte);
        assertThat(registry.getAll()).containsExactly(netherAetheryte);
    }

    @Test
    void tryRegisterEnforcesGlobalAndVariationLimitsAndOwnerCounts() {
        final UUID owner = UUID.randomUUID();
        final Aetheryte a1 = new Aetheryte(
                UUID.randomUUID(), "One", WORLD_ID, WORLD_NAME, 0, 64, 0, owner, 100L, "small"
        );
        final Aetheryte a2 = new Aetheryte(
                UUID.randomUUID(), "Two", WORLD_ID, WORLD_NAME, 10, 64, 0, owner, 200L, "small"
        );
        final Aetheryte a3 = new Aetheryte(
                UUID.randomUUID(), "Three", WORLD_ID, WORLD_NAME, 20, 64, 0, owner, 300L, "large"
        );
        final Aetheryte duplicatePos = new Aetheryte(
                UUID.randomUUID(), "Dup", WORLD_ID, WORLD_NAME, 0, 64, 0, owner, 400L, "large"
        );

        assertThat(registry.tryRegister(world, a1, 2, 1))
                .isEqualTo(AetheryteRegistry.RegistrationOutcome.REGISTERED);
        assertThat(registry.countByOwner(owner)).isEqualTo(1);
        assertThat(registry.countByOwnerAndVariation(owner, "small")).isEqualTo(1);

        // Same position rejected
        assertThat(registry.tryRegister(world, duplicatePos, -1, -1))
                .isEqualTo(AetheryteRegistry.RegistrationOutcome.ALREADY_EXISTS);

        // Variation limit (1 for "small") reached
        assertThat(registry.tryRegister(world, a2, 5, 1))
                .isEqualTo(AetheryteRegistry.RegistrationOutcome.VARIATION_LIMIT_REACHED);

        // Different variation ("large") succeeds under global limit 2
        assertThat(registry.tryRegister(world, a3, 2, 1))
                .isEqualTo(AetheryteRegistry.RegistrationOutcome.REGISTERED);
        assertThat(registry.countByOwner(owner)).isEqualTo(2);

        // Now global limit 2 is reached
        assertThat(registry.tryRegister(world, a2, 2, -1))
                .isEqualTo(AetheryteRegistry.RegistrationOutcome.GLOBAL_LIMIT_REACHED);
    }

    @Test
    void getByChunkReturnsAetherytesInTargetChunk() {
        final Aetheryte a1 = new Aetheryte(UUID.randomUUID(), "Chunk0_0", WORLD_ID, WORLD_NAME, 5, 64, 5, UUID.randomUUID(), 100L);
        final Aetheryte a2 = new Aetheryte(UUID.randomUUID(), "Chunk0_0_b", WORLD_ID, WORLD_NAME, 15, 64, 15, UUID.randomUUID(), 200L);
        final Aetheryte a3 = new Aetheryte(UUID.randomUUID(), "Chunk1_0", WORLD_ID, WORLD_NAME, 20, 64, 5, UUID.randomUUID(), 300L);

        registry.register(world, a1);
        registry.register(world, a2);
        registry.register(world, a3);

        // Chunk (0, 0)
        assertThat(registry.getByChunk(WORLD_ID, 0, 0)).containsExactlyInAnyOrder(a1, a2);
        // Chunk (1, 0)
        assertThat(registry.getByChunk(world, 1, 0)).containsExactly(a3);
        // Empty chunk (2, 2)
        assertThat(registry.getByChunk(WORLD_ID, 2, 2)).isEmpty();

        // After unregister
        registry.unregister(world, a1.id());
        assertThat(registry.getByChunk(WORLD_ID, 0, 0)).containsExactly(a2);
    }

    @Test
    void findWithinDistanceFindsClosestAetheryteInSameWorld() {
        final Aetheryte a1 = new Aetheryte(UUID.randomUUID(), "Origin", WORLD_ID, WORLD_NAME, 100, 64, 100, UUID.randomUUID(), 100L);
        registry.register(world, a1);

        // Within 10 blocks (dx=3, dy=4, dz=0 -> dist=5.0)
        assertThat(registry.findWithinDistance(WORLD_ID, 103, 68, 100, 10.0)).contains(a1);
        // Exactly 5.0 blocks (distSq == 25, threshold 5.0 is exclusive)
        assertThat(registry.findWithinDistance(WORLD_ID, 103, 68, 100, 5.0)).isEmpty();
        // Outside 4.0 blocks
        assertThat(registry.findWithinDistance(WORLD_ID, 103, 68, 100, 4.0)).isEmpty();

        // Different world
        final UUID otherWorldId = UUID.randomUUID();
        assertThat(registry.findWithinDistance(otherWorldId, 100, 64, 100, 50.0)).isEmpty();
    }

    @Test
    void tryRegisterEnforcesMinDistance() {
        final UUID owner = UUID.randomUUID();
        final Aetheryte existing = new Aetheryte(UUID.randomUUID(), "Existing", WORLD_ID, WORLD_NAME, 100, 64, 100, owner, 100L);
        registry.register(world, existing);

        // Attempt placement at distance 30 when minDistance is 50 -> TOO_CLOSE
        final Aetheryte tooClose = new Aetheryte(UUID.randomUUID(), "TooClose", WORLD_ID, WORLD_NAME, 130, 64, 100, owner, 200L);
        assertThat(registry.tryRegister(world, tooClose, -1, -1, 50.0))
                .isEqualTo(AetheryteRegistry.RegistrationOutcome.TOO_CLOSE);

        // Attempt placement at distance 60 when minDistance is 50 -> REGISTERED
        final Aetheryte farEnough = new Aetheryte(UUID.randomUUID(), "FarEnough", WORLD_ID, WORLD_NAME, 160, 64, 100, owner, 300L);
        assertThat(registry.tryRegister(world, farEnough, -1, -1, 50.0))
                .isEqualTo(AetheryteRegistry.RegistrationOutcome.REGISTERED);
    }

    @Test
    void reRegisterAetheryteReplacesOldChunkIndexEntry() {
        final UUID aetheryteId = UUID.randomUUID();
        final UUID owner = UUID.randomUUID();
        // Initial registration at block (10, 64, 10) -> chunk (0, 0)
        final Aetheryte initial = new Aetheryte(aetheryteId, "Initial", WORLD_ID, WORLD_NAME, 10, 64, 10, owner, 100L);
        registry.register(world, initial);

        assertThat(registry.getByChunk(WORLD_ID, 0, 0)).containsExactly(initial);

        // Re-register same UUID at block (32, 64, 32) -> chunk (2, 2)
        final Aetheryte updated = new Aetheryte(aetheryteId, "Updated", WORLD_ID, WORLD_NAME, 32, 64, 32, owner, 200L);
        registry.register(world, updated);

        // Old chunk index must no longer contain the Aetheryte
        assertThat(registry.getByChunk(WORLD_ID, 0, 0)).isEmpty();
        // New chunk index must contain updated Aetheryte
        assertThat(registry.getByChunk(WORLD_ID, 2, 2)).containsExactly(updated);

        // Unregister removes from new chunk cleanly
        registry.unregister(world, aetheryteId);
        assertThat(registry.getByChunk(WORLD_ID, 2, 2)).isEmpty();
    }

    @Test
    void reRegisterAetheryteDoesNotRemoveDifferentAetheryteAtOldPosition() {
        final UUID owner = UUID.randomUUID();
        final UUID idA = UUID.randomUUID();
        final UUID idB = UUID.randomUUID();
        final Aetheryte aetheryteA = new Aetheryte(idA, "A", WORLD_ID, WORLD_NAME, 10, 64, 10, owner, 100L);
        final Aetheryte aetheryteB = new Aetheryte(idB, "B", WORLD_ID, WORLD_NAME, 10, 64, 10, owner, 200L);

        registry.register(world, aetheryteA);
        // Another Aetheryte B is registered at the same position (10, 64, 10)
        registry.register(world, aetheryteB);

        // Now A is moved/re-registered to a new position (50, 64, 50)
        final Aetheryte movedA = new Aetheryte(idA, "A", WORLD_ID, WORLD_NAME, 50, 64, 50, owner, 300L);
        registry.register(world, movedA);

        // B's position index at (10, 64, 10) must NOT be deleted when A moves
        assertThat(registry.getByPosition(world, 10, 64, 10)).contains(aetheryteB);
        assertThat(registry.getByPosition(world, 50, 64, 50)).contains(movedA);
    }
}
