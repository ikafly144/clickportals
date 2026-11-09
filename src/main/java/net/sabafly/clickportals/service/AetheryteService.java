package net.sabafly.clickportals.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.item.AetheryteItemFactory;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.visual.HologramService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Folia-safe application service for placing, removing, and warping between Aetherytes (#11, #12).
 */
public final class AetheryteService {

    private static final int MAX_NAME_LENGTH = 32;

    private final Plugin plugin;
    private final AetheryteRegistry registry;
    private final HologramService hologramService;

    public AetheryteService(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final HologramService hologramService
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.hologramService = hologramService;
    }

    public AetheryteRegistry registry() {
        return registry;
    }

    public HologramService hologramService() {
        return hologramService;
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
     * Places a new Aetheryte at the target block coordinates using Folia's EntityScheduler and RegionScheduler.
     */
    public void placeAetheryte(
            final Player player,
            final World world,
            final int blockX,
            final int blockY,
            final int blockZ,
            final String rawName
    ) {
        final Optional<String> validName = sanitizeName(rawName);
        if (validName.isEmpty()) {
            player.sendMessage(Messages.errorInvalidName());
            return;
        }
        final String name = validName.get();

        if (registry.getByPosition(world, blockX, blockY, blockZ).isPresent()) {
            player.sendMessage(Messages.errorAlreadyExists());
            return;
        }

        final UUID ownerId = player.getUniqueId();
        final Location targetLocation = new Location(world, blockX, blockY, blockZ);

        // Step 1: Consume the Aetheryte item on the player's entity thread (unless in Creative mode).
        player.getScheduler().run(plugin, task -> {
            final boolean creative = player.getGameMode() == GameMode.CREATIVE;
            if (!creative && !consumeOneAetheryteItem(player.getInventory())) {
                player.sendMessage(Messages.errorMissingItem());
                return;
            }

            // Step 2: Place the Lodestone block and persist to World PDC on the target region thread.
            Bukkit.getRegionScheduler().execute(plugin, targetLocation, () -> {
                if (registry.getByPosition(world, blockX, blockY, blockZ).isPresent()) {
                    if (!creative) {
                        refundItemToPlayer(player, targetLocation);
                    }
                    player.sendMessage(Messages.errorAlreadyExists());
                    return;
                }

                final Block block = world.getBlockAt(blockX, blockY, blockZ);
                if (!isReplaceableBlock(block)) {
                    if (!creative) {
                        refundItemToPlayer(player, targetLocation);
                    }
                    player.sendMessage(Messages.errorSpaceNotEmpty());
                    return;
                }

                block.setType(Material.LODESTONE, true);

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
                registry.register(world, aetheryte);
                hologramService.showAetheryte(aetheryte);

                world.playSound(
                        aetheryte.teleportDestination(world, 0.0f, 0.0f),
                        Sound.BLOCK_LODESTONE_PLACE,
                        1.0f,
                        1.0f
                );
                player.sendMessage(Messages.aetheryteCreated(aetheryte));
            });
        }, null);
    }

    /**
     * Removes an existing Aetheryte and returns the creation item to the player.
     */
    public void removeAetheryte(final Player player, final UUID aetheryteId) {
        final Optional<Aetheryte> opt = registry.getById(aetheryteId);
        if (opt.isEmpty()) {
            player.sendMessage(Messages.errorNotFound());
            return;
        }
        final Aetheryte aetheryte = opt.get();
        final World world = Bukkit.getWorld(aetheryte.worldId());
        if (world == null) {
            player.sendMessage(Messages.errorNotFound());
            return;
        }

        final Location blockLoc = aetheryte.blockLocation(world);
        Bukkit.getRegionScheduler().execute(plugin, blockLoc, () -> {
            final Optional<Aetheryte> removed = registry.unregister(world, aetheryte.id());
            if (removed.isEmpty()) {
                player.sendMessage(Messages.errorNotFound());
                return;
            }

            final Block block = world.getBlockAt(aetheryte.blockX(), aetheryte.blockY(), aetheryte.blockZ());
            if (block.getType() == Material.LODESTONE) {
                block.setType(Material.AIR, true);
            }

            hologramService.hideAetheryte(aetheryte.id());
            world.playSound(blockLoc, Sound.BLOCK_LODESTONE_BREAK, 1.0f, 1.0f);

            if (player.getGameMode() != GameMode.CREATIVE) {
                refundItemToPlayer(player, blockLoc);
            }
            player.sendMessage(Messages.aetheryteRemoved(aetheryte.name()));
        });
    }

    /**
     * Warps a player to the specified Aetheryte using Folia-compatible region checks and {@code teleportAsync}.
     */
    public void warpToAetheryte(final Player player, final UUID destinationId) {
        final Optional<Aetheryte> opt = registry.getById(destinationId);
        if (opt.isEmpty()) {
            player.sendMessage(Messages.errorNotFound());
            return;
        }
        final Aetheryte destination = opt.get();
        final World targetWorld = Bukkit.getWorld(destination.worldId());
        if (targetWorld == null) {
            player.sendMessage(Messages.errorNotFound());
            return;
        }

        final Location destinationLoc = destination.teleportDestination(
                targetWorld,
                player.getYaw(),
                player.getPitch()
        );

        Bukkit.getRegionScheduler().execute(plugin, destinationLoc, () -> {
            if (!isSafeLanding(targetWorld, destination.blockX(), destination.blockY(), destination.blockZ())) {
                player.sendMessage(Messages.errorDestinationUnsafe());
                return;
            }

            final CompletableFuture<Void> unused = player.teleportAsync(destinationLoc).thenAccept(success -> {
                if (Boolean.TRUE.equals(success)) {
                    player.sendMessage(Messages.warpedTo(destination));
                    player.getScheduler().run(plugin, task -> {
                        player.playSound(destinationLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        hologramService.refreshPlayer(player, registry.getByWorld(targetWorld.getUID()));
                    }, null);
                }
            }).exceptionally(ex -> {
                plugin.getSLF4JLogger().warn("Failed to teleport player {} to aetheryte {}", player.getName(), destination.id(), ex);
                return null;
            });
            if (unused.isCancelled()) {
                return;
            }
        });
    }

    public static boolean isReplaceableBlock(final Block block) {
        final Material type = block.getType();
        return type.isAir() || block.isReplaceable();
    }

    public static boolean isSafeLanding(final World world, final int blockX, final int blockY, final int blockZ) {
        final Block base = world.getBlockAt(blockX, blockY, blockZ);
        if (base.getType() != Material.LODESTONE) {
            return false;
        }
        final Block feet = world.getBlockAt(blockX, blockY + 1, blockZ);
        final Block head = world.getBlockAt(blockX, blockY + 2, blockZ);
        return isPassableAndSafe(feet) && isPassableAndSafe(head);
    }

    private static boolean isPassableAndSafe(final Block block) {
        final Material type = block.getType();
        if (type == Material.LAVA || type == Material.FIRE || type == Material.SOUL_FIRE) {
            return false;
        }
        return block.isPassable() || !type.isSolid();
    }

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
