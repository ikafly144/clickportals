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

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Player-owned, world-scoped Aetheryte network grouping network-type Aetherytes (#27).
 */
public record AetheryteNetwork(
        UUID id,
        String name,
        UUID worldId,
        String worldName,
        UUID ownerId,
        @Nullable String ownerName,
        long createdAt,
        AetheryteVisibility visibility,
        Set<UUID> invitedPlayers,
        @Nullable UUID hubAetheryteId
) {

    public AetheryteNetwork(
            final UUID id,
            final String name,
            final UUID worldId,
            final String worldName,
            final UUID ownerId,
            @Nullable final String ownerName,
            final long createdAt,
            final AetheryteVisibility visibility,
            final Set<UUID> invitedPlayers
    ) {
        this(id, name, worldId, worldName, ownerId, ownerName, createdAt, visibility, invitedPlayers, null);
    }

    public AetheryteNetwork {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(ownerId, "ownerId");
        name = name == null ? "" : name.strip();
        ownerName = Aetheryte.normalizeOwnerName(ownerName);
        visibility = visibility == null ? AetheryteVisibility.PUBLIC : visibility;
        invitedPlayers = invitedPlayers == null ? Set.of() : Set.copyOf(invitedPlayers);
    }

    public AetheryteNetwork withName(final String newName) {
        return new AetheryteNetwork(id, newName, worldId, worldName, ownerId, ownerName, createdAt, visibility, invitedPlayers, hubAetheryteId);
    }

    public AetheryteNetwork withOwner(final UUID newOwnerId, @Nullable final String newOwnerName) {
        return new AetheryteNetwork(id, name, worldId, worldName, newOwnerId, newOwnerName, createdAt, visibility, invitedPlayers, hubAetheryteId);
    }

    public AetheryteNetwork withOwnerName(@Nullable final String newOwnerName) {
        return new AetheryteNetwork(id, name, worldId, worldName, ownerId, newOwnerName, createdAt, visibility, invitedPlayers, hubAetheryteId);
    }

    public AetheryteNetwork withVisibility(final AetheryteVisibility newVisibility) {
        return new AetheryteNetwork(id, name, worldId, worldName, ownerId, ownerName, createdAt, newVisibility, invitedPlayers, hubAetheryteId);
    }

    public AetheryteNetwork withInvitedPlayers(final Set<UUID> newInvitees) {
        return new AetheryteNetwork(id, name, worldId, worldName, ownerId, ownerName, createdAt, visibility, newInvitees, hubAetheryteId);
    }

    public AetheryteNetwork withHubAetheryteId(@Nullable final UUID newHubAetheryteId) {
        return new AetheryteNetwork(id, name, worldId, worldName, ownerId, ownerName, createdAt, visibility, invitedPlayers, newHubAetheryteId);
    }

    public AetheryteNetwork addInvitee(final UUID inviteeId) {
        final Set<UUID> updated = new LinkedHashSet<>(invitedPlayers);
        updated.add(inviteeId);
        return withInvitedPlayers(updated);
    }

    public AetheryteNetwork removeInvitee(final UUID inviteeId) {
        if (!invitedPlayers.contains(inviteeId)) {
            return this;
        }
        final Set<UUID> updated = new LinkedHashSet<>(invitedPlayers);
        updated.remove(inviteeId);
        return withInvitedPlayers(updated);
    }

    /**
     * Checks whether a player can warp to Aetherytes in this network according to visibility and invitees (#27).
     */
    public boolean isAccessibleTo(final @Nullable UUID playerId, final boolean bypassVisibility) {
        if (bypassVisibility || visibility == AetheryteVisibility.PUBLIC) {
            return true;
        }
        if (playerId == null) {
            return false;
        }
        if (ownerId.equals(playerId)) {
            return true;
        }
        return visibility == AetheryteVisibility.INVITE_ONLY && invitedPlayers.contains(playerId);
    }

    /**
     * Checks whether a player may join/add a new Aetheryte to this network (#27).
     */
    public boolean canJoin(final @Nullable UUID playerId, final boolean isAdmin) {
        if (isAdmin) {
            return true;
        }
        if (playerId == null) {
            return false;
        }
        return ownerId.equals(playerId) || invitedPlayers.contains(playerId);
    }
}
