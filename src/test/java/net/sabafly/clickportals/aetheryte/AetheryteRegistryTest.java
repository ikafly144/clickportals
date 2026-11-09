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
}
