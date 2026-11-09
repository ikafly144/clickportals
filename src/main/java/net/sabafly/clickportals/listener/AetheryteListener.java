package net.sabafly.clickportals.listener;

import java.util.List;
import java.util.Optional;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.dialog.AetheryteDialogFactory;
import net.sabafly.clickportals.item.AetheryteItemFactory;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.service.AetheryteService;
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
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

/**
 * Bukkit event listener handling Aetheryte placement, interaction, protection, and world lifecycle (#6, #12).
 */
public final class AetheryteListener implements Listener {

    private final Plugin plugin;
    private final AetheryteRegistry registry;
    private final AetheryteDialogFactory dialogFactory;
    private final HologramService hologramService;

    /**
     * Creates a new {@link AetheryteListener}.
     */
    public AetheryteListener(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final AetheryteDialogFactory dialogFactory,
            final HologramService hologramService
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.dialogFactory = dialogFactory;
        this.hologramService = hologramService;
    }

    /**
     * Loads Aetherytes from the world's PDC when a world is loaded.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldLoad(final WorldLoadEvent event) {
        registry.loadWorld(event.getWorld());
    }

    /**
     * Unloads Aetherytes from the in-memory index when a world is unloaded.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldUnload(final WorldUnloadEvent event) {
        registry.unloadWorld(event.getWorld().getUID());
    }

    /**
     * Handles right-click interactions on placed Aetherytes and with the Aetheryte creation item.
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

        // 1. Check if clicking an existing Aetheryte
        final Optional<Aetheryte> existing = registry.getByPosition(world, clicked.getX(), clicked.getY(), clicked.getZ());
        if (existing.isPresent()) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                dialogFactory.openWarpDialog(player, existing.get());
            }
            return;
        }

        // 2. Check if using the Aetheryte creation item
        if (!AetheryteItemFactory.isAetheryteItem(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        final Block targetBlock = AetheryteService.isReplaceableBlock(clicked)
                ? clicked
                : clicked.getRelative(event.getBlockFace());

        if (registry.getByPosition(world, targetBlock.getX(), targetBlock.getY(), targetBlock.getZ()).isPresent()) {
            player.sendMessage(Messages.errorAlreadyExists());
            return;
        }
        if (!AetheryteService.isReplaceableBlock(targetBlock)) {
            player.sendMessage(Messages.errorSpaceNotEmpty());
            return;
        }

        dialogFactory.openCreateDialog(
                player,
                world,
                targetBlock.getX(),
                targetBlock.getY(),
                targetBlock.getZ()
        );
    }

    /**
     * Prevents direct block placement of the Aetheryte creation item without going through the creation dialog.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (AetheryteItemFactory.isAetheryteItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    /**
     * Protects placed Aetheryte Lodestone blocks from direct breaking.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Block block = event.getBlock();
        if (registry.getByPosition(block.getWorld(), block.getX(), block.getY(), block.getZ()).isPresent()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.errorProtectedBreak());
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
