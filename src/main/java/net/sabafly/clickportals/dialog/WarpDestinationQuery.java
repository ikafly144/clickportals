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
package net.sabafly.clickportals.dialog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteNetwork;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * Pure grouping, filtering, sorting, and pagination logic for the hierarchical warp Dialog (#54, #55, #56).
 */
public final class WarpDestinationQuery {

    /**
     * Default number of destination entries rendered per list page.
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private WarpDestinationQuery() {
    }

    /**
     * Checks whether warps can route between {@code source} and {@code destination} (#27).
     * Standard Aetherytes ({@code networkId == null}) only route to standard Aetherytes; network Aetherytes only
     * route within the exact same {@code networkId}.
     */
    public static boolean canRouteBetweenAetherytes(
            final @Nullable Aetheryte source,
            final Aetheryte destination
    ) {
        return canRouteBetweenAetherytes(source, destination, id -> null);
    }

    /**
     * Checks whether warps can route between {@code source} and {@code destination} with Hub-child awareness (#27):
     * <ul>
     *   <li>{@code normal <-> normal}: always routable (cross-dimension rules apply separately)</li>
     *   <li>{@code normal} Hub {@code <->} child in a network hosted by that Hub: routable</li>
     *   <li>child {@code <->} child in the same {@code networkId}: routable</li>
     * </ul>
     */
    public static boolean canRouteBetweenAetherytes(
            final @Nullable Aetheryte source,
            final Aetheryte destination,
            final Function<UUID, @Nullable AetheryteNetwork> networkLookup
    ) {
        if (source == null) {
            return true;
        }
        final @Nullable UUID srcNetId = source.networkId();
        final @Nullable UUID dstNetId = destination.networkId();
        if (srcNetId == null && dstNetId == null) {
            return true;
        }
        if (srcNetId != null && dstNetId != null) {
            return srcNetId.equals(dstNetId);
        }
        if (dstNetId != null) {
            final AetheryteNetwork dstNet = networkLookup.apply(dstNetId);
            return dstNet != null && source.id().equals(dstNet.hubAetheryteId());
        }
        if (srcNetId == null) {
            return false;
        }
        final AetheryteNetwork srcNet = networkLookup.apply(srcNetId);
        return srcNet != null && destination.id().equals(srcNet.hubAetheryteId());
    }

    /**
     * Summary of a Hub-hosted network category containing reachable child urban Aetherytes (#87).
     */
    public record NetworkGroup(
            UUID networkId,
            String networkName,
            int destinationCount
    ) {
    }

    /**
     * Summary of a reachable destination world shown on the root menu (#54).
     */
    public record WorldGroup(
            UUID worldId,
            String worldName,
            String displayName,
            World.Environment environment,
            int destinationCount,
            boolean currentWorld
    ) {
        public WorldGroup(
                final UUID worldId,
                final String worldName,
                final World.Environment environment,
                final int destinationCount,
                final boolean currentWorld
        ) {
            this(worldId, worldName, worldName, environment, destinationCount, currentWorld);
        }
    }

    /**
     * Resolved candidate entry prior to cost evaluation on the active page slice.
     */
    public record Candidate(
            Aetheryte aetheryte,
            String ownerName,
            boolean favorite,
            OptionalDouble sameWorldDistance
    ) {
    }

    /**
     * Paginated slice result with clamped 0-based page index.
     */
    public record PageSlice<T>(
            List<T> items,
            int page,
            int totalPages,
            int totalItems
    ) {
        public boolean hasPrevious() {
            return page > 0;
        }

        public boolean hasNext() {
            return page + 1 < totalPages;
        }
    }

    /**
     * Extracts Hub-hosted network categories when {@code current} is a Hub hosting networks with at least one
     * reachable child Aetheryte (#87). Returns an empty list for non-Hub Aetherytes or when 0 children are reachable.
     */
    public static List<NetworkGroup> groupHostedNetworks(
            final Aetheryte current,
            final List<Aetheryte> destinations,
            final List<AetheryteNetwork> hostedNetworks,
            final Predicate<Aetheryte> reachablePredicate
    ) {
        if (current.networkId() != null || hostedNetworks.isEmpty()) {
            return List.of();
        }
        final Map<UUID, AetheryteNetwork> networksById = new LinkedHashMap<>(hostedNetworks.size());
        final Map<UUID, Integer> countsByNetwork = new LinkedHashMap<>();
        for (final AetheryteNetwork net : hostedNetworks) {
            if (current.id().equals(net.hubAetheryteId())) {
                networksById.put(net.id(), net);
            }
        }
        if (networksById.isEmpty()) {
            return List.of();
        }
        for (final Aetheryte dest : destinations) {
            final @Nullable UUID destNetId = dest.networkId();
            if (destNetId == null || dest.id().equals(current.id()) || !networksById.containsKey(destNetId)) {
                continue;
            }
            if (reachablePredicate.test(dest)) {
                countsByNetwork.merge(destNetId, 1, Integer::sum);
            }
        }
        if (countsByNetwork.isEmpty()) {
            return List.of();
        }
        final List<NetworkGroup> groups = new ArrayList<>(countsByNetwork.size());
        for (final Map.Entry<UUID, Integer> entry : countsByNetwork.entrySet()) {
            final AetheryteNetwork net = networksById.get(entry.getKey());
            if (net != null && entry.getValue() > 0) {
                groups.add(new NetworkGroup(net.id(), net.name(), entry.getValue()));
            }
        }
        groups.sort(Comparator
                .comparing(NetworkGroup::networkName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(NetworkGroup::networkId));
        return List.copyOf(groups);
    }

    /**
     * Groups reachable destinations by world, putting the current world first, then ordering by environment and name (#54).
     */
    public static List<WorldGroup> groupReachableWorlds(
            final Aetheryte current,
            final List<Aetheryte> destinations,
            final Predicate<Aetheryte> reachablePredicate,
            final Function<UUID, World.@Nullable Environment> environmentLookup
    ) {
        return groupReachableWorlds(current, destinations, reachablePredicate, environmentLookup, Function.identity(), Set.of());
    }

    public static List<WorldGroup> groupReachableWorlds(
            final Aetheryte current,
            final List<Aetheryte> destinations,
            final Predicate<Aetheryte> reachablePredicate,
            final Function<UUID, World.@Nullable Environment> environmentLookup,
            final Function<String, String> worldDisplayNameResolver
    ) {
        return groupReachableWorlds(current, destinations, reachablePredicate, environmentLookup, worldDisplayNameResolver, Set.of());
    }

    /**
     * Groups reachable destinations by world while excluding any {@code excludedNetworkIds} already separated into
     * dedicated Hub network categories (#54, #87).
     */
    public static List<WorldGroup> groupReachableWorlds(
            final Aetheryte current,
            final List<Aetheryte> destinations,
            final Predicate<Aetheryte> reachablePredicate,
            final Function<UUID, World.@Nullable Environment> environmentLookup,
            final Function<String, String> worldDisplayNameResolver,
            final Set<UUID> excludedNetworkIds
    ) {
        final Map<UUID, String> namesByWorld = new LinkedHashMap<>();
        final Map<UUID, Integer> countsByWorld = new LinkedHashMap<>();
        for (final Aetheryte dest : destinations) {
            if (dest.id().equals(current.id())) {
                continue;
            }
            if (dest.networkId() != null && excludedNetworkIds.contains(dest.networkId())) {
                continue;
            }
            if (!reachablePredicate.test(dest)) {
                continue;
            }
            namesByWorld.putIfAbsent(dest.worldId(), dest.worldName());
            countsByWorld.merge(dest.worldId(), 1, Integer::sum);
        }

        final List<WorldGroup> groups = new ArrayList<>(countsByWorld.size());
        for (final Map.Entry<UUID, Integer> entry : countsByWorld.entrySet()) {
            final UUID worldId = entry.getKey();
            final String worldName = namesByWorld.getOrDefault(worldId, worldId.toString());
            final String displayName = worldDisplayNameResolver.apply(worldName);
            final World.Environment env = environmentLookup.apply(worldId);
            groups.add(new WorldGroup(
                    worldId,
                    worldName,
                    displayName != null && !displayName.isBlank() ? displayName : worldName,
                    env != null ? env : World.Environment.CUSTOM,
                    entry.getValue(),
                    worldId.equals(current.worldId())
            ));
        }

        groups.sort(Comparator
                .comparing(WorldGroup::currentWorld).reversed()
                .thenComparingInt(g -> environmentRank(g.environment()))
                .thenComparing(WorldGroup::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(groups);
    }

    private static int environmentRank(final World.Environment environment) {
        return switch (environment) {
            case NORMAL -> 0;
            case NETHER -> 1;
            case THE_END -> 2;
            case CUSTOM -> 3;
        };
    }

    /**
     * Filters and sorts warp destination candidates according to the active {@link WarpDialogState} (#54, #55, #56).
     */
    public static List<Candidate> queryCandidates(
            final Aetheryte current,
            final UUID viewerId,
            final List<Aetheryte> allDestinations,
            final WarpDialogState state,
            final Set<UUID> favorites,
            final Predicate<Aetheryte> reachablePredicate,
            final Function<Aetheryte, String> ownerNameResolver
    ) {
        return queryCandidates(
                current,
                viewerId,
                allDestinations,
                state,
                favorites,
                reachablePredicate,
                ownerNameResolver,
                Function.identity()
        );
    }

    public static List<Candidate> queryCandidates(
            final Aetheryte current,
            final UUID viewerId,
            final List<Aetheryte> allDestinations,
            final WarpDialogState state,
            final Set<UUID> favorites,
            final Predicate<Aetheryte> reachablePredicate,
            final Function<Aetheryte, String> ownerNameResolver,
            final Function<String, String> worldDisplayNameResolver
    ) {
        return queryCandidates(
                current,
                viewerId,
                allDestinations,
                state,
                favorites,
                reachablePredicate,
                ownerNameResolver,
                worldDisplayNameResolver,
                Set.of()
        );
    }

    /**
     * Filters and sorts warp destination candidates while separating Hub-hosted {@code excludedNetworkIds}
     * from {@link WarpDialogState.ViewMode#WORLD} and {@link WarpDialogState.ViewMode#ALL} (#54, #55, #56, #87).
     */
    public static List<Candidate> queryCandidates(
            final Aetheryte current,
            final UUID viewerId,
            final List<Aetheryte> allDestinations,
            final WarpDialogState state,
            final Set<UUID> favorites,
            final Predicate<Aetheryte> reachablePredicate,
            final Function<Aetheryte, String> ownerNameResolver,
            final Function<String, String> worldDisplayNameResolver,
            final Set<UUID> excludedNetworkIds
    ) {
        final String normalizedQuery = state.searchQuery().toLowerCase(Locale.ROOT);
        final boolean hasQuery = !normalizedQuery.isEmpty();
        final boolean includeWorldInSearch = state.mode() != WarpDialogState.ViewMode.WORLD
                && state.mode() != WarpDialogState.ViewMode.NETWORK;

        final List<Candidate> filtered = new ArrayList<>();
        for (final Aetheryte dest : allDestinations) {
            if (dest.id().equals(current.id())) {
                continue;
            }
            if (state.mode() == WarpDialogState.ViewMode.NETWORK
                    && (state.targetNetworkId() == null || !state.targetNetworkId().equals(dest.networkId()))) {
                continue;
            }
            if ((state.mode() == WarpDialogState.ViewMode.WORLD || state.mode() == WarpDialogState.ViewMode.ALL)
                    && dest.networkId() != null
                    && excludedNetworkIds.contains(dest.networkId())) {
                continue;
            }
            if (state.mode() == WarpDialogState.ViewMode.WORLD
                    && (state.targetWorldId() == null || !dest.worldId().equals(state.targetWorldId()))) {
                continue;
            }
            if (state.mode() == WarpDialogState.ViewMode.FAVORITES && !favorites.contains(dest.id())) {
                continue;
            }
            if (!reachablePredicate.test(dest)) {
                continue;
            }

            final String ownerName = ownerNameResolver.apply(dest);
            if (hasQuery && !matchesSearch(dest, ownerName, normalizedQuery, includeWorldInSearch, worldDisplayNameResolver)) {
                continue;
            }

            filtered.add(new Candidate(
                    dest,
                    ownerName,
                    favorites.contains(dest.id()),
                    current.distanceTo(dest)
            ));
        }

        filtered.sort(comparatorFor(state.sortOrder(), viewerId));
        return List.copyOf(filtered);
    }

    private static boolean matchesSearch(
            final Aetheryte dest,
            final String ownerName,
            final String lowerQuery,
            final boolean includeWorldName,
            final Function<String, String> worldDisplayNameResolver
    ) {
        if (dest.name().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        if (ownerName.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        if (!includeWorldName) {
            return false;
        }
        if (dest.worldName().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        final String displayName = worldDisplayNameResolver.apply(dest.worldName());
        return displayName != null && displayName.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    /**
     * Builds a deterministic comparator for the given {@link WarpSortOrder} (#55).
     */
    public static Comparator<Candidate> comparatorFor(final WarpSortOrder order, final UUID viewerId) {
        return switch (order) {
            case DISTANCE -> Comparator
                    .comparing((Candidate c) -> c.sameWorldDistance().isEmpty())
                    .thenComparingDouble(c -> c.sameWorldDistance().orElse(Double.MAX_VALUE))
                    .thenComparing(c -> c.aetheryte().worldName(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(c -> c.aetheryte().name(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(c -> c.aetheryte().id());
            case NAME -> Comparator
                    .comparing((Candidate c) -> c.aetheryte().name(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(c -> c.aetheryte().id());
            case OWNER -> Comparator
                    .comparing((Candidate c) -> !c.aetheryte().ownerId().equals(viewerId))
                    .thenComparing(Candidate::ownerName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(c -> c.aetheryte().name(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(c -> c.aetheryte().id());
            case CREATED -> Comparator
                    .comparingLong((Candidate c) -> c.aetheryte().createdAt())
                    .thenComparing(c -> c.aetheryte().name(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(c -> c.aetheryte().id());
        };
    }

    /**
     * Slices {@code items} into a clamped page of size {@code pageSize} (#54).
     */
    public static <T> PageSlice<T> paginate(final List<T> items, final int requestedPage, final int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        if (items.isEmpty()) {
            return new PageSlice<>(List.of(), 0, 1, 0);
        }
        final int totalPages = (items.size() + pageSize - 1) / pageSize;
        final int clampedPage = Math.max(0, Math.min(requestedPage, totalPages - 1));
        final int fromIndex = clampedPage * pageSize;
        final int toIndex = Math.min(fromIndex + pageSize, items.size());
        return new PageSlice<>(List.copyOf(items.subList(fromIndex, toIndex)), clampedPage, totalPages, items.size());
    }
}
