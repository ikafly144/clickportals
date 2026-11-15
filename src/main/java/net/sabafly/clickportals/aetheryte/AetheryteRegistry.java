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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.ListPersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/**
 * Thread-safe in-memory index and World/Marker PDC persistence manager for {@link Aetheryte} instances (#6, #20, #21, #37, #43).
 */
public final class AetheryteRegistry {

    public static final NamespacedKey WORLD_AETHERYTES_KEY = new NamespacedKey("clickportals", "aetherytes");
    public static final NamespacedKey WORLD_NETWORKS_KEY = new NamespacedKey("clickportals", "networks");
    public static final NamespacedKey MARKER_AETHERYTE_KEY = new NamespacedKey("clickportals", "aetheryte");
    private static final ListPersistentDataType<PersistentDataContainer, Aetheryte> LIST_TYPE =
            PersistentDataType.LIST.listTypeFrom(AetheryteDataType.INSTANCE);
    private static final ListPersistentDataType<PersistentDataContainer, AetheryteNetwork> NETWORK_LIST_TYPE =
            PersistentDataType.LIST.listTypeFrom(AetheryteNetworkDataType.INSTANCE);
    private static final Comparator<Aetheryte> DEFAULT_ORDER = Comparator
            .comparingLong(Aetheryte::createdAt)
            .thenComparing(Aetheryte::name)
            .thenComparing(Aetheryte::id);
    private static final Comparator<AetheryteNetwork> NETWORK_ORDER = Comparator
            .comparingLong(AetheryteNetwork::createdAt)
            .thenComparing(AetheryteNetwork::name)
            .thenComparing(AetheryteNetwork::id);

    /**
     * Result of attempting to register a new Aetheryte while enforcing per-player limits (#43, #27).
     */
    public enum RegistrationOutcome {
        REGISTERED,
        ALREADY_EXISTS,
        TOO_CLOSE,
        GLOBAL_LIMIT_REACHED,
        VARIATION_LIMIT_REACHED,
        NETWORK_NOT_FOUND,
        GLOBAL_NETWORK_LIMIT_REACHED,
        VARIATION_NETWORK_LIMIT_REACHED,
        NETWORK_AETHERYTE_LIMIT_REACHED,
        NETWORK_TOO_FAR,
        NETWORK_HUB_REQUIRED
    }

    /**
     * Summary returned by {@link #unregisterHubCascade(World, UUID)} (#27).
     */
    public record HubCascadeRemoval(
            Aetheryte hub,
            List<AetheryteNetwork> removedNetworks,
            List<Aetheryte> removedChildren
    ) {
    }

    /**
     * Cache key for spatial chunk lookups.
     */
    public record ChunkPosKey(UUID worldId, int chunkX, int chunkZ) {
    }

    private record OwnerVariationKey(UUID ownerId, String variation) {
    }

    private final Map<UUID, Aetheryte> byId = new ConcurrentHashMap<>();
    private final Map<BlockPosKey, Aetheryte> byPos = new ConcurrentHashMap<>();
    private final Map<ChunkPosKey, List<Aetheryte>> byChunk = new ConcurrentHashMap<>();
    private final Map<UUID, List<Aetheryte>> byWorldCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> ownerCounts = new ConcurrentHashMap<>();
    private final Map<OwnerVariationKey, Integer> ownerVariationCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> networkMemberCounts = new ConcurrentHashMap<>();
    private final Map<UUID, AetheryteNetwork> networksById = new ConcurrentHashMap<>();
    private final Map<UUID, List<AetheryteNetwork>> networksByWorldCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<AetheryteNetwork>> networksByHubCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> ownerNetworkCounts = new ConcurrentHashMap<>();
    private final Set<UUID> loadedWorlds = ConcurrentHashMap.newKeySet();
    private volatile List<Aetheryte> allCache = List.of();
    private volatile List<AetheryteNetwork> allNetworksCache = List.of();

