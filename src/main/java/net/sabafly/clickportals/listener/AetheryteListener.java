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
package net.sabafly.clickportals.listener;

import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.dialog.AetheryteDialogFactory;
import net.sabafly.clickportals.item.AetheryteItemFactory;
import net.sabafly.clickportals.message.MessageKey;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.model.ModelAnimationTrigger;
import net.sabafly.clickportals.service.AetheryteService;
import net.sabafly.clickportals.service.ClearanceChecker;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.visual.HologramService;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

/**
 * Bukkit event listener handling Aetheryte placement, interaction, protection, and world lifecycle
 * (#6, #12, #19, #37, #38, #42, #43).
 */
public final class AetheryteListener implements Listener {

    private final Plugin plugin;
    private final AetheryteRegistry registry;
    private final AetheryteService service;
    private final AetheryteDialogFactory dialogFactory;
    private volatile HologramService hologramService;
    private final Messages messages;

    /**
     * Creates a new {@link AetheryteListener} with default messages.
     */
    public AetheryteListener(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final AetheryteDialogFactory dialogFactory,
            final HologramService hologramService
    ) {
        this(
                plugin,
                registry,
                new AetheryteService(plugin, registry, hologramService),
                dialogFactory,
                hologramService,
                Messages.defaults()
        );
    }

    /**
     * Creates a new {@link AetheryteListener} with explicit {@link Messages}.
     */
    public AetheryteListener(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final AetheryteDialogFactory dialogFactory,
            final HologramService hologramService,
            final Messages messages
    ) {
        this(
                plugin,
                registry,
                new AetheryteService(plugin, registry, hologramService),
                dialogFactory,
                hologramService,
                messages
        );
    }

    /**
     * Creates a new {@link AetheryteListener} with full service dependencies.
     */
    public AetheryteListener(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final AetheryteService service,
            final AetheryteDialogFactory dialogFactory,
            final HologramService hologramService,
            final Messages messages
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.service = service;
        this.dialogFactory = dialogFactory;
        this.hologramService = hologramService;
        this.messages = messages;
    }

    /**
     * Updates the active {@link HologramService} reference when {@code hologram.enabled} changes on reload.
     */
    public void setHologramService(final HologramService hologramService) {
        this.hologramService = hologramService;
    }

