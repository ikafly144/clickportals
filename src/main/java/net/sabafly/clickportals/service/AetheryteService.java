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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.item.AetheryteItemFactory;
import net.sabafly.clickportals.message.MessageKey;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import net.sabafly.clickportals.visual.HologramService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Folia-safe application service for placing, removing, evaluating, and warping between Aetherytes (#6, #11, #12, #17, #19).
 */
public final class AetheryteService {

    private static final int MAX_NAME_LENGTH = 32;

    /**
     * Result of evaluating whether a player can warp to a specific Aetheryte and at what cost (#12, #13).
     */
    public record WarpEvaluation(
            boolean allowed,
            Component costDisplay,
            @Nullable Component denyReason
    ) {
        /**
         * Creates an allowed evaluation with the given cost display.
         */
        public static WarpEvaluation permitted(final Component costDisplay) {
            return new WarpEvaluation(true, costDisplay, null);
        }

        /**
         * Creates a denied evaluation with the given cost display and rejection reason.
         */
        public static WarpEvaluation denied(final Component costDisplay, final Component denyReason) {
            return new WarpEvaluation(false, costDisplay, denyReason);
        }
    }

    /**
     * Pluggable strategy for assessing and charging warp costs (#12, #13, #14).
     */
    public interface WarpCostEvaluator {
        /**
         * Evaluates the cost and affordability of warping from {@code source} (nullable) to {@code destination}.
         */
        WarpEvaluation evaluate(Player player, @Nullable Aetheryte source, Aetheryte destination);

        /**
         * Charges the warp cost on the player's entity scheduler right before teleportation.
         *
         * @return {@code true} if the charge succeeded, {@code false} if insufficient funds/resources.
         */
        default boolean charge(final Player player, final @Nullable Aetheryte source, final Aetheryte destination) {
            return evaluate(player, source, destination).allowed();
        }
    }

    private final Plugin plugin;
    private final AetheryteRegistry registry;
    private volatile HologramService hologramService;
    private final Messages messages;
    private final WarpCostEvaluator costEvaluator;

    /**
     * Creates a new {@link AetheryteService} with default messages and free cost evaluation.
     */
    public AetheryteService(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final HologramService hologramService
    ) {
        this(plugin, registry, hologramService, Messages.defaults());
    }

    /**
     * Creates a new {@link AetheryteService} with the given {@link Messages} and default free cost evaluation.
     */
    public AetheryteService(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final HologramService hologramService,
            final Messages messages
    ) {
        this(
                plugin,
                registry,
                hologramService,
                messages,
                (player, source, destination) -> WarpEvaluation.permitted(messages.freeCostLabel(player))
        );
    }

    /**
     * Creates a new {@link AetheryteService} with a custom {@link WarpCostEvaluator}.
     */
    public AetheryteService(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final HologramService hologramService,
            final WarpCostEvaluator costEvaluator
    ) {
        this(plugin, registry, hologramService, Messages.defaults(), costEvaluator);
    }

    /**
     * Creates a new {@link AetheryteService} with the given {@link Messages} and custom {@link WarpCostEvaluator}.
     */
    public AetheryteService(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final HologramService hologramService,
            final Messages messages,
            final WarpCostEvaluator costEvaluator
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.hologramService = hologramService;
        this.messages = messages;
        this.costEvaluator = costEvaluator;
    }

    /**
     * Returns the backing {@link AetheryteRegistry}.
     */
    public AetheryteRegistry registry() {
        return registry;
    }

    /**
     * Returns the active {@link HologramService}.
     */
    public HologramService hologramService() {
        return hologramService;
    }

    /**
     * Updates the active {@link HologramService} reference when {@code hologram.enabled} changes on reload.
     */
    public void setHologramService(final HologramService hologramService) {
        this.hologramService = hologramService;
    }

    /**
     * Returns the active {@link Messages} service.
     */
    public Messages messages() {
        return messages;
    }

    /**
     * Validates and sanitizes an Aetheryte name.
     */
    public static Optional<String> sanitizeName(final String rawName) {
        final String trimmed = rawName.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(trimmed);
    }

    /**
     * Checks whether the given player is permitted to place a new Aetheryte (#17).
     */
    public static boolean canPlace(final Player player) {
        return Permissions.canPlace(player);
    }

    /**
     * Checks whether the given player is permitted to use Aetheryte warps (#17).
     */
    public static boolean canUse(final Player player) {
        return Permissions.canUse(player);
    }

    /**
     * Checks whether the given player is allowed to remove the specified Aetheryte (#17).
     */
    public static boolean canRemove(final Player player, final Aetheryte aetheryte) {
        return Permissions.canRemove(player, player.getUniqueId(), aetheryte);
    }

    /**
     * Evaluates whether a player can warp from {@code source} to {@code destination}, checking both permissions
     * and the configured {@link WarpCostEvaluator} (#12, #13, #17).
     */
    public WarpEvaluation evaluateWarp(
            final Player player,
            final @Nullable Aetheryte source,
            final Aetheryte destination
    ) {
        final WarpEvaluation costResult = Permissions.canBypassWarpCost(player)
                ? WarpEvaluation.permitted(messages.freeCostLabel(player))
                : costEvaluator.evaluate(player, source, destination);
        if (!canUse(player)) {
            return WarpEvaluation.denied(
                    costResult.costDisplay(),
                    messages.get(player, MessageKey.ERROR_NO_WARP_PERMISSION)
            );
        }
        return costResult;
    }

    /**
     * Places a new Aetheryte backed by a non-ticking {@link Marker} entity at the target block coordinates
     * using Folia's EntityScheduler and RegionScheduler (#6, #11, #12).
     */
    public void placeAetheryte(
            final Player player,
            final World world,
            final int blockX,
            final int blockY,
            final int blockZ,
            final String rawName
    ) {
        if (!canPlace(player)) {
            messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return;
        }
        final Optional<String> validName = sanitizeName(rawName);
        if (validName.isEmpty()) {
            messages.send(player, MessageKey.ERROR_INVALID_NAME);
            return;
        }
        final String name = validName.get();

        if (registry.getByPosition(world, blockX, blockY, blockZ).isPresent()) {
            messages.send(player, MessageKey.ERROR_ALREADY_EXISTS);
            return;
        }

        final UUID ownerId = player.getUniqueId();
        final Location targetLocation = new Location(world, blockX, blockY, blockZ);

        // Step 1: Consume the Aetheryte item on the player's entity thread (unless in Creative mode).
        player.getScheduler().run(plugin, task -> {
            final boolean creative = player.getGameMode() == GameMode.CREATIVE;
            if (!creative && !consumeOneAetheryteItem(player.getInventory())) {
                messages.send(player, MessageKey.ERROR_MISSING_ITEM);
                return;
            }

            // Step 2: Spawn the backing Marker entity and persist to World PDC on the target region thread (#6, #11).
            Bukkit.getRegionScheduler().execute(plugin, targetLocation, () -> {
                if (registry.getByPosition(world, blockX, blockY, blockZ).isPresent()) {
                    if (!creative) {
                        refundItemToPlayer(player, targetLocation);
                    }
                    messages.send(player, MessageKey.ERROR_ALREADY_EXISTS);
                    return;
                }

                final Block block = world.getBlockAt(blockX, blockY, blockZ);
                if (!isReplaceableBlock(block)) {
                    if (!creative) {
                        refundItemToPlayer(player, targetLocation);
                    }
                    messages.send(player, MessageKey.ERROR_SPACE_NOT_EMPTY);
                    return;
                }

                if (!block.getType().isAir()) {
                    block.setType(Material.AIR, true);
                }

                final Aetheryte aetheryte = new Aetheryte(
                        UUID.randomUUID(),
                        name,
                        world.getUID(),
                        world.getName(),
                        blockX,
                        blockY,
                        blockZ,
                        ownerId,
                        System.currentTimeMillis()
                );

                spawnOrUpdateMarkerEntity(world, aetheryte);
                registry.register(world, aetheryte);
                hologramService.showAetheryte(aetheryte);

                world.playSound(
                        aetheryte.markerLocation(world),
                        Sound.BLOCK_LODESTONE_PLACE,
                        1.0f,
                        1.0f
                );
                player.sendMessage(messages.aetheryteCreated(player, aetheryte));
            });
        }, null);
    }

    /**
     * Spawns the backing non-ticking {@link Marker} entity with PDC data on the current region thread (#6).
     */
    private static void spawnOrUpdateMarkerEntity(final World world, final Aetheryte aetheryte) {
        final Location markerLoc = aetheryte.markerLocation(world);
        world.spawn(markerLoc, Marker.class, marker -> {
            marker.setPersistent(true);
            AetheryteRegistry.writeMarkerData(marker, aetheryte);
        });
    }

    /**
     * Removes an existing Aetheryte, despawns its backing {@link Marker} entity, and returns the creation item
     * to the player if permitted.
     */
    public void removeAetheryte(final Player player, final UUID aetheryteId) {
        final Optional<Aetheryte> opt = registry.getById(aetheryteId);
        if (opt.isEmpty()) {
            messages.send(player, MessageKey.ERROR_NOT_FOUND);
            return;
        }
        final Aetheryte aetheryte = opt.get();
        if (!canRemove(player, aetheryte)) {
            messages.send(player, MessageKey.ERROR_NOT_OWNER);
            return;
        }
        final World world = Bukkit.getWorld(aetheryte.worldId());
        if (world == null) {
            messages.send(player, MessageKey.ERROR_NOT_FOUND);
            return;
        }

        final Location blockLoc = aetheryte.blockLocation(world);
        Bukkit.getRegionScheduler().execute(plugin, blockLoc, () -> {
            final Optional<Aetheryte> removed = registry.unregister(world, aetheryte.id());
            if (removed.isEmpty()) {
                messages.send(player, MessageKey.ERROR_NOT_FOUND);
                return;
            }

            removeMarkerEntitiesAt(world, aetheryte);

            // Clean up legacy Lodestone block if present from v0.1 worlds
            final Block block = world.getBlockAt(aetheryte.blockX(), aetheryte.blockY(), aetheryte.blockZ());
            if (block.getType() == Material.LODESTONE) {
                block.setType(Material.AIR, true);
            }

            hologramService.hideAetheryte(aetheryte.id());
            world.playSound(blockLoc, Sound.BLOCK_LODESTONE_BREAK, 1.0f, 1.0f);

            if (player.getGameMode() != GameMode.CREATIVE) {
                refundItemToPlayer(player, blockLoc);
            }
            player.sendMessage(messages.aetheryteRemoved(player, aetheryte.name()));
        });
    }

    /**
     * Removes all backing {@link Marker} entities associated with the given Aetheryte in its chunk.
     */
    private static void removeMarkerEntitiesAt(final World world, final Aetheryte target) {
        final Chunk chunk = world.getChunkAt(target.blockX() >> 4, target.blockZ() >> 4);
        for (final Entity entity : chunk.getEntities()) {
            if (entity instanceof Marker) {
                final Optional<Aetheryte> data = AetheryteRegistry.readMarkerData(entity);
                if (data.isPresent() && data.get().id().equals(target.id())) {
                    entity.remove();
                }
            }
        }
    }

    /**
     * Warps a player to the specified Aetheryte using Folia-compatible region checks and {@code teleportAsync}
     * invoked from the player's entity scheduler (#11, #12).
     */
    public void warpToAetheryte(final Player player, final UUID destinationId) {
        warpToAetheryte(player, null, destinationId);
    }

    /**
     * Warps a player from {@code source} (optional) to the specified destination Aetheryte after verifying
     * permissions, costs, and landing safety (#11, #12).
     */
    public void warpToAetheryte(
            final Player player,
            final @Nullable Aetheryte source,
            final UUID destinationId
    ) {
        final Optional<Aetheryte> opt = registry.getById(destinationId);
        if (opt.isEmpty()) {
            messages.send(player, MessageKey.ERROR_NOT_FOUND);
            return;
        }
        final Aetheryte destination = opt.get();

        final WarpEvaluation evaluation = evaluateWarp(player, source, destination);
        if (!evaluation.allowed()) {
            final Component reason = evaluation.denyReason() != null
                    ? evaluation.denyReason()
                    : messages.get(player, MessageKey.ERROR_WARP_REQUIREMENTS_NOT_MET);
            player.sendMessage(messages.errorWarpDenied(player, reason));
            return;
        }

        final World targetWorld = Bukkit.getWorld(destination.worldId());
        if (targetWorld == null) {
            messages.send(player, MessageKey.ERROR_NOT_FOUND);
            return;
        }

        final Location checkLoc = destination.blockLocation(targetWorld);
        final float yaw = player.getYaw();
        final float pitch = player.getPitch();

        Bukkit.getRegionScheduler().execute(plugin, checkLoc, () -> {
            final Optional<Location> safeLanding = resolveSafeLanding(
                    targetWorld,
                    destination.blockX(),
                    destination.blockY(),
                    destination.blockZ(),
                    yaw,
                    pitch
            );
            if (safeLanding.isEmpty()) {
                messages.send(player, MessageKey.ERROR_DESTINATION_UNSAFE);
                return;
            }
            final Location destinationLoc = safeLanding.get();

            player.getScheduler().run(plugin, task -> {
                final boolean paid = Permissions.canBypassWarpCost(player)
                        || costEvaluator.charge(player, source, destination);
                if (!canUse(player) || !paid) {
                    final WarpEvaluation recheck = evaluateWarp(player, source, destination);
                    final Component reason = recheck.denyReason() != null
                            ? recheck.denyReason()
                            : messages.get(player, MessageKey.ERROR_INSUFFICIENT_COST);
                    player.sendMessage(messages.errorWarpDenied(player, reason));
                    return;
                }

                final CompletableFuture<Void> unused = player.teleportAsync(destinationLoc).thenAccept(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        player.sendMessage(messages.warpedTo(player, destination));
                        player.getScheduler().run(plugin, postTask -> {
                            player.playSound(destinationLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                            hologramService.refreshPlayer(player, registry.getByWorld(targetWorld.getUID()));
                        }, null);
                    }
                }).exceptionally(ex -> {
                    plugin.getSLF4JLogger().warn(
                            "Failed to teleport player {} to aetheryte {}",
                            player.getName(),
                            destination.id(),
                            ex
                    );
                    return null;
                });
                if (unused.isCancelled()) {
                    return;
                }
            }, null);
        });
    }

    /**
     * Checks whether the given block is air or replaceable (e.g. tall grass, snow layer).
     */
    public static boolean isReplaceableBlock(final Block block) {
        final Material type = block.getType();
        return type.isAir() || block.isReplaceable();
    }

    /**
     * Resolves a safe landing location for the given Aetheryte coordinates, supporting both Marker-based
     * (passable origin with solid floor below) and block-based Aetherytes.
     */
    public static Optional<Location> resolveSafeLanding(
            final World world,
            final int blockX,
            final int blockY,
            final int blockZ,
            final float yaw,
            final float pitch
    ) {
        final Block origin = world.getBlockAt(blockX, blockY, blockZ);
        final Block above1 = world.getBlockAt(blockX, blockY + 1, blockZ);
        // Case 1: Origin itself is passable (Marker entity style) and has a safe support block below
        if (isPassableAndSafe(origin) && isPassableAndSafe(above1)) {
            final Block floor = world.getBlockAt(blockX, blockY - 1, blockZ);
            if (isSafeFloor(floor)) {
                return Optional.of(new Location(world, blockX + 0.5, blockY + 1.0, blockZ + 0.5, yaw, pitch));
            }
        }
        // Case 2: Origin is a solid safe block (e.g. standing on top of the display hitbox at blockY + 1.0)
        final Block above2 = world.getBlockAt(blockX, blockY + 2, blockZ);
        if (isSafeFloor(origin) && isPassableAndSafe(above1) && isPassableAndSafe(above2)) {
            return Optional.of(new Location(world, blockX + 0.5, blockY + 1.0, blockZ + 0.5, yaw, pitch));
        }
        return Optional.empty();
    }

    /**
     * Verifies whether the Aetheryte coordinates have a valid safe landing position.
     */
    public static boolean isSafeLanding(final World world, final int blockX, final int blockY, final int blockZ) {
        return resolveSafeLanding(world, blockX, blockY, blockZ, 0.0f, 0.0f).isPresent();
    }

    /**
     * Checks whether the given block is a solid, non-hazardous floor suitable for standing on.
     */
    private static boolean isSafeFloor(final Block block) {
        final Material type = block.getType();
        if (type.isAir() || type == Material.LAVA || type == Material.FIRE
                || type == Material.SOUL_FIRE || type == Material.MAGMA_BLOCK
                || type == Material.CACTUS) {
            return false;
        }
        return type.isSolid();
    }

    /**
     * Checks that a single block is passable and not lava or fire.
     */
    private static boolean isPassableAndSafe(final Block block) {
        final Material type = block.getType();
        if (type == Material.LAVA || type == Material.FIRE || type == Material.SOUL_FIRE) {
            return false;
        }
        return block.isPassable() || !type.isSolid();
    }

    /**
     * Consumes a single Aetheryte creation item from the player's hands or storage contents.
     */
    private static boolean consumeOneAetheryteItem(final PlayerInventory inventory) {
        final ItemStack mainHand = inventory.getItemInMainHand();
        if (AetheryteItemFactory.isAetheryteItem(mainHand)) {
            mainHand.setAmount(mainHand.getAmount() - 1);
            return true;
        }
        final ItemStack offHand = inventory.getItemInOffHand();
        if (AetheryteItemFactory.isAetheryteItem(offHand)) {
            offHand.setAmount(offHand.getAmount() - 1);
            return true;
        }
        final @Nullable ItemStack @Nullable [] contents = inventory.getStorageContents();
        if (contents == null) {
            return false;
        }
        for (int i = 0; i < contents.length; i++) {
            final @Nullable ItemStack slot = contents[i];
            if (slot != null && AetheryteItemFactory.isAetheryteItem(slot)) {
                slot.setAmount(slot.getAmount() - 1);
                inventory.setItem(i, slot.getAmount() > 0 ? slot : null);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns one Aetheryte creation item to the player's inventory, or drops it at the fallback location.
     */
    private void refundItemToPlayer(final Player player, final Location fallbackDropLocation) {
        final ItemStack item = AetheryteItemFactory.createItem(1);
        final boolean scheduled = player.getScheduler().run(plugin, task -> {
            final Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                final Location pLoc = player.getLocation();
                final World dropWorld = pLoc != null && pLoc.getWorld() != null ? pLoc.getWorld() : player.getWorld();
                final Location dropLoc = pLoc != null ? pLoc : fallbackDropLocation;
                for (final ItemStack remaining : leftover.values()) {
                    dropWorld.dropItemNaturally(dropLoc, remaining);
                }
            }
        }, () -> Bukkit.getRegionScheduler().execute(plugin, fallbackDropLocation, () -> {
            final World world = fallbackDropLocation.getWorld();
            if (world != null) {
                world.dropItemNaturally(fallbackDropLocation, item);
            }
        })) != null;
        if (!scheduled) {
            Bukkit.getRegionScheduler().execute(plugin, fallbackDropLocation, () -> {
                final World world = fallbackDropLocation.getWorld();
                if (world != null) {
                    world.dropItemNaturally(fallbackDropLocation, item);
                }
            });
        }
    }
}
