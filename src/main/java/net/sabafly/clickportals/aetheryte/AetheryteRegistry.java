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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.ListPersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Thread-safe in-memory index and World/Marker PDC persistence manager for {@link Aetheryte} instances (#6, #20, #21).
 */
public final class AetheryteRegistry {

    public static final NamespacedKey WORLD_AETHERYTES_KEY = new NamespacedKey("clickportals", "aetherytes");
    public static final NamespacedKey MARKER_AETHERYTE_KEY = new NamespacedKey("clickportals", "aetheryte");
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
     * Writes the given {@link Aetheryte} data into a Marker entity's {@link PersistentDataContainer} (#6).
     */
    public static void writeMarkerData(final Entity marker, final Aetheryte aetheryte) {
        marker.getPersistentDataContainer().set(MARKER_AETHERYTE_KEY, AetheryteDataType.INSTANCE, aetheryte);
    }

    /**
     * Reads {@link Aetheryte} data from an entity's {@link PersistentDataContainer}, if present (#6).
     */
    public static Optional<Aetheryte> readMarkerData(final Entity entity) {
        return Optional.ofNullable(
                entity.getPersistentDataContainer().get(MARKER_AETHERYTE_KEY, AetheryteDataType.INSTANCE)
        );
    }

    /**
     * Checks whether the given entity is an Aetheryte marker entity (#6).
     */
    public static boolean isAetheryteEntity(final Entity entity) {
        return entity.getPersistentDataContainer().has(MARKER_AETHERYTE_KEY, AetheryteDataType.INSTANCE);
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

    /**
     * Looks up an Aetheryte by its unique ID in O(1) time.
     */
    public Optional<Aetheryte> getById(final UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Looks up an Aetheryte by its world UUID and block coordinates in O(1) time.
     */
    public Optional<Aetheryte> getByPosition(final UUID worldId, final int x, final int y, final int z) {
        return Optional.ofNullable(byPos.get(new BlockPosKey(worldId, x, y, z)));
    }

    /**
     * Looks up an Aetheryte by its Bukkit {@link World} and block coordinates in O(1) time.
     */
    public Optional<Aetheryte> getByPosition(final World world, final int x, final int y, final int z) {
        return getByPosition(world.getUID(), x, y, z);
    }

    /**
     * Returns all Aetherytes registered in the specified world, ordered deterministically.
     */
    public List<Aetheryte> getByWorld(final UUID worldId) {
        return byId.values().stream()
                .filter(a -> a.worldId().equals(worldId))
                .sorted(DEFAULT_ORDER)
                .toList();
    }

    /**
     * Returns all registered Aetherytes across all loaded worlds, ordered deterministically.
     */
    public List<Aetheryte> getAll() {
        return byId.values().stream()
                .sorted(DEFAULT_ORDER)
                .toList();
    }

    /**
     * Clears the in-memory index without modifying world PDC data.
     */
    public void clear() {
        byId.clear();
        byPos.clear();
    }
}
