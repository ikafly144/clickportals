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

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntSupplier;
import java.util.function.ToLongFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry.RegistrationOutcome;
import net.sabafly.clickportals.cost.CostBundle;
import net.sabafly.clickportals.cost.CostFormatter;
import net.sabafly.clickportals.cost.CostItemQuote;
import net.sabafly.clickportals.cost.CostQuote;
import net.sabafly.clickportals.economy.EconomyProvider;
import net.sabafly.clickportals.economy.NoopEconomyProvider;
import net.sabafly.clickportals.item.AetheryteItemFactory;
import net.sabafly.clickportals.message.MessageKey;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.model.AetheryteModelService;
import net.sabafly.clickportals.model.ModelAnimationTrigger;
import net.sabafly.clickportals.model.NoopAetheryteModelService;
import net.sabafly.clickportals.permission.Permissions;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.ClearanceSection;
import net.sabafly.clickportals.variation.VariationRegistry;
import net.sabafly.clickportals.visual.HologramService;
import net.sabafly.clickportals.visual.PacketEventsHologramService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Folia-safe application service for placing, removing, evaluating, and warping between Aetherytes
 * (#6, #11, #12, #17, #19, #36, #37, #38, #41, #42, #43).
 */
public final class AetheryteService {

    private static final int MAX_NAME_LENGTH = 32;

    /**
     * Player {@link PersistentDataContainer} key storing the server/world tick when the player last placed an Aetheryte (#43).
     */
    public static final NamespacedKey LAST_PLACE_TICK_KEY = new NamespacedKey("clickportals", "last_place_tick");

    /**
     * Callback for sending client-side item cooldown packets when an Aetheryte placement cooldown is applied (#43).
     */
    @FunctionalInterface
    public interface CooldownPacketSender {
        /**
         * Sends a visual cooldown overlay packet of {@code cooldownTicks} duration to {@code player}.
         */
        void sendCooldown(Player player, int cooldownTicks);
    }

    /**
     * Result of evaluating whether a player can warp to a specific Aetheryte and at what cost (#12, #13).
     */
    public record WarpEvaluation(
            boolean allowed,
            Component costDisplay,
            @Nullable Component denyReason,
            @Nullable DenyReason denyReasonCode
    ) {
        /**
         * Category of denial for warp evaluation (#13, #26).
         */
        public enum DenyReason {
            NO_ROUTE,
            TOO_FAR,
            TOO_CLOSE,
            INSUFFICIENT_COST,
            NOT_FOUND,
            NO_PERMISSION,
            OTHER
        }

        public WarpEvaluation(final boolean allowed, final Component costDisplay, final @Nullable Component denyReason) {
            this(allowed, costDisplay, denyReason, allowed ? null : DenyReason.OTHER);
        }

        /**
         * Creates an allowed evaluation with the given cost display.
         */
        public static WarpEvaluation permitted(final Component costDisplay) {
            return new WarpEvaluation(true, costDisplay, null, null);
        }

        /**
         * Creates a denied evaluation with the given cost display and rejection reason.
         */
        public static WarpEvaluation denied(final Component costDisplay, final Component denyReason) {
            return new WarpEvaluation(false, costDisplay, denyReason, DenyReason.OTHER);
        }

        /**
         * Creates a denied evaluation with the given cost display, rejection reason, and structured code.
         */
        public static WarpEvaluation denied(
                final Component costDisplay,
                final Component denyReason,
                final @Nullable DenyReason code
        ) {
            return new WarpEvaluation(false, costDisplay, denyReason, code != null ? code : DenyReason.OTHER);
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

        /**
         * Refunds previously charged costs if teleportation fails or is cancelled (#15).
         * Must be called on the player's entity thread.
         *
         * @return {@code true} if refund succeeded or nothing to refund, {@code false} if refund failed and should be compensated
         */
        default boolean refund(final Player player, final @Nullable Aetheryte source, final Aetheryte destination) {
            return true;
        }

        /**
         * Commits and finalizes charge state upon successful teleportation (#15).
         */
        default void commit(final Player player, final @Nullable Aetheryte source, final Aetheryte destination) {
        }

        /**
         * Pops and returns unrefunded cost items for the player and destination if refund could not be applied (#15).
         */
        default List<CostItemQuote> popPendingCharge(final Player player, final Aetheryte destination) {
            return List.of();
        }
    }

    private final Plugin plugin;
    private final AetheryteRegistry registry;
    private final VariationRegistry variationRegistry;
    private volatile HologramService hologramService;
    private volatile AetheryteModelService modelService;
    private final Messages messages;
    private final IntSupplier globalMaxPerPlayerSupplier;
    private final IntSupplier globalCooldownTicksSupplier;
    private final ToLongFunction<Player> tickSupplier;
    private final CooldownPacketSender cooldownPacketSender;
    private volatile WarpCostEvaluator costEvaluator;
    private volatile EconomyProvider economyProvider = NoopEconomyProvider.INSTANCE;
    private final Set<UUID> pendingPlacements = ConcurrentHashMap.newKeySet();
    private final Set<UUID> inFlightWarps = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<CostItemQuote>> pendingRefunds = new ConcurrentHashMap<>();
    private static final double BASE_INTERACT_REACH = 6.0;

    /**
     * Creates a new {@link AetheryteService} with default variations, messages, and free cost evaluation.
     */
    public AetheryteService(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final HologramService hologramService
    ) {
        this(plugin, registry, VariationRegistry.defaults(), hologramService, Messages.defaults(), () -> -1, () -> 0);
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
        this(
                plugin,
                registry,
                VariationRegistry.defaults(),
                hologramService,
                Messages.defaults(),
                () -> -1,
                () -> 0,
                AetheryteService::resolveCurrentTick,
                PacketEventsHologramService::sendPlacementCooldown,
                costEvaluator
        );
    }

    /**
     * Creates a new {@link AetheryteService} with the given {@link VariationRegistry}, {@link Messages},
     * and global limit supplier.
     */
    public AetheryteService(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final VariationRegistry variationRegistry,
            final HologramService hologramService,
            final Messages messages,
            final IntSupplier globalMaxPerPlayerSupplier
    ) {
        this(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                globalMaxPerPlayerSupplier,
                () -> 0
        );
    }

    /**
     * Creates a new {@link AetheryteService} with the given {@link VariationRegistry}, {@link Messages},
     * global limit supplier, and global placement cooldown supplier (#43).
     */
    public AetheryteService(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final VariationRegistry variationRegistry,
            final HologramService hologramService,
            final Messages messages,
            final IntSupplier globalMaxPerPlayerSupplier,
            final IntSupplier globalCooldownTicksSupplier
    ) {
        this(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                globalMaxPerPlayerSupplier,
                globalCooldownTicksSupplier,
                AetheryteService::resolveCurrentTick,
                PacketEventsHologramService::sendPlacementCooldown,
                (player, source, destination) -> WarpEvaluation.permitted(messages.freeCostLabel(player))
        );
    }

    /**
     * Creates a new {@link AetheryteService} with full dependencies and custom {@link WarpCostEvaluator}.
     */
    public AetheryteService(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final VariationRegistry variationRegistry,
            final HologramService hologramService,
            final Messages messages,
            final IntSupplier globalMaxPerPlayerSupplier,
            final WarpCostEvaluator costEvaluator
    ) {
        this(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                globalMaxPerPlayerSupplier,
                () -> 0,
                AetheryteService::resolveCurrentTick,
                PacketEventsHologramService::sendPlacementCooldown,
                costEvaluator
        );
    }

    /**
     * Creates a new {@link AetheryteService} with full dependencies including custom tick and cooldown packet suppliers.
     */
    public AetheryteService(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final VariationRegistry variationRegistry,
            final HologramService hologramService,
            final Messages messages,
            final IntSupplier globalMaxPerPlayerSupplier,
            final IntSupplier globalCooldownTicksSupplier,
            final ToLongFunction<Player> tickSupplier,
            final CooldownPacketSender cooldownPacketSender,
            final WarpCostEvaluator costEvaluator
    ) {
        this(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                NoopAetheryteModelService.INSTANCE,
                messages,
                globalMaxPerPlayerSupplier,
                globalCooldownTicksSupplier,
                tickSupplier,
                cooldownPacketSender,
                costEvaluator
        );
    }

    /**
     * Creates a new {@link AetheryteService} with full dependencies including {@link AetheryteModelService} (#46).
     */
    public AetheryteService(
            final Plugin plugin,
            final AetheryteRegistry registry,
            final VariationRegistry variationRegistry,
            final HologramService hologramService,
            final AetheryteModelService modelService,
            final Messages messages,
            final IntSupplier globalMaxPerPlayerSupplier,
            final IntSupplier globalCooldownTicksSupplier,
            final ToLongFunction<Player> tickSupplier,
            final CooldownPacketSender cooldownPacketSender,
            final WarpCostEvaluator costEvaluator
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.variationRegistry = variationRegistry;
        this.hologramService = hologramService;
        this.modelService = modelService;
        this.messages = messages;
        this.globalMaxPerPlayerSupplier = globalMaxPerPlayerSupplier;
        this.globalCooldownTicksSupplier = globalCooldownTicksSupplier;
        this.tickSupplier = tickSupplier;
        this.cooldownPacketSender = cooldownPacketSender;
        this.costEvaluator = costEvaluator;
    }

    /**
     * Resolves the current epoch tick for placement cooldown calculation (wall-clock monotonic, 50ms per tick) (#43).
     */
    public static long resolveCurrentTick(final Player player) {
        return System.currentTimeMillis() / 50L;
    }

    /**
     * Cleans up transient in-flight placement reservations for the given player UUID when quitting or reloading (#43).
     */
    public void removePlayer(final UUID playerId) {
        pendingPlacements.remove(playerId);
        inFlightWarps.remove(playerId);
    }

    /**
     * Returns the backing {@link AetheryteRegistry}.
     */
    public AetheryteRegistry registry() {
        return registry;
    }

    /**
     * Returns the backing {@link VariationRegistry}.
     */
    public VariationRegistry variationRegistry() {
        return variationRegistry;
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
     * Returns the active {@link AetheryteModelService} (#46).
     */
    public AetheryteModelService modelService() {
        return modelService;
    }

    /**
     * Updates the active {@link AetheryteModelService} reference on reload (#46).
     */
    public void setModelService(final AetheryteModelService modelService) {
        this.modelService = modelService;
    }

    /**
     * Returns the active {@link WarpCostEvaluator}.
     */
    public WarpCostEvaluator costEvaluator() {
        return costEvaluator;
    }

    /**
     * Updates the active {@link WarpCostEvaluator} on reload.
     */
    public void setCostEvaluator(final WarpCostEvaluator costEvaluator) {
        this.costEvaluator = costEvaluator;
    }

    /**
     * Returns the active {@link EconomyProvider}.
     */
    public EconomyProvider economyProvider() {
        return economyProvider;
    }

    /**
     * Updates the active {@link EconomyProvider} on reload (#16).
     */
    public void setEconomyProvider(final EconomyProvider economyProvider) {
        this.economyProvider = economyProvider;
    }

    /**
     * Checks whether the player is in the same world and within interaction reach of the given Aetheryte,
     * scaling the reach allowance by the variation hitbox dimensions (#40).
     */
    public boolean isWithinReach(final Player player, final Aetheryte aetheryte) {
        final Location loc = player.getLocation();
        if (loc == null) {
            return false;
        }
        final World world = loc.getWorld();
        if (world == null || !world.getUID().equals(aetheryte.worldId())) {
            return false;
        }
        final AetheryteVariation variation = variationRegistry.getOrDefault(aetheryte.variation());
        final double hitboxAllowance = Math.max(variation.hitbox().width(), variation.hitbox().height());
        final double maxReach = BASE_INTERACT_REACH + hitboxAllowance;
        return loc.distanceSquared(aetheryte.markerLocation(world)) <= maxReach * maxReach;
    }

    /**
     * Processes any pending placement or warp cost refunds for a reconnecting player (#15).
     */
    public void processPendingRefunds(final Player player) {
        final List<CostItemQuote> items = pendingRefunds.get(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }
        final ScheduledTask task = player.getScheduler().run(plugin, t -> {
            final List<CostItemQuote> currentItems = pendingRefunds.get(player.getUniqueId());
            if (currentItems == null || currentItems.isEmpty()) {
                return;
            }
            final List<CostItemQuote> snapshot = new ArrayList<>(currentItems);
            final CostQuote quote = new CostQuote(snapshot, true);
            final CostBundle.RefundResult result = new CostBundle(List.of()).refundWithResult(player, quote, economyProvider);
            for (final CostItemQuote refundedItem : result.refundedItems()) {
                currentItems.remove(refundedItem);
            }
            if (currentItems.isEmpty()) {
                pendingRefunds.remove(player.getUniqueId(), currentItems);
            }
            if (result.success()) {
                plugin.getSLF4JLogger().info("Processed pending placement cost refund for returning player {}", player.getName());
            } else {
                plugin.getSLF4JLogger().warn("Partially or wholly failed to process pending cost refund for player {}", player.getName());
            }
        }, () -> {
            // Player retired / disconnected before execution; keep pendingRefunds intact
        });
        if (task == null) {
            plugin.getSLF4JLogger().warn("Could not schedule pending refund task for player {}", player.getName());
        }
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
     * Checks whether the player has already reached the global or variation-specific placement limit (#43).
     *
     * @return an error {@link Component} if a limit is reached, or {@link Optional#empty()} if allowed
     */
    public Optional<Component> checkPlacementLimit(final Player player, final String variationId) {
        if (Permissions.canBypassLimit(player)) {
            return Optional.empty();
        }
        final UUID ownerId = player.getUniqueId();
        final String normalizedVar = Aetheryte.normalizeVariation(variationId);
        final AetheryteVariation variation = variationRegistry.getOrDefault(normalizedVar);

        final int maxGlobal = globalMaxPerPlayerSupplier.getAsInt();
        if (maxGlobal >= 0 && registry.countByOwner(ownerId) >= maxGlobal) {
            return Optional.of(messages.errorLimitReachedGlobal(player, maxGlobal));
        }

        final int maxVariation = variation.limit().maxPerPlayer();
        if (maxVariation >= 0 && registry.countByOwnerAndVariation(ownerId, normalizedVar) >= maxVariation) {
            return Optional.of(messages.errorLimitReachedVariation(player, variation.displayName(), maxVariation));
        }

        return Optional.empty();
    }

    /**
     * Returns the effective placement cooldown in ticks for the given variation, using the variation override
     * when {@code >= 0} or falling back to the global {@code limits.placement-cooldown-ticks} (#43).
     */
    public int effectiveCooldownTicks(final String variationId) {
        final AetheryteVariation variation = variationRegistry.getOrDefault(Aetheryte.normalizeVariation(variationId));
        final int variationCooldown = variation.limit().cooldownTicks();
        if (variationCooldown >= 0) {
            return variationCooldown;
        }
        return Math.max(0, globalCooldownTicksSupplier.getAsInt());
    }

    /**
     * Calculates the remaining placement cooldown in ticks for {@code player} based on the last placement tick
     * stored in the player's {@link PersistentDataContainer} (#43).
     *
     * @return remaining cooldown ticks ({@code > 0} if still on cooldown, {@code 0} if ready or bypassed)
     */
    public int getRemainingCooldownTicks(final Player player, final String variationId) {
        if (Permissions.canBypassLimit(player)) {
            return 0;
        }
        final int cooldownTicks = effectiveCooldownTicks(variationId);
        if (cooldownTicks <= 0) {
            return 0;
        }
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc == null) {
            return 0;
        }
        final Long lastPlaceTick = pdc.get(LAST_PLACE_TICK_KEY, PersistentDataType.LONG);
        if (lastPlaceTick == null) {
            return 0;
        }
        final long currentTick = tickSupplier.applyAsLong(player);
        final long elapsed = currentTick - lastPlaceTick;
        if (elapsed < 0L || elapsed >= cooldownTicks) {
            return 0;
        }
        return (int) (cooldownTicks - elapsed);
    }

    /**
     * Checks whether the player is currently on placement cooldown, re-syncing the client-side item cooldown
     * overlay packet if still on cooldown (#43).
     *
     * @return a localized error {@link Component} if on cooldown, or {@link Optional#empty()} if allowed
     */
    public Optional<Component> checkPlacementCooldown(final Player player, final String variationId) {
        final int remainingTicks = getRemainingCooldownTicks(player, variationId);
        if (remainingTicks <= 0) {
            return Optional.empty();
        }
        cooldownPacketSender.sendCooldown(player, remainingTicks);
        return Optional.of(messages.errorPlacementCooldown(player, remainingTicks));
    }

    /**
     * Records the current tick in the player's {@link PersistentDataContainer} and sends the client-side
     * cooldown packet via PacketEvents when an Aetheryte is placed (#43).
     */
    public void recordPlacementCooldown(final Player player, final String variationId) {
        if (Permissions.canBypassLimit(player)) {
            return;
        }
        final int cooldownTicks = effectiveCooldownTicks(variationId);
        if (cooldownTicks <= 0) {
            return;
        }
        final long currentTick = tickSupplier.applyAsLong(player);
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc != null) {
            pdc.set(LAST_PLACE_TICK_KEY, PersistentDataType.LONG, currentTick);
        }
        cooldownPacketSender.sendCooldown(player, cooldownTicks);
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
        final WarpEvaluation evaluation = costEvaluator.evaluate(player, source, destination);
        if (!canUse(player)) {
            return WarpEvaluation.denied(
                    evaluation.costDisplay(),
                    messages.get(player, MessageKey.ERROR_NO_WARP_PERMISSION),
                    WarpEvaluation.DenyReason.NO_PERMISSION
            );
        }
        if (Permissions.canBypassWarpCost(player)) {
            final Component originalCost = evaluation.costDisplay();
            final String plainCost = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(originalCost);
            final String plainFree = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(messages.freeCostLabel(player));
            final Component bypassedCostDisplay = plainCost.equals(plainFree)
                    ? originalCost
                    : messages.get(player, MessageKey.COST_BYPASSED, Placeholder.component("cost", originalCost));

            if (evaluation.denyReasonCode() == WarpEvaluation.DenyReason.INSUFFICIENT_COST) {
                return WarpEvaluation.permitted(bypassedCostDisplay);
            }
            if (evaluation.allowed()) {
                return WarpEvaluation.permitted(bypassedCostDisplay);
            }
            return new WarpEvaluation(false, bypassedCostDisplay, evaluation.denyReason(), evaluation.denyReasonCode());
        }
        return evaluation;
    }

    /**
     * Places a new Aetheryte of the {@code default} variation (#6, #11, #12).
     */
    public void placeAetheryte(
            final Player player,
            final World world,
            final int blockX,
            final int blockY,
            final int blockZ,
            final String rawName
    ) {
        placeAetheryte(player, world, blockX, blockY, blockZ, rawName, Aetheryte.DEFAULT_VARIATION);
    }

    /**
     * Places a new Aetheryte of the specified variation backed by a non-ticking {@link Marker} entity
     * at the target block coordinates using Folia's EntityScheduler and RegionScheduler (#6, #11, #12, #37, #42, #43).
     */
    public void placeAetheryte(
            final Player player,
            final World world,
            final int blockX,
            final int blockY,
            final int blockZ,
            final String rawName,
            final String variationId
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
        final String normalizedVar = Aetheryte.normalizeVariation(variationId);
        final AetheryteVariation variation = variationRegistry.getOrDefault(normalizedVar);

        if (registry.getByPosition(world, blockX, blockY, blockZ).isPresent()) {
            messages.send(player, MessageKey.ERROR_ALREADY_EXISTS);
            return;
        }

        final Optional<Component> preLimitError = checkPlacementLimit(player, normalizedVar);
        if (preLimitError.isPresent()) {
            player.sendMessage(preLimitError.get());
            return;
        }

        final UUID ownerId = player.getUniqueId();
        final boolean bypassLimit = Permissions.canBypassLimit(player);
        final Location targetLocation = new Location(world, blockX, blockY, blockZ);

        // Step 1: Check cooldown and consume the Aetheryte item of the matching variation on the player's entity thread.
        player.getScheduler().run(plugin, task -> {
            if (!bypassLimit && !pendingPlacements.add(ownerId)) {
                final int remaining = Math.max(1, getRemainingCooldownTicks(player, normalizedVar));
                cooldownPacketSender.sendCooldown(player, remaining);
                player.sendMessage(messages.errorPlacementCooldown(player, remaining));
                return;
            }

            final Optional<Component> cooldownError = checkPlacementCooldown(player, normalizedVar);
            if (cooldownError.isPresent()) {
                pendingPlacements.remove(ownerId);
                player.sendMessage(cooldownError.get());
                return;
            }

            final boolean creative = player.getGameMode() == GameMode.CREATIVE;
            final boolean bypassCost = Permissions.canBypassPlaceCost(player);
            final CostBundle placementCostBundle = new CostBundle(variation.placement().cost());
            final CostQuote placementQuote;
            final EconomyProvider activeEconomy = this.economyProvider;
            if (!creative && !bypassCost && !placementCostBundle.isEmpty()) {
                final Map<String, Double> costVars = Map.of(
                        "owned_count", (double) registry.countByOwner(ownerId),
                        "variation_count", (double) registry.countByOwnerAndVariation(ownerId, normalizedVar)
                );
                placementQuote = placementCostBundle.quote(player, costVars, activeEconomy);
                if (!placementQuote.affordable()) {
                    pendingPlacements.remove(ownerId);
                    final Component reason = CostFormatter.formatMissingRequirements(player, placementQuote, activeEconomy, messages);
                    player.sendMessage(messages.get(player, MessageKey.ERROR_PLACEMENT_COST_INSUFFICIENT, Placeholder.component("reason", reason)));
                    return;
                }
            } else {
                placementQuote = CostQuote.FREE;
            }

            if (!creative && !consumeOneAetheryteItem(player.getInventory(), normalizedVar)) {
                pendingPlacements.remove(ownerId);
                messages.send(player, MessageKey.ERROR_MISSING_ITEM);
                return;
            }

            final CostQuote chargedPlacementQuote;
            if (!creative && !bypassCost && !placementCostBundle.isEmpty()) {
                final CostBundle.ChargeResult chargeResult = placementCostBundle.chargeWithResult(player, placementQuote, activeEconomy);
                if (!chargeResult.success()) {
                    refundItemToPlayer(player, targetLocation, normalizedVar);
                    pendingPlacements.remove(ownerId);
                    final Component reason = CostFormatter.formatMissingRequirements(player, placementQuote, activeEconomy, messages);
                    player.sendMessage(messages.get(player, MessageKey.ERROR_PLACEMENT_COST_INSUFFICIENT, Placeholder.component("reason", reason)));
                    return;
                }
                chargedPlacementQuote = chargeResult.chargedQuote();
            } else {
                chargedPlacementQuote = CostQuote.FREE;
            }

            // Step 2: Validate clearance, check limits atomically, and spawn the Marker entity on the region thread.
            Bukkit.getRegionScheduler().execute(plugin, targetLocation, () -> {
                Aetheryte placedAetheryte = null;
                try {
                    if (registry.getByPosition(world, blockX, blockY, blockZ).isPresent()) {
                        refundPlacement(player, targetLocation, normalizedVar, creative, bypassCost, placementCostBundle, chargedPlacementQuote, activeEconomy);
                        pendingPlacements.remove(ownerId);
                        messages.send(player, MessageKey.ERROR_ALREADY_EXISTS);
                        return;
                    }

                    if (!ClearanceChecker.hasClearance(world, blockX, blockY, blockZ, variation)) {
                        refundPlacement(player, targetLocation, normalizedVar, creative, bypassCost, placementCostBundle, chargedPlacementQuote, activeEconomy);
                        pendingPlacements.remove(ownerId);
                        sendClearanceError(player, variation);
                        return;
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
                            System.currentTimeMillis(),
                            normalizedVar
                    );

                    final int effectiveMaxGlobal = bypassLimit ? -1 : globalMaxPerPlayerSupplier.getAsInt();
                    final int effectiveMaxVariation = bypassLimit ? -1 : variation.limit().maxPerPlayer();
                    final RegistrationOutcome outcome = registry.tryRegister(
                            world,
                            aetheryte,
                            effectiveMaxGlobal,
                            effectiveMaxVariation
                    );

                    if (outcome != RegistrationOutcome.REGISTERED) {
                        refundPlacement(player, targetLocation, normalizedVar, creative, bypassCost, placementCostBundle, chargedPlacementQuote, activeEconomy);
                        pendingPlacements.remove(ownerId);
                        switch (outcome) {
                            case ALREADY_EXISTS -> messages.send(player, MessageKey.ERROR_ALREADY_EXISTS);
                            case GLOBAL_LIMIT_REACHED ->
                                    player.sendMessage(messages.errorLimitReachedGlobal(player, effectiveMaxGlobal));
                            case VARIATION_LIMIT_REACHED -> player.sendMessage(
                                    messages.errorLimitReachedVariation(player, variation.displayName(), effectiveMaxVariation)
                            );
                            default -> {
                            }
                        }
                        return;
                    }

                    placedAetheryte = aetheryte;

                    final Block originBlock = world.getBlockAt(blockX, blockY, blockZ);
                    if (!AetheryteVariation.isAirMaterial(originBlock.getType())) {
                        originBlock.setType(Material.AIR, true);
                    }

                    spawnOrUpdateMarkerEntity(world, aetheryte);
                    modelService.registerAetheryte(aetheryte, variation, world);
                    try {
                        hologramService.showAetheryte(aetheryte);
                    } catch (final Exception e) {
                        plugin.getSLF4JLogger().warn("Failed to display hologram for newly placed Aetheryte {}", aetheryte.id(), e);
                    }

                    try {
                        world.playSound(
                                aetheryte.markerLocation(world),
                                Sound.BLOCK_LODESTONE_PLACE,
                                1.0f,
                                1.0f
                        );
                    } catch (final Throwable ignored) {
                        // Sound playback is non-fatal if sound registry is unavailable in test environments
                    }
                    player.getScheduler().run(plugin, postPlaceTask -> {
                        try {
                            recordPlacementCooldown(player, normalizedVar);
                            player.sendMessage(messages.aetheryteCreated(player, aetheryte));
                            if (!chargedPlacementQuote.isFree()) {
                                final Component paidDisplay = CostFormatter.format(player, chargedPlacementQuote, activeEconomy, messages, false);
                                player.sendMessage(messages.get(player, MessageKey.AETHERYTE_COST_PAID, Placeholder.component("cost", paidDisplay)));
                            }
                        } finally {
                            pendingPlacements.remove(ownerId);
                        }
                    }, null);
                } catch (final Throwable t) {
                    if (placedAetheryte != null) {
                        registry.unregister(world, placedAetheryte.id());
                        removeMarkerEntitiesAt(world, placedAetheryte);
                        modelService.unregisterAetheryte(placedAetheryte.id());
                        hologramService.hideAetheryte(placedAetheryte.id());
                    }
                    refundPlacement(player, targetLocation, normalizedVar, creative, bypassCost, placementCostBundle, chargedPlacementQuote, activeEconomy);
                    pendingPlacements.remove(ownerId);
                    throw t;
                }
            });
        }, null);
    }

    void refundPlacement(
            final Player player,
            final Location targetLocation,
            final String normalizedVar,
            final boolean creative,
            final boolean bypassCost,
            final CostBundle costBundle,
            final CostQuote costQuote
    ) {
        refundPlacement(player, targetLocation, normalizedVar, creative, bypassCost, costBundle, costQuote, this.economyProvider);
    }

    void refundPlacement(
            final Player player,
            final Location targetLocation,
            final String normalizedVar,
            final boolean creative,
            final boolean bypassCost,
            final CostBundle costBundle,
            final CostQuote costQuote,
            final EconomyProvider paymentProvider
    ) {
        if (!creative) {
            refundItemToPlayer(player, targetLocation, normalizedVar);
            if (!bypassCost && !costBundle.isEmpty()) {
                final ScheduledTask scheduled = player.getScheduler().run(
                        plugin,
                        t -> {
                            final CostBundle.RefundResult result = costBundle.refundWithResult(player, costQuote, paymentProvider);
                            if (!result.success()) {
                                pendingRefunds.computeIfAbsent(player.getUniqueId(), k -> new CopyOnWriteArrayList<>())
                                        .addAll(result.unrefundedItems());
                                plugin.getSLF4JLogger().warn(
                                        "Placement refund failed for player {}. Queued for next login.",
                                        player.getUniqueId());
                            }
                        },
                        () -> {
                            pendingRefunds.computeIfAbsent(player.getUniqueId(), k -> new CopyOnWriteArrayList<>())
                                    .addAll(costQuote.items());
                            plugin.getSLF4JLogger().warn(
                                    "Player {} disconnected before placement refund could be applied directly. Queued for next login.",
                                    player.getUniqueId());
                        }
                );
                if (scheduled == null) {
                    pendingRefunds.computeIfAbsent(player.getUniqueId(), k -> new CopyOnWriteArrayList<>())
                            .addAll(costQuote.items());
                    plugin.getSLF4JLogger().warn(
                            "Failed to schedule placement refund for player {}. Queued for next login.",
                            player.getUniqueId());
                }
            }
        }
    }

    /**
     * Sends the appropriate space or surrounding clearance error message to the player (#42).
     */
    public void sendClearanceError(final Player player, final AetheryteVariation variation) {
        final ClearanceSection clearance = variation.placement().clearance();
        if (clearance.radius() <= 0 && clearance.height() <= 1) {
            messages.send(player, MessageKey.ERROR_SPACE_NOT_EMPTY);
        } else {
            final int horizontalSpan = clearance.radius() * 2 + 1;
            player.sendMessage(messages.errorClearanceInsufficient(player, horizontalSpan, clearance.height()));
        }
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
     * of the matching variation to the player if permitted (#38).
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

            modelService.unregisterAetheryte(aetheryte.id());
            hologramService.hideAetheryte(aetheryte.id());
            world.playSound(blockLoc, Sound.BLOCK_LODESTONE_BREAK, 1.0f, 1.0f);

            if (player.getGameMode() != GameMode.CREATIVE) {
                refundItemToPlayer(player, blockLoc, aetheryte.variation());
            }
            player.sendMessage(messages.aetheryteRemoved(player, aetheryte.name()));
        });
    }

    /**
     * Removes all backing {@link Marker} entities associated with the given Aetheryte in its chunk.
     */
    private static void removeMarkerEntitiesAt(final World world, final Aetheryte target) {
        final @Nullable Chunk chunk = world.getChunkAt(target.blockX() >> 4, target.blockZ() >> 4);
        if (chunk == null) {
            return;
        }
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
     * invoked from the player's entity scheduler (#11, #12, #41).
     */
    public void warpToAetheryte(final Player player, final UUID destinationId) {
        warpToAetheryte(player, null, destinationId);
    }

    /**
     * Warps a player from {@code source} (optional) to the specified destination Aetheryte after verifying
     * permissions, costs, and landing safety (#11, #12, #41).
     */
    public void warpToAetheryte(
            final Player player,
            final @Nullable Aetheryte source,
            final UUID destinationId
    ) {
        final UUID playerId = player.getUniqueId();
        if (playerId != null && !inFlightWarps.add(playerId)) {
            return;
        }

        final Optional<Aetheryte> opt = registry.getById(destinationId);
        if (opt.isEmpty()) {
            if (playerId != null) {
                inFlightWarps.remove(playerId);
            }
            messages.send(player, MessageKey.ERROR_NOT_FOUND);
            return;
        }
        final Aetheryte destination = opt.get();

        if (source != null && !isWithinReach(player, source)) {
            if (playerId != null) {
                inFlightWarps.remove(playerId);
            }
            messages.send(player, MessageKey.ERROR_TOO_FAR);
            return;
        }

        final WarpEvaluation evaluation = evaluateWarp(player, source, destination);
        if (!evaluation.allowed()) {
            if (playerId != null) {
                inFlightWarps.remove(playerId);
            }
            final Component reason = evaluation.denyReason() != null
                    ? evaluation.denyReason()
                    : messages.get(player, MessageKey.ERROR_WARP_REQUIREMENTS_NOT_MET);
            player.sendMessage(messages.errorWarpDenied(player, reason));
            return;
        }

        final World targetWorld = Bukkit.getWorld(destination.worldId());
        if (targetWorld == null) {
            if (playerId != null) {
                inFlightWarps.remove(playerId);
            }
            messages.send(player, MessageKey.ERROR_NOT_FOUND);
            return;
        }

        final Location checkLoc = destination.blockLocation(targetWorld);
        final float yaw = player.getYaw();
        final float pitch = player.getPitch();
        final AetheryteVariation destVariation = variationRegistry.getOrDefault(destination.variation());

        Bukkit.getRegionScheduler().execute(plugin, checkLoc, () -> {
            try {
                final Optional<Location> safeLanding = LandingResolver.resolveForVariation(
                        targetWorld,
                        destination.blockX(),
                        destination.blockY(),
                        destination.blockZ(),
                        yaw,
                        pitch,
                        destVariation
                );
                if (safeLanding.isEmpty()) {
                    if (playerId != null) {
                        inFlightWarps.remove(playerId);
                    }
                    messages.send(player, MessageKey.ERROR_DESTINATION_UNSAFE);
                    return;
                }
                final Location destinationLoc = safeLanding.get();

                player.getScheduler().run(plugin, task -> {
                    final WarpCostEvaluator activeEvaluator = this.costEvaluator;
                    final boolean bypassCost = Permissions.canBypassWarpCost(player);
                    boolean paid = false;
                    try {
                        if (source != null && !isWithinReach(player, source)) {
                            if (playerId != null) {
                                inFlightWarps.remove(playerId);
                            }
                            messages.send(player, MessageKey.ERROR_TOO_FAR);
                            return;
                        }

                        if (!canUse(player)) {
                            if (playerId != null) {
                                inFlightWarps.remove(playerId);
                            }
                            final WarpEvaluation recheck = evaluateWarp(player, source, destination);
                            final Component reason = recheck.denyReason() != null
                                    ? recheck.denyReason()
                                    : messages.get(player, MessageKey.ERROR_NO_WARP_PERMISSION);
                            player.sendMessage(messages.errorWarpDenied(player, reason));
                            return;
                        }

                        paid = bypassCost || activeEvaluator.charge(player, source, destination);
                        if (!paid) {
                            if (playerId != null) {
                                inFlightWarps.remove(playerId);
                            }
                            final WarpEvaluation recheck = evaluateWarp(player, source, destination);
                            final Component reason = recheck.denyReason() != null
                                    ? recheck.denyReason()
                                    : messages.get(player, MessageKey.ERROR_INSUFFICIENT_COST);
                            player.sendMessage(messages.errorWarpDenied(player, reason));
                            return;
                        }

                        if (source != null) {
                            modelService.triggerAnimation(source.id(), ModelAnimationTrigger.ON_WARP_DEPART);
                        }
                        final CompletableFuture<Void> unused = player.teleportAsync(destinationLoc).thenAccept(success -> {
                            if (playerId != null) {
                                inFlightWarps.remove(playerId);
                            }
                            if (Boolean.TRUE.equals(success)) {
                                if (!bypassCost) {
                                    final List<CostItemQuote> chargedItems = activeEvaluator.popPendingCharge(player, destination);
                                    activeEvaluator.commit(player, source, destination);
                                    final CostQuote paidQuote = new CostQuote(chargedItems);
                                    if (!paidQuote.isFree()) {
                                        final Component paidDisplay = CostFormatter.format(player, paidQuote, this.economyProvider, messages, false);
                                        player.sendMessage(messages.get(player, MessageKey.WARP_COST_PAID, Placeholder.component("cost", paidDisplay)));
                                    }
                                }
                                modelService.triggerAnimation(destination.id(), ModelAnimationTrigger.ON_WARP_ARRIVE);
                                player.sendMessage(messages.warpedTo(player, destination));
                                player.getScheduler().run(plugin, postTask -> {
                                    try {
                                        player.playSound(destinationLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                                    } catch (final Throwable ignored) {
                                        // Sound playback is non-fatal if sound registry is unavailable in test environments
                                    }
                                    hologramService.refreshPlayer(player, registry.getByWorld(targetWorld.getUID()));
                                    modelService.refreshPlayer(player, registry.getByWorld(targetWorld.getUID()));
                                }, null);
                            } else {
                                handleWarpFailure(player, source, destination, activeEvaluator, bypassCost, MessageKey.ERROR_DESTINATION_UNSAFE);
                            }
                        }).exceptionally(ex -> {
                            if (playerId != null) {
                                inFlightWarps.remove(playerId);
                            }
                            plugin.getSLF4JLogger().warn(
                                    "Failed to teleport player {} to aetheryte {}",
                                    player.getName(),
                                    destination.id(),
                                    ex
                            );
                            handleWarpFailure(player, source, destination, activeEvaluator, bypassCost, MessageKey.ERROR_NOT_FOUND);
                            return null;
                        });
                        if (unused.isCancelled()) {
                            if (playerId != null) {
                                inFlightWarps.remove(playerId);
                            }
                            handleWarpFailure(player, source, destination, activeEvaluator, bypassCost, MessageKey.ERROR_NOT_FOUND);
                        }
                    } catch (final Throwable t) {
                        if (playerId != null) {
                            inFlightWarps.remove(playerId);
                        }
                        plugin.getSLF4JLogger().error(
                                "Unexpected error during warp charge or execution for player {} to {}",
                                player.getName(),
                                destination.id(),
                                t
                        );
                        if (paid) {
                            handleWarpFailure(player, source, destination, activeEvaluator, bypassCost, MessageKey.ERROR_NOT_FOUND);
                        } else {
                            messages.send(player, MessageKey.ERROR_NOT_FOUND);
                        }
                    }
                }, () -> {
                    if (playerId != null) {
                        inFlightWarps.remove(playerId);
                    }
                });
            } catch (final Throwable t) {
                if (playerId != null) {
                    inFlightWarps.remove(playerId);
                }
                plugin.getSLF4JLogger().error(
                        "Unexpected error during warp landing resolution for player {} to {}",
                        player.getName(),
                        destination.id(),
                        t
                );
                messages.send(player, MessageKey.ERROR_NOT_FOUND);
            }
        });
    }

    private void handleWarpFailure(
            final Player player,
            final @Nullable Aetheryte source,
            final Aetheryte destination,
            final WarpCostEvaluator evaluator,
            final boolean bypassCost,
            final MessageKey errorKey
    ) {
        final UUID playerId = player.getUniqueId();
        if (playerId != null) {
            inFlightWarps.remove(playerId);
        }
        final ScheduledTask task = player.getScheduler().run(plugin, t -> {
            if (!bypassCost) {
                final boolean refunded = evaluator.refund(player, source, destination);
                if (!refunded) {
                    final List<CostItemQuote> unrefunded = evaluator.popPendingCharge(player, destination);
                    if (!unrefunded.isEmpty()) {
                        pendingRefunds.computeIfAbsent(player.getUniqueId(), k -> new CopyOnWriteArrayList<>())
                                .addAll(unrefunded);
                        plugin.getSLF4JLogger().warn("Warp refund failed for player {}. Queued for next login.", player.getUniqueId());
                    }
                }
            }
            messages.send(player, errorKey);
        }, () -> {
            if (!bypassCost) {
                final List<CostItemQuote> unrefunded = evaluator.popPendingCharge(player, destination);
                if (!unrefunded.isEmpty()) {
                    pendingRefunds.computeIfAbsent(player.getUniqueId(), k -> new CopyOnWriteArrayList<>())
                            .addAll(unrefunded);
                    plugin.getSLF4JLogger().warn(
                            "Player {} disconnected before warp refund could execute. Queued for next login.",
                            player.getUniqueId());
                }
            }
        });

        if (task == null && !bypassCost) {
            final List<CostItemQuote> unrefunded = evaluator.popPendingCharge(player, destination);
            if (!unrefunded.isEmpty()) {
                pendingRefunds.computeIfAbsent(player.getUniqueId(), k -> new CopyOnWriteArrayList<>())
                        .addAll(unrefunded);
                plugin.getSLF4JLogger().warn(
                        "Could not schedule warp refund for player {}. Queued for next login.",
                        player.getUniqueId());
            }
        }
    }

    /**
     * Checks whether the given block is air or replaceable (excluding liquid blocks).
     */
    public static boolean isReplaceableBlock(final Block block) {
        return ClearanceChecker.isReplaceableBlock(block);
    }

    /**
     * Resolves a safe landing location for the given Aetheryte coordinates at radius 0.
     */
    public static Optional<Location> resolveSafeLanding(
            final World world,
            final int blockX,
            final int blockY,
            final int blockZ,
            final float yaw,
            final float pitch
    ) {
        return LandingResolver.resolveForVariation(
                world,
                blockX,
                blockY,
                blockZ,
                yaw,
                pitch,
                AetheryteVariation.defaultVariation()
        );
    }

    /**
     * Verifies whether the Aetheryte coordinates have a valid safe landing position.
     */
    public static boolean isSafeLanding(final World world, final int blockX, final int blockY, final int blockZ) {
        return resolveSafeLanding(world, blockX, blockY, blockZ, 0.0f, 0.0f).isPresent();
    }

    /**
     * Consumes a single Aetheryte creation item of the specified variation from the player's hands or storage contents.
     */
    private static boolean consumeOneAetheryteItem(final PlayerInventory inventory, final String variationId) {
        final ItemStack mainHand = inventory.getItemInMainHand();
        if (AetheryteItemFactory.isAetheryteItemOfVariation(mainHand, variationId)) {
            mainHand.setAmount(mainHand.getAmount() - 1);
            return true;
        }
        final ItemStack offHand = inventory.getItemInOffHand();
        if (AetheryteItemFactory.isAetheryteItemOfVariation(offHand, variationId)) {
            offHand.setAmount(offHand.getAmount() - 1);
            return true;
        }
        final @Nullable ItemStack @Nullable [] contents = inventory.getStorageContents();
        if (contents == null) {
            return false;
        }
        for (int i = 0; i < contents.length; i++) {
            final @Nullable ItemStack slot = contents[i];
            if (slot != null && AetheryteItemFactory.isAetheryteItemOfVariation(slot, variationId)) {
                slot.setAmount(slot.getAmount() - 1);
                inventory.setItem(i, slot.getAmount() > 0 ? slot : null);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns one Aetheryte creation item of the specified variation to the player's inventory,
     * or drops it at the fallback location.
     */
    private void refundItemToPlayer(
            final Player player,
            final Location fallbackDropLocation,
            final String variationId
    ) {
        try {
            final AetheryteVariation variation = variationRegistry.getOrDefault(variationId);
            final ItemStack item = AetheryteItemFactory.createItem(variationId, variation, 1, messages);
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
        } catch (final Throwable t) {
            plugin.getSLF4JLogger().warn("Failed to refund Aetheryte item to player {} for variation {}", player.getName(), variationId, t);
        }
    }
}
