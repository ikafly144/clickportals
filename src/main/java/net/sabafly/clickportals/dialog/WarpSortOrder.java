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

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Available sort orders for warp destination lists (#55).
 */
public enum WarpSortOrder {
    DISTANCE("distance"),
    NAME("name"),
    OWNER("owner"),
    CREATED("created");

    private final String id;

    WarpSortOrder(final String id) {
        this.id = id;
    }

    /**
     * Returns the persistent identifier for this sort order.
     */
    public String id() {
        return id;
    }

    /**
     * Returns the next sort order in the 1-click cycle.
     */
    public WarpSortOrder next() {
        return switch (this) {
            case DISTANCE -> NAME;
            case NAME -> OWNER;
            case OWNER -> CREATED;
            case CREATED -> DISTANCE;
        };
    }

    /**
     * Resolves a {@link WarpSortOrder} from its persisted identifier, defaulting to {@link #DISTANCE}.
     */
    public static WarpSortOrder fromId(@Nullable final String raw) {
        if (raw == null || raw.isBlank()) {
            return DISTANCE;
        }
        final String normalized = raw.strip().toLowerCase(Locale.ROOT);
        for (final WarpSortOrder order : values()) {
            if (order.id.equals(normalized)) {
                return order;
            }
        }
        return DISTANCE;
    }
}
