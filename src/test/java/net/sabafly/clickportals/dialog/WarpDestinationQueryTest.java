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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class WarpDestinationQueryTest {

    private static final UUID OVERWORLD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NETHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID END_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CUSTOM_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void sortOrderCyclesAndParsesWithSafeDefault() {
        assertThat(WarpSortOrder.DISTANCE.next()).isEqualTo(WarpSortOrder.NAME);
        assertThat(WarpSortOrder.NAME.next()).isEqualTo(WarpSortOrder.OWNER);
        assertThat(WarpSortOrder.OWNER.next()).isEqualTo(WarpSortOrder.CREATED);
        assertThat(WarpSortOrder.CREATED.next()).isEqualTo(WarpSortOrder.DISTANCE);

        assertThat(WarpSortOrder.fromId(null)).isEqualTo(WarpSortOrder.DISTANCE);
        assertThat(WarpSortOrder.fromId("unknown")).isEqualTo(WarpSortOrder.DISTANCE);
        assertThat(WarpSortOrder.fromId("OWNER")).isEqualTo(WarpSortOrder.OWNER);
        assertThat(WarpSortOrder.fromId("created")).isEqualTo(WarpSortOrder.CREATED);
    }

    @Test
    void dialogStateNormalizesAndResetsPageOnFilterAndModeTransitions() {
        final WarpDialogState root = WarpDialogState.root(WarpSortOrder.NAME)
                .withPage(-5);
        assertThat(root.mode()).isEqualTo(WarpDialogState.ViewMode.ROOT);
        assertThat(root.page()).isZero();
        assertThat(root.sortOrder()).isEqualTo(WarpSortOrder.NAME);

        final WarpDialogState world = root.toWorld(OVERWORLD_ID)
                .withPage(3)
                .withSearchQuery("  spawn  ");
        assertThat(world.mode()).isEqualTo(WarpDialogState.ViewMode.WORLD);
        assertThat(world.targetWorldId()).isEqualTo(OVERWORLD_ID);
        assertThat(world.searchQuery()).isEqualTo("spawn");
        assertThat(world.page()).isZero();

        final WarpDialogState sorted = world.withPage(2).withSortOrder(WarpSortOrder.OWNER);
        assertThat(sorted.sortOrder()).isEqualTo(WarpSortOrder.OWNER);
        assertThat(sorted.page()).isZero();

        final WarpDialogState editFav = sorted.toFavorites().withFavoriteEditMode(true);
        assertThat(editFav.mode()).isEqualTo(WarpDialogState.ViewMode.FAVORITES);
        assertThat(editFav.searchQuery()).isEmpty();
        assertThat(editFav.favoriteEditMode()).isTrue();

        assertThat(editFav.toAll().mode()).isEqualTo(WarpDialogState.ViewMode.ALL);
        assertThat(editFav.toOperations().mode()).isEqualTo(WarpDialogState.ViewMode.OPERATIONS);
    }

    @Test
    void groupReachableWorldsOrdersCurrentWorldFirstThenEnvironmentThenName() {
        final UUID viewerId = UUID.randomUUID();
        final Aetheryte current = aetheryte("origin", NETHER_ID, "world_nether", 0, 0, 0, viewerId, "Alice", 10L);
        final Aetheryte netherSibling = aetheryte("nether-hub", NETHER_ID, "world_nether", 50, 64, 50, viewerId, "Alice", 20L);
        final Aetheryte overworldA = aetheryte("alpha-base", OVERWORLD_ID, "alpha_world", 10, 64, 10, viewerId, "Alice", 30L);
        final Aetheryte customWorld = aetheryte("sky-island", CUSTOM_ID, "custom_sky", 10, 64, 10, viewerId, "Alice", 40L);
        final Aetheryte endBlocked = aetheryte("end-city", END_ID, "world_the_end", 10, 64, 10, viewerId, "Alice", 50L);

        final Map<UUID, World.Environment> envs = Map.of(
                OVERWORLD_ID, World.Environment.NORMAL,
                NETHER_ID, World.Environment.NETHER,
                END_ID, World.Environment.THE_END
        );

        final List<WarpDestinationQuery.WorldGroup> groups = WarpDestinationQuery.groupReachableWorlds(
                current,
                List.of(current, netherSibling, overworldA, customWorld, endBlocked),
                dest -> !dest.worldId().equals(END_ID),
                envs::get
        );

        assertThat(groups).extracting(WarpDestinationQuery.WorldGroup::worldId)
                .containsExactly(NETHER_ID, OVERWORLD_ID, CUSTOM_ID);
        assertThat(groups.get(0).currentWorld()).isTrue();
        assertThat(groups.get(0).destinationCount()).isEqualTo(1);
        assertThat(groups.get(2).environment()).isEqualTo(World.Environment.CUSTOM);
    }

    @Test
    void queryCandidatesFiltersByModeAndSearchAndAppliesAllSortOrders() {
        final UUID aliceId = UUID.randomUUID();
        final UUID bobId = UUID.randomUUID();
        final Aetheryte current = aetheryte("Origin", OVERWORLD_ID, "world", 0, 64, 0, aliceId, "Alice", 100L);
        final Aetheryte farOwnedByBob = aetheryte("Beta", OVERWORLD_ID, "world", 100, 64, 0, bobId, "Bob", 300L);
        final Aetheryte nearOwnedByCharlie = aetheryte("Gamma", OVERWORLD_ID, "world", 10, 64, 0, UUID.randomUUID(), "Charlie", 200L);
        final Aetheryte crossWorldOwnedByAlice = aetheryte("Alpha", NETHER_ID, "world_nether", 5, 64, 0, aliceId, "Alice", 400L);
        final List<Aetheryte> all = List.of(current, farOwnedByBob, nearOwnedByCharlie, crossWorldOwnedByAlice);

        final Function<Aetheryte, String> ownerResolver = a -> Objects.requireNonNullElse(a.ownerName(), "Unknown");

        // DISTANCE: same-world sorted by distance, then cross-world
        final List<WarpDestinationQuery.Candidate> byDistance = WarpDestinationQuery.queryCandidates(
                current,
                aliceId,
                all,
                WarpDialogState.root(WarpSortOrder.DISTANCE).toAll(),
                Set.of(farOwnedByBob.id()),
                dest -> true,
                ownerResolver
        );
        assertThat(byDistance).extracting(c -> c.aetheryte().name())
                .containsExactly("Gamma", "Beta", "Alpha");
        assertThat(byDistance.get(1).favorite()).isTrue();
        assertThat(byDistance.get(0).sameWorldDistance()).hasValue(10.0);
        assertThat(byDistance.get(2).sameWorldDistance()).isEmpty();

        // NAME: alphabetical regardless of world
        final List<WarpDestinationQuery.Candidate> byName = WarpDestinationQuery.queryCandidates(
                current,
                aliceId,
                all,
                WarpDialogState.root(WarpSortOrder.NAME).toAll(),
                Set.of(),
                dest -> true,
                ownerResolver
        );
        assertThat(byName).extracting(c -> c.aetheryte().name())
                .containsExactly("Alpha", "Beta", "Gamma");

        // OWNER: viewer's own first, then alphabetical by ownerName
        final List<WarpDestinationQuery.Candidate> byOwner = WarpDestinationQuery.queryCandidates(
                current,
                aliceId,
                all,
                WarpDialogState.root(WarpSortOrder.OWNER).toAll(),
                Set.of(),
                dest -> true,
                ownerResolver
        );
        assertThat(byOwner).extracting(c -> c.aetheryte().name())
                .containsExactly("Alpha", "Beta", "Gamma");

        // CREATED: ascending createdAt
        final List<WarpDestinationQuery.Candidate> byCreated = WarpDestinationQuery.queryCandidates(
                current,
                aliceId,
                all,
                WarpDialogState.root(WarpSortOrder.CREATED).toAll(),
                Set.of(),
                dest -> true,
                ownerResolver
        );
        assertThat(byCreated).extracting(c -> c.aetheryte().name())
                .containsExactly("Gamma", "Beta", "Alpha");

        // WORLD mode filters strictly to targetWorldId and ignores worldName during search
        final WarpDialogState worldSearchWorldName = WarpDialogState.root(WarpSortOrder.NAME)
                .toWorld(OVERWORLD_ID)
                .withSearchQuery("world");
        assertThat(WarpDestinationQuery.queryCandidates(
                current, aliceId, all, worldSearchWorldName, Set.of(), dest -> true, ownerResolver
        )).isEmpty();

        // ALL mode matches ownerName and worldName case-insensitively
        final WarpDialogState ownerSearch = WarpDialogState.root(WarpSortOrder.NAME)
                .toAll()
                .withSearchQuery("bob");
        assertThat(WarpDestinationQuery.queryCandidates(
                current, aliceId, all, ownerSearch, Set.of(), dest -> true, ownerResolver
        )).extracting(c -> c.aetheryte().name()).containsExactly("Beta");

        final WarpDialogState favOnly = WarpDialogState.root(WarpSortOrder.NAME)
                .toFavorites();
        assertThat(WarpDestinationQuery.queryCandidates(
                current, aliceId, all, favOnly, Set.of(crossWorldOwnedByAlice.id()), dest -> true, ownerResolver
        )).extracting(c -> c.aetheryte().name()).containsExactly("Alpha");
    }

    @Test
    void paginateClampsPageAndComputesFlags() {
        final WarpDestinationQuery.PageSlice<String> empty = WarpDestinationQuery.paginate(List.of(), 4, 10);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.page()).isZero();
        assertThat(empty.totalPages()).isEqualTo(1);
        assertThat(empty.hasPrevious()).isFalse();
        assertThat(empty.hasNext()).isFalse();

        final List<String> items = List.of("a", "b", "c", "d", "e");
        final WarpDestinationQuery.PageSlice<String> first = WarpDestinationQuery.paginate(items, -1, 2);
        assertThat(first.items()).containsExactly("a", "b");
        assertThat(first.page()).isZero();
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.hasPrevious()).isFalse();
        assertThat(first.hasNext()).isTrue();

        final WarpDestinationQuery.PageSlice<String> clampedLast = WarpDestinationQuery.paginate(items, 99, 2);
        assertThat(clampedLast.items()).containsExactly("e");
        assertThat(clampedLast.page()).isEqualTo(2);
        assertThat(clampedLast.hasPrevious()).isTrue();
        assertThat(clampedLast.hasNext()).isFalse();

        assertThatThrownBy(() -> WarpDestinationQuery.paginate(items, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Aetheryte aetheryte(
            final String name,
            final UUID worldId,
            final String worldName,
            final int x,
            final int y,
            final int z,
            final UUID ownerId,
            final String ownerName,
            final long createdAt
    ) {
        return new Aetheryte(
                UUID.randomUUID(),
                name,
                worldId,
                worldName,
                x,
                y,
                z,
                ownerId,
                createdAt,
                Aetheryte.DEFAULT_VARIATION,
                ownerName
        );
    }
}
