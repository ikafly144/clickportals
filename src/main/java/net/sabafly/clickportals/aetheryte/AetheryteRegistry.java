package net.sabafly.clickportals.aetheryte;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.ListPersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Thread-safe in-memory index and World PDC persistence manager for {@link Aetheryte} instances (#20, #21).
 */
public final class AetheryteRegistry {

    public static final NamespacedKey WORLD_AETHERYTES_KEY = new NamespacedKey("clickportals", "aetherytes");
    private static final ListPersistentDataType<PersistentDataContainer, Aetheryte> LIST_TYPE =
            PersistentDataType.LIST.listTypeFrom(AetheryteDataType.INSTANCE);
    private static final Comparator<Aetheryte> DEFAULT_ORDER = Comparator
            .comparingLong(Aetheryte::createdAt)
            .thenComparing(Aetheryte::name)
            .thenComparing(Aetheryte::id);

    private final Map<UUID, Aetheryte> byId = new ConcurrentHashMap<>();
    private final Map<BlockPosKey, Aetheryte> byPos = new ConcurrentHashMap<>();

    /**
     * Loads all Aetherytes stored in the given world's {@link PersistentDataContainer} into the in-memory index.
     */
    public List<Aetheryte> loadWorld(final World world) {
        final PersistentDataContainer pdc = world.getPersistentDataContainer();
        final List<Aetheryte> stored = pdc.get(WORLD_AETHERYTES_KEY, LIST_TYPE);
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        final List<Aetheryte> loaded = new ArrayList<>(stored.size());
        for (final Aetheryte entry : stored) {
            final Aetheryte normalized = new Aetheryte(
                    entry.id(),
                    entry.name(),
                    world.getUID(),
                    world.getName(),
                    entry.blockX(),
                    entry.blockY(),
                    entry.blockZ(),
                    entry.ownerId(),
                    entry.createdAt()
            );
            byId.put(normalized.id(), normalized);
            byPos.put(normalized.posKey(), normalized);
            loaded.add(normalized);
        }
        return List.copyOf(loaded);
    }

    /**
     * Removes all Aetherytes belonging to the specified world from the in-memory index.
     */
    public void unloadWorld(final UUID worldId) {
        byId.values().removeIf(aetheryte -> {
            if (aetheryte.worldId().equals(worldId)) {
                byPos.remove(aetheryte.posKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Registers a new Aetheryte in memory and persists the updated list to the world's PDC.
     */
    public synchronized void register(final World world, final Aetheryte aetheryte) {
        byId.put(aetheryte.id(), aetheryte);
        byPos.put(aetheryte.posKey(), aetheryte);
        saveWorld(world);
    }

    /**
     * Unregisters an Aetheryte from memory and updates the world's PDC.
     */
    public synchronized Optional<Aetheryte> unregister(final World world, final UUID id) {
        final Aetheryte removed = byId.remove(id);
        if (removed == null) {
            return Optional.empty();
        }
        byPos.remove(removed.posKey());
        saveWorld(world);
        return Optional.of(removed);
    }

    /**
     * Persists all in-memory Aetherytes for the given world into its {@link PersistentDataContainer}.
     */
    public synchronized void saveWorld(final World world) {
        final UUID worldId = world.getUID();
        final List<Aetheryte> worldEntries = byId.values().stream()
                .filter(a -> a.worldId().equals(worldId))
                .sorted(DEFAULT_ORDER)
                .toList();
        final PersistentDataContainer pdc = world.getPersistentDataContainer();
        if (worldEntries.isEmpty()) {
            pdc.remove(WORLD_AETHERYTES_KEY);
        } else {
            pdc.set(WORLD_AETHERYTES_KEY, LIST_TYPE, worldEntries);
        }
    }

    public Optional<Aetheryte> getById(final UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<Aetheryte> getByPosition(final UUID worldId, final int x, final int y, final int z) {
        return Optional.ofNullable(byPos.get(new BlockPosKey(worldId, x, y, z)));
    }

    public Optional<Aetheryte> getByPosition(final World world, final int x, final int y, final int z) {
        return getByPosition(world.getUID(), x, y, z);
    }

    public List<Aetheryte> getByWorld(final UUID worldId) {
        return byId.values().stream()
                .filter(a -> a.worldId().equals(worldId))
                .sorted(DEFAULT_ORDER)
                .toList();
    }

    public List<Aetheryte> getAll() {
        return byId.values().stream()
                .sorted(DEFAULT_ORDER)
                .toList();
    }

    public void clear() {
        byId.clear();
        byPos.clear();
    }
}