    /**
     * Loads Aetherytes from the world's PDC and registers external models when a world is loaded (#46).
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldLoad(final WorldLoadEvent event) {
        final List<Aetheryte> loaded = registry.loadWorld(event.getWorld());
        for (final Aetheryte aetheryte : loaded) {
            final AetheryteVariation var = service.variationRegistry().getOrDefault(aetheryte.variation());
            service.modelService().registerAetheryte(aetheryte, var, event.getWorld());
        }
    }

    /**
     * Unloads Aetherytes and cleans up model trackers when a world is unloaded (#46).
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldUnload(final WorldUnloadEvent event) {
        for (final Aetheryte aetheryte : registry.getByWorld(event.getWorld().getUID())) {
            service.modelService().unregisterAetheryte(aetheryte.id());
        }
        registry.unloadWorld(event.getWorld().getUID());
    }

    /**
     * Handles right-click interactions on placed Aetherytes and with Aetheryte creation items (#37, #38, #42, #43).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        final Player player = event.getPlayer();
        final World world = clicked.getWorld();

        // 1. Check if clicking an existing Aetheryte directly or on the support face immediately below/adjacent
        final Optional<Aetheryte> existingDirect = registry.getByPosition(world, clicked.getX(), clicked.getY(), clicked.getZ());
        if (existingDirect.isPresent()) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                final Aetheryte aetheryte = existingDirect.get();
                service.modelService().triggerAnimation(aetheryte.id(), ModelAnimationTrigger.ON_INTERACT);
                dialogFactory.openWarpDialog(player, aetheryte);
            }
            return;
        }

        final Block relativeBlock = clicked.getRelative(event.getBlockFace());
        final Optional<Aetheryte> existingRelative = registry.getByPosition(
                world,
                relativeBlock.getX(),
                relativeBlock.getY(),
                relativeBlock.getZ()
        );
        final Optional<String> itemVariationOpt = AetheryteItemFactory.variationIdOf(event.getItem());
        if (existingRelative.isPresent()
                && !isInteractableBlock(clicked)
                && itemVariationOpt.isEmpty()) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                final Aetheryte aetheryte = existingRelative.get();
                service.modelService().triggerAnimation(aetheryte.id(), ModelAnimationTrigger.ON_INTERACT);
                dialogFactory.openWarpDialog(player, aetheryte);
            }
            return;
        }

        // 2. Check if using an Aetheryte creation item
        if (itemVariationOpt.isEmpty()) {
            return;
        }
        final String variationId = itemVariationOpt.get();

        event.setCancelled(true);
        if (!AetheryteService.canPlace(player)) {
            messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return;
        }

        final Block targetBlock = AetheryteService.isReplaceableBlock(clicked)
                ? clicked
                : relativeBlock;

        if (registry.getByPosition(world, targetBlock.getX(), targetBlock.getY(), targetBlock.getZ()).isPresent()) {
            messages.send(player, MessageKey.ERROR_ALREADY_EXISTS);
            return;
        }

        final Optional<Component> limitError = service.checkPlacementLimit(player, variationId);
        if (limitError.isPresent()) {
            player.sendMessage(limitError.get());
            return;
        }

        final Optional<Component> cooldownError = service.checkPlacementCooldown(player, variationId);
        if (cooldownError.isPresent()) {
            player.sendMessage(cooldownError.get());
            return;
        }

        final AetheryteVariation variation = service.variationRegistry().getOrDefault(variationId);
        if (!ClearanceChecker.hasClearance(
                world,
                targetBlock.getX(),
                targetBlock.getY(),
                targetBlock.getZ(),
                variation
        )) {
            service.sendClearanceError(player, variation);
            return;
        }

        dialogFactory.openCreateDialog(
                player,
                world,
                targetBlock.getX(),
                targetBlock.getY(),
                targetBlock.getZ(),
                variationId
        );
    }

    /**
     * Prevents direct block placement of the Aetheryte creation item or placing blocks into an occupied Aetheryte space.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (AetheryteItemFactory.isAetheryteItem(event.getItemInHand())) {
            event.setCancelled(true);
            return;
        }
        final Block placed = event.getBlockPlaced();
        if (registry.getByPosition(placed.getWorld(), placed.getX(), placed.getY(), placed.getZ()).isPresent()) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), MessageKey.ERROR_ALREADY_EXISTS);
        }
    }

    /**
     * Protects placed Aetheryte positions from direct breaking.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Block block = event.getBlock();
        if (registry.getByPosition(block.getWorld(), block.getX(), block.getY(), block.getZ()).isPresent()) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), MessageKey.ERROR_PROTECTED_BREAK);
        }
    }

    /**
     * Protects placed Aetherytes from block explosions.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent event) {
        protectExplosionBlocks(event.blockList());
    }

    /**
     * Protects placed Aetherytes from entity explosions.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent event) {
        protectExplosionBlocks(event.blockList());
    }

    /**
     * Prevents pistons from pushing placed Aetherytes.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(final BlockPistonExtendEvent event) {
        if (containsAetheryte(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents sticky pistons from pulling placed Aetherytes.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(final BlockPistonRetractEvent event) {
        if (containsAetheryte(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    /**
     * Refreshes client-side holograms when a player joins the server.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        scheduleHologramRefresh(event.getPlayer());
    }

    /**
     * Refreshes client-side holograms when a player changes worlds.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(final PlayerChangedWorldEvent event) {
        scheduleHologramRefresh(event.getPlayer());
    }

    /**
     * Refreshes client-side holograms when a player respawns.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(final PlayerRespawnEvent event) {
        scheduleHologramRefresh(event.getPlayer());
    }

    /**
     * Refreshes client-side holograms when a player changes their client language setting (#19).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLocaleChange(final PlayerLocaleChangeEvent event) {
        scheduleHologramRefresh(event.getPlayer());
    }

    /**
     * Cleans up per-player hologram interaction state and transient placement reservations when a player quits.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final java.util.UUID playerId = event.getPlayer().getUniqueId();
        hologramService.removePlayer(playerId);
        service.removePlayer(playerId);
    }

    /**
     * Schedules a delayed hologram refresh on the player's entity scheduler.
     */
    private void scheduleHologramRefresh(final Player player) {
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) {
                hologramService.refreshPlayer(player, registry.getByWorld(player.getWorld().getUID()));
            }
        }, null, 5L);
    }

    /**
     * Removes any registered Aetheryte blocks from an explosion's block list.
     */
    private void protectExplosionBlocks(final List<Block> blocks) {
        blocks.removeIf(block -> registry.getByPosition(block.getWorld(), block.getX(), block.getY(), block.getZ()).isPresent());
    }

    /**
     * Checks whether the given block has a native right-click interaction (e.g. chest, door, button).
     */
    @SuppressWarnings("deprecation")
    private static boolean isInteractableBlock(final Block block) {
        return block.getType().isInteractable();
    }

    /**
     * Checks whether any block in the list is a registered Aetheryte.
     */
    private boolean containsAetheryte(final List<Block> blocks) {
        for (final Block block : blocks) {
            if (registry.getByPosition(block.getWorld(), block.getX(), block.getY(), block.getZ()).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
