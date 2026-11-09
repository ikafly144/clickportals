package net.sabafly.clickportals.aetheryte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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