    /**
     * Loads all Aetherytes and networks stored in the given world's {@link PersistentDataContainer} into memory.
     */
    public synchronized List<Aetheryte> loadWorld(final World world) {
        final UUID worldId = world.getUID();
        loadedWorlds.add(worldId);
        final PersistentDataContainer pdc = world.getPersistentDataContainer();

        final List<AetheryteNetwork> storedNetworks = pdc.get(WORLD_NETWORKS_KEY, NETWORK_LIST_TYPE);
        if (storedNetworks != null && !storedNetworks.isEmpty()) {
            for (final AetheryteNetwork net : storedNetworks) {
                final AetheryteNetwork normalizedNet = new AetheryteNetwork(
                        net.id(),
                        net.name(),
                        worldId,
                        world.getName(),
                        net.ownerId(),
                        net.ownerName(),
                        net.createdAt(),
                        net.visibility(),
                        net.invitedPlayers(),
                        net.hubAetheryteId()
                );
                final AetheryteNetwork previousNet = indexNetworkEntry(normalizedNet);
                if (previousNet != null && !previousNet.worldId().equals(worldId)) {
                    rebuildNetworkWorldCache(previousNet.worldId());
                }
                if (previousNet != null && !Objects.equals(previousNet.hubAetheryteId(), normalizedNet.hubAetheryteId())) {
                    rebuildNetworkHubCache(previousNet.hubAetheryteId());
                }
                rebuildNetworkHubCache(normalizedNet.hubAetheryteId());
            }
            rebuildNetworkWorldCache(worldId);
            rebuildAllNetworksCache();
        }

        final List<Aetheryte> stored = pdc.get(WORLD_AETHERYTES_KEY, LIST_TYPE);
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        final List<Aetheryte> loaded = new ArrayList<>(stored.size());
        for (final Aetheryte entry : stored) {
            final Aetheryte normalized = new Aetheryte(
                    entry.id(),
                    entry.name(),
                    worldId,
                    world.getName(),
                    entry.blockX(),
                    entry.blockY(),
                    entry.blockZ(),
                    entry.ownerId(),
                    entry.createdAt(),
                    entry.variation(),
                    entry.ownerName(),
                    entry.visibility(),
                    entry.invitedPlayers(),
                    entry.networkId()
            );
            final Aetheryte previous = indexEntry(normalized);
            if (previous != null && !previous.worldId().equals(worldId)) {
                rebuildWorldCache(previous.worldId());
            }
            loaded.add(normalized);
        }
        rebuildWorldCache(worldId);
        rebuildAllCache();
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
    public synchronized void unloadWorld(final UUID worldId) {
        loadedWorlds.remove(worldId);
        final List<Aetheryte> removed = new ArrayList<>();
        byId.values().removeIf(aetheryte -> {
            if (aetheryte.worldId().equals(worldId)) {
                removed.add(aetheryte);
                return true;
            }
            return false;
        });
        for (final Aetheryte entry : removed) {
            removeFromIndexes(entry);
        }
        byWorldCache.remove(worldId);
        if (!removed.isEmpty()) {
            rebuildAllCache();
        }
        final List<AetheryteNetwork> removedNets = new ArrayList<>();
        networksById.values().removeIf(net -> {
            if (net.worldId().equals(worldId)) {
                removedNets.add(net);
                return true;
            }
            return false;
        });
        for (final AetheryteNetwork net : removedNets) {
            removeNetworkFromIndexes(net);
            rebuildNetworkHubCache(net.hubAetheryteId());
        }
        networksByWorldCache.remove(worldId);
        if (!removedNets.isEmpty()) {
            rebuildAllNetworksCache();
        }
    }

    /**
     * Registers a new Aetheryte in memory and persists the updated list to the world's PDC.
     */
    public synchronized void register(final World world, final Aetheryte aetheryte) {
        loadedWorlds.add(world.getUID());
        final Aetheryte existing = indexEntry(aetheryte);
        if (existing != null && !existing.worldId().equals(world.getUID())) {
            rebuildWorldCache(existing.worldId());
        }
        rebuildWorldCache(world.getUID());
        rebuildAllCache();
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
        loadedWorlds.add(world.getUID());
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
        final Aetheryte existing = indexEntry(aetheryte);
        if (existing != null && !existing.worldId().equals(world.getUID())) {
            rebuildWorldCache(existing.worldId());
        }
        rebuildWorldCache(world.getUID());
        rebuildAllCache();
        saveWorld(world);
        return RegistrationOutcome.REGISTERED;
    }

    /**
     * Atomically verifies Hub validity and per-player network limits before creating and persisting a new
     * Hub-attached {@link AetheryteNetwork} (#27).
     */
    public synchronized RegistrationOutcome tryCreateNetwork(
            final World world,
            final AetheryteNetwork network,
            final int maxGlobalNetworks,
            final int maxVariationNetworks
    ) {
        final UUID worldId = world.getUID();
        loadedWorlds.add(worldId);
        final @Nullable UUID hubId = network.hubAetheryteId();
        final @Nullable Aetheryte hub = hubId != null ? byId.get(hubId) : null;
        if (hub == null || !hub.worldId().equals(worldId) || !network.worldId().equals(worldId) || hub.networkId() != null) {
            return RegistrationOutcome.NETWORK_HUB_REQUIRED;
        }
        if (maxGlobalNetworks >= 0 && countNetworksByOwner(network.ownerId()) >= maxGlobalNetworks) {
            return RegistrationOutcome.GLOBAL_NETWORK_LIMIT_REACHED;
        }
        if (maxVariationNetworks >= 0
                && countNetworksByOwnerAndVariation(network.ownerId(), hub.variation()) >= maxVariationNetworks) {
            return RegistrationOutcome.VARIATION_NETWORK_LIMIT_REACHED;
        }
        final AetheryteNetwork previousNet = indexNetworkEntry(network);
        if (previousNet != null && !previousNet.worldId().equals(worldId)) {
            rebuildNetworkWorldCache(previousNet.worldId());
        }
        if (previousNet != null && !Objects.equals(previousNet.hubAetheryteId(), network.hubAetheryteId())) {
            rebuildNetworkHubCache(previousNet.hubAetheryteId());
        }
        rebuildNetworkWorldCache(worldId);
        rebuildNetworkHubCache(network.hubAetheryteId());
        rebuildAllNetworksCache();
        saveWorld(world);
        return RegistrationOutcome.REGISTERED;
    }

    /**
     * Atomically verifies Aetheryte and network limits/distance and registers a network Aetheryte (creating a new
     * {@link AetheryteNetwork} when {@code newNetwork != null}, or joining an existing network otherwise) (#27).
     */
    public synchronized RegistrationOutcome tryRegisterInNetwork(
            final World world,
            final Aetheryte aetheryte,
            final @Nullable AetheryteNetwork newNetwork,
            final int maxGlobal,
            final int maxVariation,
            final double minDistance,
            final int maxGlobalNetworks,
            final int maxVariationNetworks,
            final int maxNetworkAetherytes,
            final double maxNetworkDistance
    ) {
        final UUID worldId = world.getUID();
        loadedWorlds.add(worldId);
        final BlockPosKey posKey = aetheryte.posKey();
        if (byPos.containsKey(posKey)) {
            return RegistrationOutcome.ALREADY_EXISTS;
        }
        if (minDistance > 0.0 && findWithinDistance(worldId, aetheryte.blockX(), aetheryte.blockY(), aetheryte.blockZ(), minDistance).isPresent()) {
            return RegistrationOutcome.TOO_CLOSE;
        }
        if (maxGlobal >= 0 && countByOwner(aetheryte.ownerId()) >= maxGlobal) {
            return RegistrationOutcome.GLOBAL_LIMIT_REACHED;
        }
        if (maxVariation >= 0 && countByOwnerAndVariation(aetheryte.ownerId(), aetheryte.variation()) >= maxVariation) {
            return RegistrationOutcome.VARIATION_LIMIT_REACHED;
        }
        final UUID targetNetworkId = aetheryte.networkId();
        if (targetNetworkId == null) {
            return RegistrationOutcome.NETWORK_NOT_FOUND;
        }
        if (newNetwork != null) {
            if (!newNetwork.id().equals(targetNetworkId) || !newNetwork.worldId().equals(worldId)) {
                return RegistrationOutcome.NETWORK_NOT_FOUND;
            }
            if (maxGlobalNetworks >= 0 && countNetworksByOwner(newNetwork.ownerId()) >= maxGlobalNetworks) {
                return RegistrationOutcome.GLOBAL_NETWORK_LIMIT_REACHED;
            }
            if (maxVariationNetworks >= 0
                    && countNetworksByOwnerAndVariation(newNetwork.ownerId(), aetheryte.variation()) >= maxVariationNetworks) {
                return RegistrationOutcome.VARIATION_NETWORK_LIMIT_REACHED;
            }
            if (maxNetworkAetherytes == 0) {
                return RegistrationOutcome.NETWORK_AETHERYTE_LIMIT_REACHED;
            }
            final AetheryteNetwork previousNet = indexNetworkEntry(newNetwork);
            if (previousNet != null && !previousNet.worldId().equals(worldId)) {
                rebuildNetworkWorldCache(previousNet.worldId());
            }
            if (previousNet != null && !Objects.equals(previousNet.hubAetheryteId(), newNetwork.hubAetheryteId())) {
                rebuildNetworkHubCache(previousNet.hubAetheryteId());
            }
            rebuildNetworkWorldCache(worldId);
            rebuildNetworkHubCache(newNetwork.hubAetheryteId());
            rebuildAllNetworksCache();
        } else {
            final AetheryteNetwork existingNet = networksById.get(targetNetworkId);
            if (existingNet == null || !existingNet.worldId().equals(worldId)) {
                return RegistrationOutcome.NETWORK_NOT_FOUND;
            }
            final @Nullable UUID hubId = existingNet.hubAetheryteId();
            final @Nullable Aetheryte hub = hubId != null ? byId.get(hubId) : null;
            if (hub == null || !hub.worldId().equals(worldId) || hub.networkId() != null) {
                return RegistrationOutcome.NETWORK_HUB_REQUIRED;
            }
            if (maxNetworkAetherytes >= 0 && countAetherytesInNetwork(targetNetworkId) >= maxNetworkAetherytes) {
                return RegistrationOutcome.NETWORK_AETHERYTE_LIMIT_REACHED;
            }
            if (maxNetworkDistance > 0.0 && !isWithinNetworkDistance(
                    targetNetworkId, worldId, aetheryte.blockX(), aetheryte.blockY(), aetheryte.blockZ(), maxNetworkDistance)) {
                return RegistrationOutcome.NETWORK_TOO_FAR;
            }
        }
        final Aetheryte existing = indexEntry(aetheryte);
        if (existing != null && !existing.worldId().equals(worldId)) {
            rebuildWorldCache(existing.worldId());
        }
        rebuildWorldCache(worldId);
        rebuildAllCache();
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
     * Atomically updates an existing Aetheryte in memory and persists the updated list to the world's PDC (#53, #57).
     */
    public synchronized Optional<Aetheryte> update(
            final World world,
            final UUID id,
            final UnaryOperator<Aetheryte> updater
    ) {
        final Aetheryte existing = byId.get(id);
        if (existing == null) {
            return Optional.empty();
        }
        final Aetheryte updated = updater.apply(existing);
        indexEntry(updated);
        if (!existing.worldId().equals(updated.worldId())) {
            rebuildWorldCache(existing.worldId());
        }
        rebuildWorldCache(updated.worldId());
        if (!world.getUID().equals(updated.worldId())) {
            rebuildWorldCache(world.getUID());
        }
        rebuildAllCache();
        saveWorld(world);
        return Optional.of(updated);
    }

    /**
     * Unregisters an Aetheryte from memory and updates the world's PDC.
     * Hub-attached networks persist when their last child is removed; removing a Hub with zero child
     * Aetherytes also removes any empty networks attached to it.
     */
    public synchronized Optional<Aetheryte> unregister(final World world, final UUID id) {
        final Aetheryte removed = byId.remove(id);
        if (removed == null) {
            return Optional.empty();
        }
        removeFromIndexes(removed);
        final UUID networkId = removed.networkId();
        boolean networksChanged = false;
        final Set<UUID> touchedNetWorlds = new HashSet<>();
        if (networkId != null && countAetherytesInNetwork(networkId) == 0) {
            final AetheryteNetwork net = networksById.get(networkId);
            if (net != null && net.hubAetheryteId() == null) {
                networksById.remove(networkId);
                removeNetworkFromIndexes(net);
                touchedNetWorlds.add(world.getUID());
                touchedNetWorlds.add(net.worldId());
                networksChanged = true;
            }
        } else if (networkId == null) {
            final List<AetheryteNetwork> hosted = List.copyOf(getNetworksByHub(removed.id()));
            for (final AetheryteNetwork net : hosted) {
                if (countAetherytesInNetwork(net.id()) == 0) {
                    networksById.remove(net.id());
                    removeNetworkFromIndexes(net);
                    touchedNetWorlds.add(world.getUID());
                    touchedNetWorlds.add(net.worldId());
                    networksChanged = true;
                }
            }
            if (networksChanged) {
                rebuildNetworkHubCache(removed.id());
            }
        }
        if (networksChanged) {
            for (final UUID netWorldId : touchedNetWorlds) {
                rebuildNetworkWorldCache(netWorldId);
            }
            rebuildAllNetworksCache();
        }
        rebuildWorldCache(world.getUID());
        if (!removed.worldId().equals(world.getUID())) {
            rebuildWorldCache(removed.worldId());
        }
        rebuildAllCache();
        saveWorld(world);
        return Optional.of(removed);
    }

    /**
     * Atomically unregisters a Hub Aetheryte together with all hosted {@link AetheryteNetwork}s and all child
     * Aetherytes belonging to those networks, persisting the updated world state once (#27).
     */
    public synchronized Optional<HubCascadeRemoval> unregisterHubCascade(final World world, final UUID hubId) {
        final Aetheryte hub = byId.remove(hubId);
        if (hub == null) {
            return Optional.empty();
        }
        removeFromIndexes(hub);
        final List<AetheryteNetwork> hostedNetworks = List.copyOf(getNetworksByHub(hubId));
        final Set<UUID> hostedNetIds = new HashSet<>(hostedNetworks.size());
        for (final AetheryteNetwork net : hostedNetworks) {
            hostedNetIds.add(net.id());
        }

        final Set<UUID> touchedWorlds = new HashSet<>();
        touchedWorlds.add(world.getUID());
        touchedWorlds.add(hub.worldId());

        final List<Aetheryte> removedChildren = new ArrayList<>();
        if (!hostedNetIds.isEmpty()) {
            for (final Aetheryte entry : List.copyOf(byId.values())) {
                if (entry.networkId() != null && hostedNetIds.contains(entry.networkId())) {
                    if (byId.remove(entry.id(), entry)) {
                        removeFromIndexes(entry);
                        removedChildren.add(entry);
                        touchedWorlds.add(entry.worldId());
                    }
                }
            }
            for (final AetheryteNetwork net : hostedNetworks) {
                if (networksById.remove(net.id(), net)) {
                    removeNetworkFromIndexes(net);
                    touchedWorlds.add(net.worldId());
                }
            }
            rebuildNetworkHubCache(hubId);
        }

        for (final UUID touchedWorldId : touchedWorlds) {
            rebuildWorldCache(touchedWorldId);
            rebuildNetworkWorldCache(touchedWorldId);
        }
        rebuildAllCache();
        rebuildAllNetworksCache();
        saveWorld(world);
        return Optional.of(new HubCascadeRemoval(hub, hostedNetworks, List.copyOf(removedChildren)));
    }

    /**
     * Persists all in-memory Aetherytes and networks for the given world into its {@link PersistentDataContainer}.
     */
    public synchronized void saveWorld(final World world) {
        final List<Aetheryte> worldEntries = getByWorld(world.getUID());
        final PersistentDataContainer pdc = world.getPersistentDataContainer();
        if (worldEntries.isEmpty()) {
            pdc.remove(WORLD_AETHERYTES_KEY);
        } else {
            pdc.set(WORLD_AETHERYTES_KEY, LIST_TYPE, worldEntries);
        }
        final List<AetheryteNetwork> worldNetworks = getNetworksByWorld(world.getUID());
        if (worldNetworks.isEmpty()) {
            pdc.remove(WORLD_NETWORKS_KEY);
        } else {
            pdc.set(WORLD_NETWORKS_KEY, NETWORK_LIST_TYPE, worldNetworks);
        }
    }

    /**
     * Registers or replaces an {@link AetheryteNetwork} in memory and persists it to the world's PDC (#27).
     */
    public synchronized void registerNetwork(final World world, final AetheryteNetwork network) {
        loadedWorlds.add(world.getUID());
        final AetheryteNetwork previous = indexNetworkEntry(network);
        if (previous != null && !previous.worldId().equals(world.getUID())) {
            rebuildNetworkWorldCache(previous.worldId());
        }
        if (previous != null && !Objects.equals(previous.hubAetheryteId(), network.hubAetheryteId())) {
            rebuildNetworkHubCache(previous.hubAetheryteId());
        }
        rebuildNetworkWorldCache(world.getUID());
        rebuildNetworkHubCache(network.hubAetheryteId());
        rebuildAllNetworksCache();
        saveWorld(world);
    }

    /**
     * Atomically updates an existing {@link AetheryteNetwork} in memory and persists to the world's PDC (#27).
     */
    public synchronized Optional<AetheryteNetwork> updateNetwork(
            final World world,
            final UUID networkId,
            final UnaryOperator<AetheryteNetwork> updater
    ) {
        final AetheryteNetwork existing = networksById.get(networkId);
        if (existing == null) {
            return Optional.empty();
        }
        final AetheryteNetwork updated = updater.apply(existing);
        indexNetworkEntry(updated);
        if (!existing.worldId().equals(updated.worldId())) {
            rebuildNetworkWorldCache(existing.worldId());
        }
        if (!Objects.equals(existing.hubAetheryteId(), updated.hubAetheryteId())) {
            rebuildNetworkHubCache(existing.hubAetheryteId());
        }
        rebuildNetworkWorldCache(updated.worldId());
        if (!world.getUID().equals(updated.worldId())) {
            rebuildNetworkWorldCache(world.getUID());
        }
        rebuildNetworkHubCache(updated.hubAetheryteId());
        rebuildAllNetworksCache();
        saveWorld(world);
        return Optional.of(updated);
    }

    /**
     * Unregisters an {@link AetheryteNetwork} from memory and updates the world's PDC (#27).
     */
    public synchronized Optional<AetheryteNetwork> unregisterNetwork(final World world, final UUID networkId) {
        final AetheryteNetwork removed = networksById.remove(networkId);
        if (removed == null) {
            return Optional.empty();
        }
        removeNetworkFromIndexes(removed);
        rebuildNetworkWorldCache(world.getUID());
        if (!removed.worldId().equals(world.getUID())) {
            rebuildNetworkWorldCache(removed.worldId());
        }
        rebuildNetworkHubCache(removed.hubAetheryteId());
        rebuildAllNetworksCache();
        saveWorld(world);
        return Optional.of(removed);
    }

    public Optional<AetheryteNetwork> getNetworkById(final UUID networkId) {
        return Optional.ofNullable(networksById.get(networkId));
    }

    public List<AetheryteNetwork> getNetworksByWorld(final UUID worldId) {
        return networksByWorldCache.getOrDefault(worldId, List.of());
    }

    /**
     * Returns all {@link AetheryteNetwork}s hosted on the given Hub Aetheryte ID (#27).
     */
    public List<AetheryteNetwork> getNetworksByHub(final UUID hubAetheryteId) {
        return networksByHubCache.getOrDefault(hubAetheryteId, List.of());
    }

    /**
     * Returns all child Aetherytes belonging to {@code networkId} (#27).
     */
    public List<Aetheryte> getChildAetherytesInNetwork(final UUID networkId) {
        final @Nullable AetheryteNetwork net = networksById.get(networkId);
        final List<Aetheryte> candidates = net != null ? getByWorld(net.worldId()) : allCache;
        return candidates.stream()
                .filter(a -> networkId.equals(a.networkId()))
                .toList();
    }

    /**
     * Returns all child Aetherytes across all networks hosted by {@code hubAetheryteId} (#27).
     */
    public List<Aetheryte> getChildAetherytesByHub(final UUID hubAetheryteId) {
        final List<AetheryteNetwork> hosted = getNetworksByHub(hubAetheryteId);
        if (hosted.isEmpty()) {
            return List.of();
        }
        final Set<UUID> netIds = new HashSet<>(hosted.size());
        for (final AetheryteNetwork net : hosted) {
            netIds.add(net.id());
        }
        final @Nullable Aetheryte hub = byId.get(hubAetheryteId);
        final List<Aetheryte> candidates = hub != null ? getByWorld(hub.worldId()) : allCache;
        return candidates.stream()
                .filter(a -> a.networkId() != null && netIds.contains(a.networkId()))
                .toList();
    }

    /**
     * Counts all loaded child Aetherytes across all networks hosted by {@code hubAetheryteId} (#27).
     */
    public int countChildAetherytesByHub(final UUID hubAetheryteId) {
        int total = 0;
        for (final AetheryteNetwork net : getNetworksByHub(hubAetheryteId)) {
            total += countAetherytesInNetwork(net.id());
        }
        return total;
    }

    public List<AetheryteNetwork> getAllNetworks() {
        return allNetworksCache;
    }

    public int countNetworksByOwner(final UUID ownerId) {
        return ownerNetworkCounts.getOrDefault(ownerId, 0);
    }

    public int countNetworksByOwnerAndVariation(final UUID ownerId, final String variation) {
        final String normalized = Aetheryte.normalizeVariation(variation);
        int count = 0;
        for (final AetheryteNetwork net : networksById.values()) {
            if (!net.ownerId().equals(ownerId)) {
                continue;
            }
            final @Nullable Aetheryte hub = net.hubAetheryteId() != null ? byId.get(net.hubAetheryteId()) : null;
            if (hub != null) {
                if (hub.variation().equals(normalized)) {
                    count++;
                }
            } else {
                final boolean matchesChild = getByWorld(net.worldId()).stream()
                        .anyMatch(a -> net.id().equals(a.networkId()) && a.variation().equals(normalized));
                if (matchesChild) {
                    count++;
                }
            }
        }
        return count;
    }

    public int countAetherytesInNetwork(final UUID networkId) {
        return networkMemberCounts.getOrDefault(networkId, 0);
    }

    /**
     * Checks whether the coordinates {@code (x, y, z)} in {@code worldId} are within {@code maxDistance} blocks
     * (inclusive) of any existing Aetheryte or Hub in {@code networkId}. Returns {@code true} if the network has no
     * loaded members or Hub in {@code worldId}.
     */
    public boolean isWithinNetworkDistance(
            final UUID networkId,
            final UUID worldId,
            final int x,
            final int y,
            final int z,
            final double maxDistance
    ) {
        if (maxDistance <= 0.0) {
            return true;
        }
        final double maxDistSq = maxDistance * maxDistance;
        boolean foundAnyAnchor = false;
        final @Nullable AetheryteNetwork net = networksById.get(networkId);
        if (net != null && net.hubAetheryteId() != null) {
            final @Nullable Aetheryte hub = byId.get(net.hubAetheryteId());
            if (hub != null && hub.worldId().equals(worldId)) {
                foundAnyAnchor = true;
                final double dx = hub.blockX() - x;
                final double dy = hub.blockY() - y;
                final double dz = hub.blockZ() - z;
                if ((dx * dx + dy * dy + dz * dz) <= maxDistSq) {
                    return true;
                }
            }
        }
        for (final Aetheryte member : getByWorld(worldId)) {
            if (!networkId.equals(member.networkId())) {
                continue;
            }
            foundAnyAnchor = true;
            final double dx = member.blockX() - x;
            final double dy = member.blockY() - y;
            final double dz = member.blockZ() - z;
            if ((dx * dx + dy * dy + dz * dz) <= maxDistSq) {
                return true;
            }
        }
        return !foundAnyAnchor;
    }

    /**
     * Counts the total number of loaded Aetherytes owned by the specified player (#43).
     */
    public int countByOwner(final UUID ownerId) {
        return ownerCounts.getOrDefault(ownerId, 0);
    }

    /**
     * Counts the number of loaded Aetherytes of a specific variation owned by the specified player (#43).
     */
    public int countByOwnerAndVariation(final UUID ownerId, final String variation) {
        final String normalized = Aetheryte.normalizeVariation(variation);
        return ownerVariationCounts.getOrDefault(new OwnerVariationKey(ownerId, normalized), 0);
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
     * Returns all Aetherytes registered in the specified world, ordered deterministically, in O(1) time via cache.
     */
    public List<Aetheryte> getByWorld(final UUID worldId) {
        return byWorldCache.getOrDefault(worldId, List.of());
    }

    /**
     * Returns all registered Aetherytes across all loaded worlds, ordered deterministically, in O(1) time via cache.
     */
    public List<Aetheryte> getAll() {
        return allCache;
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
        final List<Aetheryte> worldEntries = getByWorld(worldId);
        if (worldEntries.isEmpty()) {
            return Optional.empty();
        }
        final double distSq = distance * distance;
        final int span = (int) Math.ceil(distance);
        final int minCX = (x - span) >> 4;
        final int maxCX = (x + span) >> 4;
        final int minCZ = (z - span) >> 4;
        final int maxCZ = (z + span) >> 4;
        final long chunkCount = (long) (maxCX - minCX + 1) * (long) (maxCZ - minCZ + 1);

        if (chunkCount <= worldEntries.size()) {
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    final List<Aetheryte> chunkList = byChunk.get(new ChunkPosKey(worldId, cx, cz));
                    if (chunkList == null) {
                        continue;
                    }
                    for (final Aetheryte aetheryte : chunkList) {
                        if (isWithinDistanceSq(aetheryte, x, y, z, distSq)) {
                            return Optional.of(aetheryte);
                        }
                    }
                }
            }
            return Optional.empty();
        }

        for (final Aetheryte aetheryte : worldEntries) {
            if (isWithinDistanceSq(aetheryte, x, y, z, distSq)) {
                return Optional.of(aetheryte);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns whether the specified world is currently loaded in this registry.
     */
    public boolean isWorldLoaded(final UUID worldId) {
        return loadedWorlds.contains(worldId);
    }

    /**
     * Returns whether at least one world has been loaded or registered in this registry.
     */
    public boolean hasLoadedWorlds() {
        return !loadedWorlds.isEmpty();
    }

    /**
     * Clears the in-memory index without modifying world PDC data.
     */
    public synchronized void clear() {
        loadedWorlds.clear();
        byId.clear();
        byPos.clear();
        byChunk.clear();
        byWorldCache.clear();
        ownerCounts.clear();
        ownerVariationCounts.clear();
        networkMemberCounts.clear();
        networksById.clear();
        networksByWorldCache.clear();
        networksByHubCache.clear();
        ownerNetworkCounts.clear();
        allCache = List.of();
        allNetworksCache = List.of();
    }

    private @Nullable Aetheryte indexEntry(final Aetheryte aetheryte) {
        final Aetheryte existing = byId.put(aetheryte.id(), aetheryte);
        if (existing != null) {
            removeFromIndexes(existing);
        }
        byPos.put(aetheryte.posKey(), aetheryte);
        final ChunkPosKey chunkKey = new ChunkPosKey(aetheryte.worldId(), aetheryte.blockX() >> 4, aetheryte.blockZ() >> 4);
        byChunk.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(aetheryte);
        ownerCounts.merge(aetheryte.ownerId(), 1, Integer::sum);
        ownerVariationCounts.merge(new OwnerVariationKey(aetheryte.ownerId(), aetheryte.variation()), 1, Integer::sum);
        if (aetheryte.networkId() != null) {
            networkMemberCounts.merge(aetheryte.networkId(), 1, Integer::sum);
        }
        return existing;
    }

    private void removeFromIndexes(final Aetheryte existing) {
        byPos.remove(existing.posKey(), existing);
        final ChunkPosKey oldChunkKey = new ChunkPosKey(existing.worldId(), existing.blockX() >> 4, existing.blockZ() >> 4);
        byChunk.computeIfPresent(oldChunkKey, (k, list) -> {
            list.removeIf(a -> a.id().equals(existing.id()));
            return list.isEmpty() ? null : list;
        });
        ownerCounts.computeIfPresent(existing.ownerId(), (k, count) -> count > 1 ? count - 1 : null);
        ownerVariationCounts.computeIfPresent(
                new OwnerVariationKey(existing.ownerId(), existing.variation()),
                (k, count) -> count > 1 ? count - 1 : null
        );
        if (existing.networkId() != null) {
            networkMemberCounts.computeIfPresent(existing.networkId(), (k, count) -> count > 1 ? count - 1 : null);
        }
    }

    private @Nullable AetheryteNetwork indexNetworkEntry(final AetheryteNetwork network) {
        final AetheryteNetwork existing = networksById.put(network.id(), network);
        if (existing != null) {
            removeNetworkFromIndexes(existing);
        }
        ownerNetworkCounts.merge(network.ownerId(), 1, Integer::sum);
        return existing;
    }

    private void removeNetworkFromIndexes(final AetheryteNetwork existing) {
        ownerNetworkCounts.computeIfPresent(existing.ownerId(), (k, count) -> count > 1 ? count - 1 : null);
    }

    private void rebuildNetworkWorldCache(final UUID worldId) {
        final List<AetheryteNetwork> sorted = networksById.values().stream()
                .filter(n -> n.worldId().equals(worldId))
                .sorted(NETWORK_ORDER)
                .toList();
        if (sorted.isEmpty()) {
            networksByWorldCache.remove(worldId);
        } else {
            networksByWorldCache.put(worldId, sorted);
        }
    }

    private void rebuildNetworkHubCache(final @Nullable UUID hubId) {
        if (hubId == null) {
            return;
        }
        final List<AetheryteNetwork> sorted = networksById.values().stream()
                .filter(n -> hubId.equals(n.hubAetheryteId()))
                .sorted(NETWORK_ORDER)
                .toList();
        if (sorted.isEmpty()) {
            networksByHubCache.remove(hubId);
        } else {
            networksByHubCache.put(hubId, sorted);
        }
    }

    private void rebuildAllNetworksCache() {
        if (networksById.isEmpty()) {
            allNetworksCache = List.of();
            return;
        }
        allNetworksCache = networksById.values().stream()
                .sorted(NETWORK_ORDER)
                .toList();
    }

    private void rebuildWorldCache(final UUID worldId) {
        final List<Aetheryte> sorted = byId.values().stream()
                .filter(a -> a.worldId().equals(worldId))
                .sorted(DEFAULT_ORDER)
                .toList();
        if (sorted.isEmpty()) {
            byWorldCache.remove(worldId);
        } else {
            byWorldCache.put(worldId, sorted);
        }
    }

    private void rebuildAllCache() {
        if (byId.isEmpty()) {
            allCache = List.of();
            return;
        }
        allCache = byId.values().stream()
                .sorted(DEFAULT_ORDER)
                .toList();
    }

    private static boolean isWithinDistanceSq(
            final Aetheryte aetheryte,
            final int x,
            final int y,
            final int z,
            final double distSq
    ) {
        final double dx = aetheryte.blockX() - x;
        final double dy = aetheryte.blockY() - y;
        final double dz = aetheryte.blockZ() - z;
        return (dx * dx + dy * dy + dz * dz) < distSq;
    }
}
