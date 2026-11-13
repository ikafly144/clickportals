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
        String searchQuery,
        WarpSortOrder sortOrder,
        int page,
        boolean favoriteEditMode
) {

    /**
     * Active screen within the warp Dialog hierarchy.
     */
    public enum ViewMode {
        ROOT,
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
        return new WarpDialogState(ViewMode.ROOT, null, "", sortOrder, 0, false);
    }

    /**
     * Returns a state navigating to the specified world's destination page.
     */
    public WarpDialogState toWorld(final UUID worldId) {
        return new WarpDialogState(ViewMode.WORLD, worldId, "", sortOrder, 0, false);
    }

    /**
     * Returns a state navigating to the favorites destination page.
     */
    public WarpDialogState toFavorites() {
        return new WarpDialogState(ViewMode.FAVORITES, null, "", sortOrder, 0, false);
    }

    /**
     * Returns a state navigating to the cross-world search/all-destinations page.
     */
    public WarpDialogState toAll() {
        return new WarpDialogState(ViewMode.ALL, null, "", sortOrder, 0, false);
    }

    /**
     * Returns a state navigating to the operations/management page.
     */
    public WarpDialogState toOperations() {
        return new WarpDialogState(ViewMode.OPERATIONS, null, "", sortOrder, 0, false);
    }

    /**
     * Returns a copy with an updated page index.
     */
    public WarpDialogState withPage(final int newPage) {
        return new WarpDialogState(mode, targetWorldId, searchQuery, sortOrder, newPage, favoriteEditMode);
    }

    /**
     * Returns a copy with an updated search query, resetting the page index to {@code 0}.
     */
    public WarpDialogState withSearchQuery(@Nullable final String newQuery) {
        final String normalized = newQuery == null ? "" : newQuery.strip();
        return new WarpDialogState(mode, targetWorldId, normalized, sortOrder, 0, favoriteEditMode);
    }

    /**
     * Returns a copy with an updated sort order, resetting the page index to {@code 0}.
     */
    public WarpDialogState withSortOrder(final WarpSortOrder newSortOrder) {
        return new WarpDialogState(mode, targetWorldId, searchQuery, newSortOrder, 0, favoriteEditMode);
    }

    /**
     * Returns a copy with toggled favorite edit mode.
     */
    public WarpDialogState withFavoriteEditMode(final boolean enabled) {
        return new WarpDialogState(mode, targetWorldId, searchQuery, sortOrder, page, enabled);
    }
}
