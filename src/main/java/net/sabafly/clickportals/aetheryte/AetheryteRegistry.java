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
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.ListPersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Thread-safe in-memory index and World/Marker PDC persistence manager for {@link Aetheryte} instances (#6, #20, #21, #37, #43).
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

    /**
     * Result of attempting to register a new Aetheryte while enforcing per-player limits (#43).
     */
    public enum RegistrationOutcome {
        REGISTERED,
        ALREADY_EXISTS,
        TOO_CLOSE,
        GLOBAL_LIMIT_REACHED,
        VARIATION_LIMIT_REACHED
    }

    /**
     * Cache key for spatial chunk lookups.
     */
    public record ChunkPosKey(UUID worldId, int chunkX, int chunkZ) {
    }

    private final Map<UUID, Aetheryte> byId = new ConcurrentHashMap<>();
    private final Map<BlockPosKey, Aetheryte> byPos = new ConcurrentHashMap<>();
    private final Map<ChunkPosKey, List<Aetheryte>> byChunk = new ConcurrentHashMap<>();

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
                    entry.createdAt(),
                    entry.variation()
            );
            byId.put(normalized.id(), normalized);
            byPos.put(normalized.posKey(), normalized);
            final ChunkPosKey chunkKey = new ChunkPosKey(world.getUID(), normalized.blockX() >> 4, normalized.blockZ() >> 4);
            byChunk.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(normalized);
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
        byChunk.keySet().removeIf(k -> k.worldId().equals(worldId));
    }

    /**
     * Registers a new Aetheryte in memory and persists the updated list to the world's PDC.
     */
    public synchronized void register(final World world, final Aetheryte aetheryte) {
        byId.put(aetheryte.id(), aetheryte);
        byPos.put(aetheryte.posKey(), aetheryte);
        final ChunkPosKey chunkKey = new ChunkPosKey(world.getUID(), aetheryte.blockX() >> 4, aetheryte.blockZ() >> 4);
        byChunk.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(aetheryte);
        saveWorld(world);
    }

    /**
     * Atomically verifies position uniqueness, distance to existing Aetherytes, and per-player placement limits
     * before registering and persisting a new Aetheryte.
     *
     * @param maxGlobal    maximum total Aetherytes per player ({@code < 0} for unlimited)
     * @param maxVariation maximum Aetherytes of this variation per player ({@code < 0} for unlimited)
     * @param minDistance  minimum block distance to existing Aetherytes in the same world ({@code <= 0} to disable)
     */
    public synchronized RegistrationOutcome tryRegister(
            final World world,
            final Aetheryte aetheryte,
            final int maxGlobal,
            final int maxVariation,
            final double minDistance
    ) {
        final BlockPosKey posKey = aetheryte.posKey();
        if (byPos.containsKey(posKey)) {
            return RegistrationOutcome.ALREADY_EXISTS;
        }
        if (minDistance > 0.0 && findWithinDistance(world.getUID(), aetheryte.blockX(), aetheryte.blockY(), aetheryte.blockZ(), minDistance).isPresent()) {
            return RegistrationOutcome.TOO_CLOSE;
        }
        if (maxGlobal >= 0 && countByOwner(aetheryte.ownerId()) >= maxGlobal) {
            return RegistrationOutcome.GLOBAL_LIMIT_REACHED;
        }
        if (maxVariation >= 0 && countByOwnerAndVariation(aetheryte.ownerId(), aetheryte.variation()) >= maxVariation) {
            return RegistrationOutcome.VARIATION_LIMIT_REACHED;
        }
        byId.put(aetheryte.id(), aetheryte);
        byPos.put(posKey, aetheryte);
        final ChunkPosKey chunkKey = new ChunkPosKey(world.getUID(), aetheryte.blockX() >> 4, aetheryte.blockZ() >> 4);
        byChunk.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(aetheryte);
        saveWorld(world);
        return RegistrationOutcome.REGISTERED;
    }

    /**
     * Atomically verifies position uniqueness and per-player placement limits before registering and persisting
     * a new Aetheryte (#43).
     *
     * @param maxGlobal    maximum total Aetherytes per player ({@code < 0} for unlimited)
     * @param maxVariation maximum Aetherytes of this variation per player ({@code < 0} for unlimited)
     */
    public synchronized RegistrationOutcome tryRegister(
            final World world,
            final Aetheryte aetheryte,
            final int maxGlobal,
            final int maxVariation
    ) {
        return tryRegister(world, aetheryte, maxGlobal, maxVariation, 0.0);
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
        final ChunkPosKey chunkKey = new ChunkPosKey(world.getUID(), removed.blockX() >> 4, removed.blockZ() >> 4);
        byChunk.computeIfPresent(chunkKey, (k, list) -> {
            list.remove(removed);
            return list.isEmpty() ? null : list;
        });
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
     * Counts the total number of loaded Aetherytes owned by the specified player (#43).
     */
    public int countByOwner(final UUID ownerId) {
        int count = 0;
        for (final Aetheryte aetheryte : byId.values()) {
            if (aetheryte.ownerId().equals(ownerId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts the number of loaded Aetherytes of a specific variation owned by the specified player (#43).
     */
    public int countByOwnerAndVariation(final UUID ownerId, final String variation) {
        final String normalized = Aetheryte.normalizeVariation(variation);
        int count = 0;
        for (final Aetheryte aetheryte : byId.values()) {
            if (aetheryte.ownerId().equals(ownerId) && aetheryte.variation().equals(normalized)) {
                count++;
            }
        }
        return count;
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
     * Looks up all Aetherytes located within the specified chunk in O(1) time.
     */
    public List<Aetheryte> getByChunk(final UUID worldId, final int chunkX, final int chunkZ) {
        final List<Aetheryte> list = byChunk.get(new ChunkPosKey(worldId, chunkX, chunkZ));
        return list == null ? List.of() : List.copyOf(list);
    }

    /**
     * Looks up all Aetherytes located within the specified chunk in O(1) time.
     */
    public List<Aetheryte> getByChunk(final World world, final int chunkX, final int chunkZ) {
        return getByChunk(world.getUID(), chunkX, chunkZ);
    }

    /**
     * Finds an Aetheryte in the same world that is strictly within {@code distance} blocks (3D Euclidean distance)
     * of the specified coordinates.
     *
     * @param worldId  world identifier
     * @param x        target X coordinate
     * @param y        target Y coordinate
     * @param z        target Z coordinate
     * @param distance maximum distance threshold (exclusive)
     * @return the first found Aetheryte within the distance, or empty
     */
    public Optional<Aetheryte> findWithinDistance(
            final UUID worldId,
            final int x,
            final int y,
            final int z,
            final double distance
    ) {
        if (distance <= 0.0) {
            return Optional.empty();
        }
        final double distSq = distance * distance;
        for (final Aetheryte aetheryte : byId.values()) {
            if (!aetheryte.worldId().equals(worldId)) {
                continue;
            }
            final double dx = aetheryte.blockX() - x;
            final double dy = aetheryte.blockY() - y;
            final double dz = aetheryte.blockZ() - z;
            if ((dx * dx + dy * dy + dz * dz) < distSq) {
                return Optional.of(aetheryte);
            }
        }
        return Optional.empty();
    }

    /**
     * Clears the in-memory index without modifying world PDC data.
     */
    public void clear() {
        byId.clear();
        byPos.clear();
        byChunk.clear();
    }
}
