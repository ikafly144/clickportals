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
package net.sabafly.clickportals.service;

import io.papermc.paper.entity.Leashable;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Finds and Folia-safely teleports {@link Leashable} entities leashed to a warping player (#22).
 */
public final class PetWarpHelper {

    /**
     * Bounding radius in blocks used when scanning for entities leashed to a warping player (#22).
     */
    public static final double SEARCH_RADIUS = 16.0;

    private static final double MAX_REATTACH_DISTANCE_SQUARED = 32.0 * 32.0;

    private PetWarpHelper() {
    }

    /**
     * Returns all valid entities within {@link #SEARCH_RADIUS} blocks implementing {@link Leashable} that are
     * currently leashed directly to {@code player}.
     */
    public static List<Leashable> findLeashedEntities(final Player player) {
        final List<Entity> nearby;
        try {
            nearby = player.getNearbyEntities(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS);
        } catch (final RuntimeException e) {
            return List.of();
        }
        if (nearby == null || nearby.isEmpty()) {
            return List.of();
        }
        final List<Leashable> leashed = new ArrayList<>();
        for (final Entity entity : nearby) {
            if (entity instanceof Leashable leashable
                    && entity.isValid()
                    && !entity.isDead()
                    && isLeashedToPlayer(leashable, player)) {
                leashed.add(leashable);
            }
        }
        return List.copyOf(leashed);
    }

    /**
     * Returns {@code true} if {@code leashable} is currently leashed directly to {@code player}.
     */
    public static boolean isLeashedToPlayer(final Leashable leashable, final Player player) {
        if (!leashable.isLeashed()) {
            return false;
        }
        try {
            final @Nullable Entity holder = leashable.getLeashHolder();
            if (holder == null) {
                return false;
            }
            final @Nullable UUID playerId = player.getUniqueId();
            if (playerId != null && playerId.equals(holder.getUniqueId())) {
                return true;
            }
            return holder.equals(player);
        } catch (final IllegalStateException e) {
            return false;
        }
    }

    /**
     * Detaches all eligible {@code pets} from {@code player} before teleporting on their respective Folia owning
     * threads, avoiding lead item drops when chunks unload or distances snap (#22).
     *
     * <p>When every pet is already owned by the current thread (the common case on Paper and single-region Folia),
     * the returned future completes synchronously.
     */
    public static CompletableFuture<List<Leashable>> detachLeashedEntities(
            final Plugin plugin,
            final Player player,
            final List<Leashable> pets
    ) {
        if (pets.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        final List<Leashable> immediateDetached = new ArrayList<>(pets.size());
        final List<CompletableFuture<@Nullable Leashable>> asyncFutures = new ArrayList<>();
        final boolean hasServer = Bukkit.getServer() != null;

        for (final Leashable pet : pets) {
            final boolean sameThread = !hasServer || Bukkit.isOwnedByCurrentRegion(pet);
            if (sameThread) {
                if (tryDetach(pet, player)) {
                    immediateDetached.add(pet);
                }
                continue;
            }
            final EntityScheduler scheduler = pet.getScheduler();
            if (scheduler == null) {
                if (tryDetach(pet, player)) {
                    immediateDetached.add(pet);
                }
                continue;
            }
            final CompletableFuture<@Nullable Leashable> future = new CompletableFuture<>();
            try {
                final var scheduled = scheduler.run(plugin, task -> {
                    if (tryDetach(pet, player)) {
                        future.complete(pet);
                    } else {
                        future.complete(null);
                    }
                }, () -> future.complete(null));
                if (scheduled == null) {
                    future.complete(null);
                }
            } catch (final RuntimeException e) {
                future.complete(null);
            }
            asyncFutures.add(future);
        }

        if (asyncFutures.isEmpty()) {
            return CompletableFuture.completedFuture(List.copyOf(immediateDetached));
        }
        return CompletableFuture.allOf(asyncFutures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    final List<Leashable> combined = new ArrayList<>(immediateDetached);
                    for (final CompletableFuture<@Nullable Leashable> future : asyncFutures) {
                        final @Nullable Leashable result = future.join();
                        if (result != null) {
                            combined.add(result);
                        }
                    }
                    return List.copyOf(combined);
                });
    }

    /**
     * Teleports each previously detached pet to {@code destinationLoc} and schedules leash re-attachment to
     * {@code player} once both teleport futures settle (so both successful warps and failed/cancelled teleports that
     * leave the player and pet near each other restore the leash) (#22).
     */
    public static void teleportAndReattachPets(
            final Plugin plugin,
            final Player player,
            final Location destinationLoc,
            final List<Leashable> detachedPets,
            final CompletableFuture<Boolean> playerTeleportFuture
    ) {
        if (detachedPets.isEmpty()) {
            return;
        }
        for (final Leashable pet : detachedPets) {
            final CompletableFuture<Boolean> petTeleportFuture;
            try {
                final CompletableFuture<Boolean> rawFuture = pet.teleportAsync(destinationLoc);
                petTeleportFuture = rawFuture != null ? rawFuture : CompletableFuture.completedFuture(false);
            } catch (final RuntimeException e) {
                final var _ = playerTeleportFuture.whenComplete((ignored, ex) -> scheduleReattach(plugin, pet, player));
                continue;
            }
            final var _ = playerTeleportFuture.thenCombine(
                    petTeleportFuture,
                    (playerOk, petOk) -> Boolean.TRUE.equals(playerOk) && Boolean.TRUE.equals(petOk)
            ).handle((bothSucceeded, ex) -> {
                if (ex != null) {
                    plugin.getSLF4JLogger().debug("Pet teleport completion callback failed: {}", ex.getMessage());
                }
                scheduleReattach(plugin, pet, player);
                return null;
            });
        }
    }

    /**
     * Schedules leash re-attachment for all previously detached pets (used when warp dispatch aborts) (#22).
     */
    public static void scheduleReattachAll(
            final Plugin plugin,
            final List<Leashable> detachedPets,
            final Player player
    ) {
        for (final Leashable pet : detachedPets) {
            scheduleReattach(plugin, pet, player);
        }
    }

    private static boolean tryDetach(final Leashable pet, final Player player) {
        if (!pet.isValid() || pet.isDead() || !isLeashedToPlayer(pet, player)) {
            return false;
        }
        pet.setLeashHolder(null);
        return true;
    }

    private static void scheduleReattach(
            final Plugin plugin,
            final Leashable pet,
            final Player player
    ) {
        final EntityScheduler scheduler = pet.getScheduler();
        if (scheduler == null) {
            tryReattach(pet, player);
            return;
        }
        try {
            scheduler.runDelayed(plugin, task -> tryReattach(pet, player), null, 1L);
        } catch (final RuntimeException e) {
            plugin.getSLF4JLogger().debug("Could not schedule pet leash reattach: {}", e.getMessage());
        }
    }

    private static void tryReattach(final Leashable pet, final Player player) {
        if (!pet.isValid() || pet.isDead() || !player.isOnline() || player.isDead() || pet.isLeashed()) {
            return;
        }
        final @Nullable World petWorld = pet.getWorld();
        final @Nullable World playerWorld = player.getWorld();
        if (petWorld == null || playerWorld == null || !petWorld.getUID().equals(playerWorld.getUID())) {
            return;
        }
        final @Nullable Location petLoc = pet.getLocation();
        final @Nullable Location playerLoc = player.getLocation();
        if (petLoc != null && playerLoc != null && petLoc.distanceSquared(playerLoc) > MAX_REATTACH_DISTANCE_SQUARED) {
            return;
        }
        pet.setLeashHolder(player);
    }
}
