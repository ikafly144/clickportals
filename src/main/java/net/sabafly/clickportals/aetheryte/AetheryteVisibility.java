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

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Visibility and access restriction modes for a placed {@link Aetheryte} (#24, #25).
 */
public enum AetheryteVisibility {

    /**
     * Any player can see and warp to this Aetheryte.
     */
    PUBLIC("public"),

    /**
     * Only the owner and explicitly invited players can see and warp to this Aetheryte.
     */
    INVITE_ONLY("invite-only"),

    /**
     * Only the owner can see and warp to this Aetheryte.
     */
    OWNER_ONLY("owner-only");

    private final String id;

    AetheryteVisibility(final String id) {
        this.id = id;
    }

    /**
     * Returns the serialized identifier used in PDC storage and configuration.
     */
    public String id() {
        return id;
    }

    /**
     * Parses a visibility identifier or enum name, falling back to {@link #PUBLIC} if null or unknown.
     */
    public static AetheryteVisibility fromString(final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return PUBLIC;
        }
        final String normalized = raw.strip().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "invite-only" -> INVITE_ONLY;
            case "owner-only", "private" -> OWNER_ONLY;
            default -> PUBLIC;
        };
    }
}
