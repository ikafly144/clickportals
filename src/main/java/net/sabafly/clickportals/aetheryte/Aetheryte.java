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
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * Immutable domain model representing a placed Aetheryte warp point (#6, #12, #24, #37, #53).
 */
public record Aetheryte(
        UUID id,
        String name,
        UUID worldId,
        String worldName,
        int blockX,
        int blockY,
        int blockZ,
        UUID ownerId,
        long createdAt,
        String variation,
        @Nullable String ownerName,
        AetheryteVisibility visibility,
        Set<UUID> invitedPlayers,
        @Nullable UUID networkId
) {

    /**
     * Identifier of the default Aetheryte variation (#37).
     */
    public static final String DEFAULT_VARIATION = "default";

    /**
     * Compact constructor normalizing variation identifiers, optional owner names, visibility, and invitees.
     */
    public Aetheryte {
        variation = normalizeVariation(variation);
        ownerName = normalizeOwnerName(ownerName);
        visibility = visibility == null ? AetheryteVisibility.PUBLIC : visibility;
        invitedPlayers = invitedPlayers == null ? Set.of() : Set.copyOf(invitedPlayers);
    }

    /**
     * Convenience constructor using {@link #DEFAULT_VARIATION}, no cached owner name, and {@link AetheryteVisibility#PUBLIC}.
     */
    public Aetheryte(
            final UUID id,
            final String name,
            final UUID worldId,
            final String worldName,
            final int blockX,
            final int blockY,
            final int blockZ,
            final UUID ownerId,
            final long createdAt
    ) {
        this(id, name, worldId, worldName, blockX, blockY, blockZ, ownerId, createdAt,
                DEFAULT_VARIATION, null, AetheryteVisibility.PUBLIC, Set.of(), null);
    }

    /**
     * Convenience constructor using no cached owner name and {@link AetheryteVisibility#PUBLIC}.
     */
    public Aetheryte(
            final UUID id,
            final String name,
            final UUID worldId,
            final String worldName,
            final int blockX,
            final int blockY,
            final int blockZ,
            final UUID ownerId,
            final long createdAt,
            final String variation
    ) {
        this(id, name, worldId, worldName, blockX, blockY, blockZ, ownerId, createdAt,
                variation, null, AetheryteVisibility.PUBLIC, Set.of(), null);
    }

    /**
     * Convenience constructor defaulting visibility to {@link AetheryteVisibility#PUBLIC} and empty invitees (#24).
     */
    public Aetheryte(
            final UUID id,
            final String name,
            final UUID worldId,
            final String worldName,
            final int blockX,
            final int blockY,
            final int blockZ,
            final UUID ownerId,
            final long createdAt,
            final String variation,
            @Nullable final String ownerName
    ) {
        this(id, name, worldId, worldName, blockX, blockY, blockZ, ownerId, createdAt,
                variation, ownerName, AetheryteVisibility.PUBLIC, Set.of(), null);
    }

    /**
     * Convenience constructor defaulting {@code networkId} to {@code null} (#27).
     */
    public Aetheryte(
            final UUID id,
            final String name,
            final UUID worldId,
            final String worldName,
            final int blockX,
            final int blockY,
            final int blockZ,
            final UUID ownerId,
            final long createdAt,
            final String variation,
            @Nullable final String ownerName,
            final AetheryteVisibility visibility,
            final Set<UUID> invitedPlayers
    ) {
        this(id, name, worldId, worldName, blockX, blockY, blockZ, ownerId, createdAt,
                variation, ownerName, visibility, invitedPlayers, null);
    }

    /**
     * Normalizes an optional owner name, returning {@code null} when null or blank.
     */
    public static @Nullable String normalizeOwnerName(@Nullable final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.strip();
    }

    /**
     * Returns a copy of this Aetheryte with an updated display name.
     */
    public Aetheryte withName(final String newName) {
        return new Aetheryte(id, newName, worldId, worldName, blockX, blockY, blockZ,
                ownerId, createdAt, variation, ownerName, visibility, invitedPlayers, networkId);
    }

    /**
     * Returns a copy of this Aetheryte with an updated owner UUID and cached name.
     */
    public Aetheryte withOwner(final UUID newOwnerId, @Nullable final String newOwnerName) {
        return new Aetheryte(id, name, worldId, worldName, blockX, blockY, blockZ,
                newOwnerId, createdAt, variation, newOwnerName, visibility, invitedPlayers, networkId);
    }

    /**
     * Returns a copy of this Aetheryte with an updated cached owner name.
     */
    public Aetheryte withOwnerName(@Nullable final String newOwnerName) {
        return new Aetheryte(id, name, worldId, worldName, blockX, blockY, blockZ,
                ownerId, createdAt, variation, newOwnerName, visibility, invitedPlayers, networkId);
    }

    /**
     * Returns a copy of this Aetheryte with an updated variation identifier.
     */
    public Aetheryte withVariation(final String newVariation) {
        return new Aetheryte(id, name, worldId, worldName, blockX, blockY, blockZ,
                ownerId, createdAt, newVariation, ownerName, visibility, invitedPlayers, networkId);
    }

    /**
     * Returns a copy of this Aetheryte with an updated {@link AetheryteVisibility} (#24).
     */
    public Aetheryte withVisibility(final AetheryteVisibility newVisibility) {
        return new Aetheryte(id, name, worldId, worldName, blockX, blockY, blockZ,
                ownerId, createdAt, variation, ownerName, newVisibility, invitedPlayers, networkId);
    }

    /**
     * Returns a copy of this Aetheryte with an updated set of invited players (#24).
     */
    public Aetheryte withInvitedPlayers(final Set<UUID> newInvitees) {
        return new Aetheryte(id, name, worldId, worldName, blockX, blockY, blockZ,
                ownerId, createdAt, variation, ownerName, visibility, newInvitees, networkId);
    }

    /**
     * Returns a copy of this Aetheryte with an updated network ID ({@code null} for standalone Aetherytes) (#27).
     */
    public Aetheryte withNetworkId(final @Nullable UUID newNetworkId) {
        return new Aetheryte(id, name, worldId, worldName, blockX, blockY, blockZ,
                ownerId, createdAt, variation, ownerName, visibility, invitedPlayers, newNetworkId);
    }

    /**
     * Returns a copy of this Aetheryte with {@code inviteeId} added (#24).
     */
    public Aetheryte addInvitee(final UUID inviteeId) {
        final Set<UUID> updated = new LinkedHashSet<>(invitedPlayers);
        updated.add(inviteeId);
        return withInvitedPlayers(updated);
    }

    /**
     * Returns a copy of this Aetheryte with {@code inviteeId} removed (#24).
     */
    public Aetheryte removeInvitee(final UUID inviteeId) {
        if (!invitedPlayers.contains(inviteeId)) {
            return this;
        }
        final Set<UUID> updated = new LinkedHashSet<>(invitedPlayers);
        updated.remove(inviteeId);
        return withInvitedPlayers(updated);
    }

    /**
     * Checks whether a player with {@code playerId} is permitted to see and warp to this Aetheryte
     * according to its visibility mode and invitee list (excluding bypass permissions) (#24).
     */
    public boolean isAccessibleTo(final @Nullable UUID playerId) {
        if (visibility == AetheryteVisibility.PUBLIC) {
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
     * Normalizes a variation identifier to lowercase or returns {@link #DEFAULT_VARIATION} if null/blank.
     */
    public static String normalizeVariation(@Nullable final String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_VARIATION;
        }
        return raw.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the {@link BlockPosKey} corresponding to this Aetheryte's block coordinates.
     */
    public BlockPosKey posKey() {
        return new BlockPosKey(worldId, blockX, blockY, blockZ);
    }

    /**
     * Returns a Bukkit {@link Location} pointing to the Aetheryte's block origin.
     */
    public Location blockLocation(final World world) {
        return new Location(world, blockX, blockY, blockZ);
    }

    /**
     * Returns the Bukkit {@link Location} where the backing {@link org.bukkit.entity.Marker} entity is spawned.
     */
    public Location markerLocation(final World world) {
        return new Location(world, blockX + 0.5, blockY, blockZ + 0.5);
    }

    /**
     * Returns the landing {@link Location} centered at the Aetheryte position.
     */
    public Location teleportDestination(final World world, final float yaw, final float pitch) {
        return new Location(world, blockX + 0.5, blockY, blockZ + 0.5, yaw, pitch);
    }

    /**
     * Computes the 3D Euclidean block distance to another Aetheryte if both are in the same world.
     */
    public OptionalDouble distanceTo(final Aetheryte other) {
        if (!this.worldId.equals(other.worldId)) {
            return OptionalDouble.empty();
        }
        final double dx = this.blockX - other.blockX;
        final double dy = this.blockY - other.blockY;
        final double dz = this.blockZ - other.blockZ;
        return OptionalDouble.of(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }
}
