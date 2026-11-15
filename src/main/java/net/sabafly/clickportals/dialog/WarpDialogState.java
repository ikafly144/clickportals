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

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Immutable navigation and filter state passed across hierarchical warp Dialog transitions (#54, #55, #56).
 */
public record WarpDialogState(
        ViewMode mode,
        @Nullable UUID targetWorldId,
        @Nullable UUID targetNetworkId,
        String searchQuery,
        WarpSortOrder sortOrder,
        int page,
        boolean favoriteEditMode
) {

    /**
     * Backward-compatible constructor without {@code targetNetworkId}.
     */
    public WarpDialogState(
            final ViewMode mode,
            final @Nullable UUID targetWorldId,
            final String searchQuery,
            final WarpSortOrder sortOrder,
            final int page,
            final boolean favoriteEditMode
    ) {
        this(mode, targetWorldId, null, searchQuery, sortOrder, page, favoriteEditMode);
    }

    /**
     * Active screen within the warp Dialog hierarchy.
     */
    public enum ViewMode {
        ROOT,
        NETWORK,
        FAVORITES,
        WORLD,
        ALL,
        OPERATIONS
    }

    public WarpDialogState {
        searchQuery = searchQuery == null ? "" : searchQuery.strip();
        sortOrder = sortOrder == null ? WarpSortOrder.DISTANCE : sortOrder;
        page = Math.max(0, page);
    }

    /**
     * Creates the initial root-menu state for the given player's persisted sort preference.
     */
    public static WarpDialogState root(final WarpSortOrder sortOrder) {
        return root(sortOrder, "");
    }

    /**
     * Creates a root-menu state preserving {@code sortOrder} and an existing {@code searchQuery} (#55).
     */
    public static WarpDialogState root(final WarpSortOrder sortOrder, @Nullable final String searchQuery) {
        return new WarpDialogState(ViewMode.ROOT, null, null, searchQuery == null ? "" : searchQuery, sortOrder, 0, false);
    }

    /**
     * Returns a state returning to the root menu while preserving {@code sortOrder} and {@code searchQuery} (#55).
     */
    public WarpDialogState toRoot() {
        return root(sortOrder, searchQuery);
    }

    /**
     * Returns a state navigating to the specified Hub-hosted network's destination page while preserving {@code searchQuery} (#87).
     */
    public WarpDialogState toNetwork(final UUID networkId) {
        return new WarpDialogState(ViewMode.NETWORK, null, networkId, searchQuery, sortOrder, 0, false);
    }

    /**
     * Returns a state navigating to the specified world's destination page while preserving {@code searchQuery}.
     */
    public WarpDialogState toWorld(final UUID worldId) {
        return new WarpDialogState(ViewMode.WORLD, worldId, null, searchQuery, sortOrder, 0, false);
    }

    /**
     * Returns a state navigating to the favorites destination page while preserving {@code searchQuery}.
     */
    public WarpDialogState toFavorites() {
        return new WarpDialogState(ViewMode.FAVORITES, null, null, searchQuery, sortOrder, 0, false);
    }

    /**
     * Returns a state navigating to the cross-world search/all-destinations page while preserving {@code searchQuery}.
     */
    public WarpDialogState toAll() {
        return new WarpDialogState(ViewMode.ALL, null, null, searchQuery, sortOrder, 0, false);
    }

    /**
     * Returns a state navigating to the operations/management page while preserving {@code searchQuery}.
     */
    public WarpDialogState toOperations() {
        return new WarpDialogState(ViewMode.OPERATIONS, null, null, searchQuery, sortOrder, 0, false);
    }

    /**
     * Returns a copy with an updated page index.
     */
    public WarpDialogState withPage(final int newPage) {
        return new WarpDialogState(mode, targetWorldId, targetNetworkId, searchQuery, sortOrder, newPage, favoriteEditMode);
    }

    /**
     * Returns a copy with an updated search query, resetting the page index to {@code 0}.
     */
    public WarpDialogState withSearchQuery(@Nullable final String newQuery) {
        final String normalized = newQuery == null ? "" : newQuery.strip();
        return new WarpDialogState(mode, targetWorldId, targetNetworkId, normalized, sortOrder, 0, favoriteEditMode);
    }

    /**
     * Returns a copy with an updated sort order, resetting the page index to {@code 0}.
     */
    public WarpDialogState withSortOrder(final WarpSortOrder newSortOrder) {
        return new WarpDialogState(mode, targetWorldId, targetNetworkId, searchQuery, newSortOrder, 0, favoriteEditMode);
    }

    /**
     * Returns a copy with toggled favorite edit mode.
     */
    public WarpDialogState withFavoriteEditMode(final boolean enabled) {
        return new WarpDialogState(mode, targetWorldId, targetNetworkId, searchQuery, sortOrder, page, enabled);
    }
}
