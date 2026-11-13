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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry.RegistrationOutcome;
import net.sabafly.clickportals.cost.CostBundle;
import net.sabafly.clickportals.cost.CostFormatter;
import net.sabafly.clickportals.cost.CostItemQuote;
import net.sabafly.clickportals.cost.CostQuote;
import net.sabafly.clickportals.dialog.WarpSortOrder;
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
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
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
     * Player {@link PersistentDataContainer} key storing recently processed pending refund transaction IDs for idempotency (#15).
     */
    public static final NamespacedKey PROCESSED_REFUNDS_KEY = new NamespacedKey("clickportals", "processed_refunds");

    /**
     * Player {@link PersistentDataContainer} key storing favorited Aetheryte UUIDs (#56).
     */
    public static final NamespacedKey FAVORITES_KEY = new NamespacedKey("clickportals", "favorites");

    /**
     * Player {@link PersistentDataContainer} key storing the player's last selected warp sort order (#55).
     */
    public static final NamespacedKey WARP_SORT_KEY = new NamespacedKey("clickportals", "warp_sort");

    /**
     * Resolved player identity used for ownership transfers (#57).
     */
    public record ResolvedPlayer(UUID id, String name) {
    }

    private static final int MAX_TRACKED_PROCESSED_REFUNDS = 100;

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
         * Evaluates the cost and affordability of warping from {@code source} to each destination in {@code destinations}
         * in order (#20).
         */
        default List<WarpEvaluation> evaluateBatch(
                final Player player,
                final @Nullable Aetheryte source,
                final List<Aetheryte> destinations
        ) {
            final List<WarpEvaluation> results = new ArrayList<>(destinations.size());
            for (final Aetheryte destination : destinations) {
                results.add(evaluate(player, source, destination));
            }
            return results;
        }

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

        /**
         * Returns pending charged cost items for the player and destination without removing them (#15).
         */
        default List<CostItemQuote> peekPendingCharge(final Player player, final Aetheryte destination) {
            return List.of();
        }

        /**
         * Pops and returns any unrefunded items remaining after a failed {@link #charge} rollback (#15).
         */
        default List<CostItemQuote> popFailedChargeUnrefunded(final Player player, final Aetheryte destination) {
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
    private volatile DoubleSupplier globalMinDistanceSupplier = () -> 0.0;
    private volatile IntSupplier maxFavoritesPerPlayerSupplier = () -> -1;
    private final ToLongFunction<Player> tickSupplier;
    private final CooldownPacketSender cooldownPacketSender;
    private volatile WarpCostEvaluator costEvaluator;
    private volatile EconomyProvider economyProvider = NoopEconomyProvider.INSTANCE;
    private volatile Function<UUID, @Nullable String> ownerNameLookup = AetheryteService::defaultOwnerNameLookup;
    private volatile Function<String, @Nullable ResolvedPlayer> playerByNameLookup = AetheryteService::defaultPlayerByNameLookup;
    private final Set<UUID> pendingPlacements = ConcurrentHashMap.newKeySet();
    private final Set<UUID> inFlightWarps = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<CostItemQuote>> pendingRefunds = new ConcurrentHashMap<>();
    private final PendingRefundStorage pendingRefundStorage;
    private final Object refundPersistLock = new Object();
    private final AtomicBoolean pendingRefundsDirty = new AtomicBoolean(false);
    private final AtomicLong pendingRefundMutationGeneration = new AtomicLong(0L);
    private volatile long persistedRefundGeneration = 0L;
    private final AtomicBoolean refundAsyncPersistRunning = new AtomicBoolean(false);
    private final AtomicBoolean refundPersistScheduled = new AtomicBoolean(false);
    private final Set<UUID> playersPendingPdcTrim = ConcurrentHashMap.newKeySet();
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
        this(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                modelService,
                messages,
                globalMaxPerPlayerSupplier,
                globalCooldownTicksSupplier,
                () -> 0.0,
                tickSupplier,
                cooldownPacketSender,
                costEvaluator
        );
    }

    /**
     * Creates a new {@link AetheryteService} with full dependencies including minimum placement distance supplier.
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
            final DoubleSupplier globalMinDistanceSupplier,
            final ToLongFunction<Player> tickSupplier,
            final CooldownPacketSender cooldownPacketSender,
            final WarpCostEvaluator costEvaluator
    ) {
        this(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                modelService,
                messages,
                globalMaxPerPlayerSupplier,
                globalCooldownTicksSupplier,
                globalMinDistanceSupplier,
                tickSupplier,
                cooldownPacketSender,
                costEvaluator,
                resolveDefaultRefundStorage(plugin)
        );
    }

    /**
     * Creates a new {@link AetheryteService} with full dependencies including minimum placement distance supplier
     * and custom pending refund storage (#15).
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
            final DoubleSupplier globalMinDistanceSupplier,
            final ToLongFunction<Player> tickSupplier,
            final CooldownPacketSender cooldownPacketSender,
            final WarpCostEvaluator costEvaluator,
            final PendingRefundStorage pendingRefundStorage
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.variationRegistry = variationRegistry;
        this.hologramService = hologramService;
        this.modelService = modelService;
        this.messages = messages;
        this.globalMaxPerPlayerSupplier = globalMaxPerPlayerSupplier;
        this.globalCooldownTicksSupplier = globalCooldownTicksSupplier;
        this.globalMinDistanceSupplier = globalMinDistanceSupplier != null ? globalMinDistanceSupplier : () -> 0.0;
        this.tickSupplier = tickSupplier;
        this.cooldownPacketSender = cooldownPacketSender;
        this.costEvaluator = costEvaluator;
        this.pendingRefundStorage = pendingRefundStorage != null ? pendingRefundStorage : PendingRefundStorage.noop();
        this.pendingRefunds.putAll(this.pendingRefundStorage.load());
    }

    private static PendingRefundStorage resolveDefaultRefundStorage(final Plugin plugin) {
        if (plugin == null || plugin.getDataFolder() == null) {
            return PendingRefundStorage.noop();
        }
        return new PendingRefundStorage(
                plugin.getDataFolder().toPath().resolve("pending_refunds.yml"),
                plugin.getSLF4JLogger()
        );
    }

    /**
     * Returns the global minimum placement distance supplier.
     */
    public DoubleSupplier globalMinDistanceSupplier() {
        return globalMinDistanceSupplier;
    }

    /**
     * Updates the global minimum placement distance supplier on reload.
     */
    public void setGlobalMinDistanceSupplier(final DoubleSupplier globalMinDistanceSupplier) {
        this.globalMinDistanceSupplier = globalMinDistanceSupplier != null ? globalMinDistanceSupplier : () -> 0.0;
    }

    /**
     * Updates the global maximum favorites per player supplier on reload.
     */
    public void setMaxFavoritesPerPlayerSupplier(final IntSupplier maxFavoritesPerPlayerSupplier) {
        this.maxFavoritesPerPlayerSupplier = maxFavoritesPerPlayerSupplier != null
                ? maxFavoritesPerPlayerSupplier
                : () -> -1;
    }

    /**
     * Returns the configured maximum number of favorites per player (-1 for unlimited, 0 to disable).
     */
    public int getMaxFavoritesPerPlayer() {
        return Math.max(-1, maxFavoritesPerPlayerSupplier.getAsInt());
    }

    /**
     * Returns the effective minimum placement distance for the given variation.
     */
    public double effectiveMinDistance(final String variationId) {
        final AetheryteVariation var = variationRegistry.getOrDefault(variationId);
        return var.placement().minDistanceOrDefault(globalMinDistanceSupplier.getAsDouble());
    }

    /**
     * Checks whether an Aetheryte can be placed at the specified position without violating minimum distance constraints.
     */
    public Optional<Component> checkPlacementDistance(
            final Player player,
            final World world,
            final int x,
            final int y,
            final int z,
            final String variationId
    ) {
        if (Permissions.canBypassPlacementDistance(player)) {
            return Optional.empty();
        }
        final double minDistance = effectiveMinDistance(variationId);
        if (minDistance <= 0.0) {
            return Optional.empty();
        }
        final Optional<Aetheryte> nearby = registry.findWithinDistance(world.getUID(), x, y, z, minDistance);
        if (nearby.isPresent()) {
            final Component distComp = Component.text(String.format(java.util.Locale.ROOT, "%.1f", minDistance));
            return Optional.of(messages.get(player, MessageKey.ERROR_PLACEMENT_TOO_CLOSE, Placeholder.component("distance", distComp)));
        }
        return Optional.empty();
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
            final PersistentDataContainer pdc = player.getPersistentDataContainer();
            final List<String> existingProcessed = pdc != null
                    ? pdc.get(PROCESSED_REFUNDS_KEY, PersistentDataType.LIST.strings())
                    : null;
            final List<String> processedList = existingProcessed != null ? new ArrayList<>(existingProcessed) : new ArrayList<>();

            final List<CostItemQuote> snapshot = new ArrayList<>(currentItems);
            final List<CostItemQuote> toRefund = new ArrayList<>();
            for (final CostItemQuote item : snapshot) {
                final UUID txId = item.transactionId();
                if (txId != null && processedList.contains(txId.toString())) {
                    // Already deposited prior to a crash or storage save failure; remove without re-depositing
                    currentItems.remove(item);
                } else {
                    toRefund.add(item);
                }
            }

            boolean allSuccess = true;
            if (!toRefund.isEmpty()) {
                final CostQuote quote = new CostQuote(toRefund, true);
                final CostBundle.RefundResult result = new CostBundle(List.of()).refundWithResult(player, quote, economyProvider);
                allSuccess = result.success();
                for (final CostItemQuote refundedItem : result.refundedItems()) {
                    currentItems.remove(refundedItem);
                    final UUID txId = refundedItem.transactionId();
                    if (txId != null) {
                        final String txIdStr = txId.toString();
                        if (!processedList.contains(txIdStr)) {
                            processedList.add(txIdStr);
                        }
                    }
                }
                if (pdc != null && !result.refundedItems().isEmpty()) {
                    pdc.set(PROCESSED_REFUNDS_KEY, PersistentDataType.LIST.strings(), processedList);
                    if (processedList.size() > MAX_TRACKED_PROCESSED_REFUNDS) {
                        playersPendingPdcTrim.add(player.getUniqueId());
                    }
                }
            }
            pendingRefunds.computeIfPresent(player.getUniqueId(), (k, list) -> list.isEmpty() ? null : list);
            markPendingRefundsDirtyAndPersist();
            if (allSuccess) {
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
     * Returns the active {@link PendingRefundStorage} (#15).
     */
    public PendingRefundStorage pendingRefundStorage() {
        return pendingRefundStorage;
    }

    private void recordPendingRefund(final UUID playerId, final List<CostItemQuote> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        final List<CostItemQuote> stamped = new ArrayList<>(items.size());
        for (final CostItemQuote item : items) {
            stamped.add(item.transactionId() != null ? item : item.withTransactionId(UUID.randomUUID()));
        }
        pendingRefunds.compute(playerId, (k, existing) -> {
            final List<CostItemQuote> list = existing != null ? existing : new CopyOnWriteArrayList<>();
            list.addAll(stamped);
            return list;
        });
        markPendingRefundsDirtyAndPersist();
    }

    /**
     * Synchronously flushes any unpersisted pending refunds to disk, e.g., during plugin shutdown (#15).
     *
     * @return {@code true} if no pending refunds remain dirty or persistence succeeded, {@code false} otherwise
     */
    public boolean flushPendingRefunds() {
        return savePendingRefundsLoop();
    }

    /**
     * Returns the number of players currently awaiting a pending refund.
     */
    public int pendingRefundPlayerCount() {
        return pendingRefunds.size();
    }

    /**
     * Returns the total number of unrefunded cost items across all players.
     */
    public int pendingRefundItemCount() {
        int total = 0;
        for (final List<CostItemQuote> list : pendingRefunds.values()) {
            if (list != null) {
                total += list.size();
            }
        }
        return total;
    }

    private void markPendingRefundsDirtyAndPersist() {
        pendingRefundMutationGeneration.incrementAndGet();
        pendingRefundsDirty.set(true);
        scheduleImmediateAsyncPendingRefundPersist();
    }

    private void scheduleImmediateAsyncPendingRefundPersist() {
        if (!pendingRefundsDirty.get() || !refundAsyncPersistRunning.compareAndSet(false, true)) {
            return;
        }
        final org.bukkit.Server server = plugin.getServer();
        if (server == null || server.getAsyncScheduler() == null || !plugin.isEnabled()) {
            refundAsyncPersistRunning.set(false);
            plugin.getSLF4JLogger().warn(
                    "AsyncScheduler is unavailable while persisting pending refunds; leaving dirty state for flush on shutdown."
            );
            return;
        }
        try {
            server.getAsyncScheduler().runNow(plugin, task -> {
                boolean saved = false;
                try {
                    saved = savePendingRefundsLoop();
                    if (!saved) {
                        plugin.getSLF4JLogger().error("Failed to persist pending refunds to disk; scheduling background retries.");
                        schedulePendingRefundRetry(0);
                    }
                } finally {
                    refundAsyncPersistRunning.set(false);
                    if (saved && pendingRefundsDirty.get()) {
                        scheduleImmediateAsyncPendingRefundPersist();
                    }
                }
            });
        } catch (final Throwable t) {
            refundAsyncPersistRunning.set(false);
            plugin.getSLF4JLogger().error("Failed to schedule async pending refund persistence; scheduling retry.", t);
            schedulePendingRefundRetry(0);
        }
    }

    private boolean savePendingRefundsLoop() {
        synchronized (refundPersistLock) {
            while (pendingRefundsDirty.getAndSet(false)) {
                final long targetGeneration = pendingRefundMutationGeneration.get();
                if (!pendingRefundStorage.save(pendingRefunds)) {
                    pendingRefundsDirty.set(true);
                    return false;
                }
                persistedRefundGeneration = targetGeneration;
            }
            trimProcessedRefundsForPendingPlayers(persistedRefundGeneration);
            return true;
        }
    }

    private void schedulePendingRefundRetry(final int attempt) {
        if (!pendingRefundsDirty.get() || !refundPersistScheduled.compareAndSet(false, true)) {
            return;
        }
        final org.bukkit.Server server = plugin.getServer();
        if (server == null || server.getAsyncScheduler() == null || !plugin.isEnabled()) {
            refundPersistScheduled.set(false);
            return;
        }
        final long delaySeconds = Math.min(60L, 5L * (1L << Math.min(attempt, 4)));
        try {
            server.getAsyncScheduler().runDelayed(plugin, t -> {
                refundPersistScheduled.set(false);
                if (!pendingRefundsDirty.get()) {
                    return;
                }
                if (savePendingRefundsLoop()) {
                    plugin.getSLF4JLogger().info("Successfully persisted pending refunds after {} retry attempt(s).", attempt + 1);
                    return;
                }
                final int nextAttempt = attempt + 1;
                if (nextAttempt == 5 || nextAttempt % 10 == 0) {
                    plugin.getSLF4JLogger().error(
                            "Still unable to persist pending refunds to disk after {} attempts; continuing background retries.",
                            nextAttempt
                    );
                }
                schedulePendingRefundRetry(nextAttempt);
            }, delaySeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (final Throwable t) {
            refundPersistScheduled.set(false);
            plugin.getSLF4JLogger().error("Failed to schedule pending refund persistence retry.", t);
        }
    }

    private void trimProcessedRefundsForPendingPlayers(final long scheduledGeneration) {
        if (playersPendingPdcTrim.isEmpty() || !plugin.isEnabled()) {
            return;
        }
        final org.bukkit.Server server = plugin.getServer();
        if (server == null) {
            return;
        }
        final List<UUID> snapshot = new ArrayList<>(playersPendingPdcTrim);
        for (final UUID playerId : snapshot) {
            final Player player = server.getPlayer(playerId);
            if (player == null || player.getScheduler() == null) {
                continue;
            }
            try {
                player.getScheduler().run(plugin, t -> {
                    if (pendingRefundsDirty.get()
                            || persistedRefundGeneration != scheduledGeneration
                            || pendingRefundMutationGeneration.get() != scheduledGeneration) {
                        return;
                    }
                    final PersistentDataContainer pdc = player.getPersistentDataContainer();
                    if (pdc == null) {
                        playersPendingPdcTrim.remove(playerId);
                        return;
                    }
                    final List<String> existing = pdc.get(PROCESSED_REFUNDS_KEY, PersistentDataType.LIST.strings());
                    if (existing == null || existing.size() <= MAX_TRACKED_PROCESSED_REFUNDS) {
                        playersPendingPdcTrim.remove(playerId);
                        return;
                    }
                    final List<String> trimmed = new ArrayList<>(
                            existing.subList(existing.size() - MAX_TRACKED_PROCESSED_REFUNDS, existing.size())
                    );
                    pdc.set(PROCESSED_REFUNDS_KEY, PersistentDataType.LIST.strings(), trimmed);
                    playersPendingPdcTrim.remove(playerId);
                }, null);
            } catch (final Throwable ignored) {
                // Plugin may be shutting down between isEnabled() check and task registration
            }
        }
    }

    /**
     * Returns the active {@link Messages} service.
     */
    public Messages messages() {
        return messages;
    }

    /**
     * Overrides the fallback UUID-to-name lookup used when an {@link Aetheryte} has no cached {@code ownerName} (#53).
     */
    public void setOwnerNameLookup(final Function<UUID, @Nullable String> ownerNameLookup) {
        this.ownerNameLookup = ownerNameLookup != null ? ownerNameLookup : AetheryteService::defaultOwnerNameLookup;
    }

    private static @Nullable String defaultOwnerNameLookup(final UUID ownerId) {
        try {
            if (Bukkit.getServer() == null) {
                return null;
            }
            final Player online = Bukkit.getPlayer(ownerId);
            if (online != null) {
                return online.getName();
            }
            final OfflinePlayer offline = Bukkit.getOfflinePlayer(ownerId);
            return offline != null ? offline.getName() : null;
        } catch (final Throwable ignored) {
            return null;
        }
    }

    /**
     * Resolves a human-readable owner name for {@code aetheryte}, falling back to a localized unknown label (#53).
     */
    public String resolveOwnerName(final Aetheryte aetheryte, final CommandSender viewer) {
        return resolveOwnerName(aetheryte, viewer, null);
    }

    /**
     * Resolves a human-readable owner name using an optional per-render cache to avoid repeated lookups (#53).
     */
    public String resolveOwnerName(
            final Aetheryte aetheryte,
            final CommandSender viewer,
            @Nullable final Map<UUID, String> cache
    ) {
        final UUID ownerId = aetheryte.ownerId();
        if (cache != null) {
            final String cached = cache.get(ownerId);
            if (cached != null) {
                return cached;
            }
        }
        String resolved = null;
        try {
            if (Bukkit.getServer() != null) {
                final Player online = Bukkit.getPlayer(ownerId);
                if (online != null) {
                    resolved = online.getName();
                }
            }
        } catch (final Throwable ignored) {
            // Fall back to stored name / lookup in unit test environments
        }
        if (resolved == null || resolved.isBlank()) {
            resolved = aetheryte.ownerName();
        }
        if (resolved == null || resolved.isBlank()) {
            resolved = ownerNameLookup.apply(ownerId);
        }
        final String finalName = (resolved != null && !resolved.isBlank())
                ? resolved
                : PlainTextComponentSerializer.plainText().serialize(messages.get(viewer, MessageKey.DIALOG_WARP_UNKNOWN_OWNER));
        if (cache != null) {
            cache.put(ownerId, finalName);
        }
        return finalName;
    }

    /**
     * Lazily refreshes the stored {@code ownerName} when the owner interacts with their own Aetheryte after a rename (#53).
     */
    public Aetheryte refreshOwnerNameIfNeeded(final Player player, final Aetheryte aetheryte) {
        if (!aetheryte.ownerId().equals(player.getUniqueId())) {
            return aetheryte;
        }
        final String currentName = player.getName();
        if (currentName.equals(aetheryte.ownerName())) {
            return aetheryte;
        }
        final Aetheryte updatedPreview = aetheryte.withOwnerName(currentName);
        try {
            if (Bukkit.getServer() == null) {
                return updatedPreview;
            }
            final World world = Bukkit.getWorld(aetheryte.worldId());
            if (world == null) {
                return updatedPreview;
            }
            final UUID expectedOwnerId = player.getUniqueId();
            Bukkit.getRegionScheduler().execute(plugin, aetheryte.blockLocation(world), () -> {
                final Optional<Aetheryte> updated = registry.update(
                        world,
                        aetheryte.id(),
                        a -> a.ownerId().equals(expectedOwnerId) ? a.withOwnerName(currentName) : a
                );
                updated.filter(value -> value.ownerId().equals(expectedOwnerId) && currentName.equals(value.ownerName()))
                        .ifPresent(value -> updateMarkerDataAt(world, value));
            });
        } catch (final Throwable ignored) {
            // Non-fatal in test environments without a live Bukkit server/scheduler
        }
        return updatedPreview;
    }

    /**
     * Overrides the player name-to-identity lookup used for Admin ownership transfers (#57).
     */
    public void setPlayerByNameLookup(final Function<String, @Nullable ResolvedPlayer> playerByNameLookup) {
        this.playerByNameLookup = playerByNameLookup != null ? playerByNameLookup : AetheryteService::defaultPlayerByNameLookup;
    }

    private static @Nullable ResolvedPlayer defaultPlayerByNameLookup(final String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        final String trimmed = playerName.strip();
        try {
            if (Bukkit.getServer() == null) {
                return null;
            }
            final Player online = Bukkit.getPlayerExact(trimmed);
            if (online != null) {
                return new ResolvedPlayer(online.getUniqueId(), online.getName());
            }
            final OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(trimmed);
            if (offline != null && offline.getName() != null) {
                return new ResolvedPlayer(offline.getUniqueId(), offline.getName());
            }
            return null;
        } catch (final Throwable ignored) {
            return null;
        }
    }

    private record FavoriteEntry(UUID id, @Nullable UUID worldId) {
        String serialize() {
            return worldId != null ? id + ":" + worldId : id.toString();
        }
    }

    private record LoadedFavorites(Map<UUID, FavoriteEntry> entries, boolean changedOnRead) {
    }

    /**
     * Returns the player's favorited Aetheryte UUIDs from their PDC, excluding entries confirmed deleted in loaded worlds (#56).
     */
    public Set<UUID> getFavorites(final Player player) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc == null) {
            return Set.of();
        }
        return Set.copyOf(loadFavorites(pdc).entries().keySet());
    }

    /**
     * Returns whether {@code aetheryteId} is favorited by {@code player} (#56).
     */
    public boolean isFavorite(final Player player, final UUID aetheryteId) {
        return getFavorites(player).contains(aetheryteId);
    }

    /**
     * Toggles favorite state for {@code aetheryteId} in {@code player}'s PDC and returns the new state (#56).
     */
    public boolean toggleFavorite(final Player player, final UUID aetheryteId) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc == null) {
            return false;
        }
        final LoadedFavorites loaded = loadFavorites(pdc);
        final Map<UUID, FavoriteEntry> updated = loaded.entries();
        final boolean added;
        if (updated.containsKey(aetheryteId)) {
            updated.remove(aetheryteId);
            added = false;
        } else {
            final Optional<Aetheryte> liveTarget = registry.getById(aetheryteId);
            if (registry.hasLoadedWorlds() && liveTarget.isEmpty()) {
                if (loaded.changedOnRead()) {
                    writeFavorites(pdc, updated);
                }
                messages.send(player, MessageKey.ERROR_NOT_FOUND);
                return false;
            }
            final int maxFavorites = getMaxFavoritesPerPlayer();
            if (maxFavorites >= 0 && updated.size() >= maxFavorites) {
                if (loaded.changedOnRead()) {
                    writeFavorites(pdc, updated);
                }
                messages.send(
                        player,
                        MessageKey.ERROR_FAVORITES_LIMIT_REACHED,
                        Placeholder.unparsed("max", Integer.toString(maxFavorites))
                );
                return false;
            }
            updated.put(aetheryteId, new FavoriteEntry(aetheryteId, liveTarget.map(Aetheryte::worldId).orElse(null)));
            added = true;
        }
        writeFavorites(pdc, updated);
        return added;
    }

    private LoadedFavorites loadFavorites(final PersistentDataContainer pdc) {
        final List<String> raw = pdc.get(FAVORITES_KEY, PersistentDataType.LIST.strings());
        if (raw == null || raw.isEmpty()) {
            return new LoadedFavorites(new LinkedHashMap<>(), false);
        }
        final Map<UUID, FavoriteEntry> valid = new LinkedHashMap<>(raw.size());
        final boolean hasLoadedWorlds = registry.hasLoadedWorlds();
        boolean changed = false;
        for (final String entryStr : raw) {
            final FavoriteEntry parsed = parseFavoriteEntry(entryStr);
            if (parsed == null) {
                changed = true;
                continue;
            }
            final Optional<Aetheryte> live = registry.getById(parsed.id());
            final FavoriteEntry resolved;
            if (live.isPresent()) {
                final UUID liveWorldId = live.get().worldId();
                if (!liveWorldId.equals(parsed.worldId())) {
                    changed = true;
                }
                resolved = new FavoriteEntry(parsed.id(), liveWorldId);
            } else if (parsed.worldId() != null) {
                if (registry.isWorldLoaded(parsed.worldId())) {
                    changed = true;
                    continue;
                }
                resolved = parsed;
            } else if (hasLoadedWorlds) {
                changed = true;
                continue;
            } else {
                resolved = parsed;
            }
            if (valid.putIfAbsent(resolved.id(), resolved) != null) {
                changed = true;
            }
        }
        return new LoadedFavorites(valid, changed);
    }

    private static @Nullable FavoriteEntry parseFavoriteEntry(@Nullable final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final int sep = raw.indexOf(':');
        if (sep < 0) {
            try {
                return new FavoriteEntry(UUID.fromString(raw), null);
            } catch (final IllegalArgumentException ignored) {
                return null;
            }
        }
        try {
            final UUID id = UUID.fromString(raw.substring(0, sep));
            final UUID worldId = UUID.fromString(raw.substring(sep + 1));
            return new FavoriteEntry(id, worldId);
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void writeFavorites(
            final PersistentDataContainer pdc,
            final Map<UUID, FavoriteEntry> entries
    ) {
        if (entries.isEmpty()) {
            pdc.remove(FAVORITES_KEY);
        } else {
            final List<String> serialized = entries.values().stream()
                    .map(FavoriteEntry::serialize)
                    .toList();
            pdc.set(FAVORITES_KEY, PersistentDataType.LIST.strings(), serialized);
        }
    }

    /**
     * Returns the player's persisted warp sort order, defaulting to {@link WarpSortOrder#DISTANCE} (#55).
     */
    public WarpSortOrder getWarpSortOrder(final Player player) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc == null) {
            return WarpSortOrder.DISTANCE;
        }
        return WarpSortOrder.fromId(pdc.get(WARP_SORT_KEY, PersistentDataType.STRING));
    }

    /**
     * Persists the player's selected warp sort order in their PDC (#55).
     */
    public void setWarpSortOrder(final Player player, final WarpSortOrder sortOrder) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc != null) {
            pdc.set(WARP_SORT_KEY, PersistentDataType.STRING, sortOrder.id());
        }
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
     * Checks whether the given player is allowed to rename the specified Aetheryte (#57).
     */
    public static boolean canRename(final Player player, final Aetheryte aetheryte) {
        return Permissions.canRename(player, player.getUniqueId(), aetheryte);
    }

    /**
     * Checks whether the given player holds administrative privileges (#57).
     */
    public static boolean canAdmin(final Player player) {
        return Permissions.canAdmin(player);
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
        final boolean hasUsePerm = canUse(player);
        final boolean canBypass = hasUsePerm && Permissions.canBypassWarpCost(player);
        final @Nullable String plainFree = canBypass
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(messages.freeCostLabel(player))
                : null;
        return applyPermissionAndBypass(player, evaluation, hasUsePerm, canBypass, null, plainFree);
    }

    /**
     * Batch-evaluates warping from {@code source} to all {@code destinations} in order (#20).
     */
    public List<WarpEvaluation> evaluateWarps(
            final Player player,
            final @Nullable Aetheryte source,
            final List<Aetheryte> destinations
    ) {
        if (destinations.isEmpty()) {
            return List.of();
        }
        final List<WarpEvaluation> raw = costEvaluator.evaluateBatch(player, source, destinations);
        final boolean hasUsePerm = canUse(player);
        final @Nullable Component noPermMsg = hasUsePerm ? null : messages.get(player, MessageKey.ERROR_NO_WARP_PERMISSION);
        final boolean canBypass = hasUsePerm && Permissions.canBypassWarpCost(player);
        final @Nullable String plainFree = canBypass
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(messages.freeCostLabel(player))
                : null;

        final List<WarpEvaluation> results = new ArrayList<>(raw.size());
        for (final WarpEvaluation evaluation : raw) {
            results.add(applyPermissionAndBypass(player, evaluation, hasUsePerm, canBypass, noPermMsg, plainFree));
        }
        return results;
    }

    private WarpEvaluation applyPermissionAndBypass(
            final Player player,
            final WarpEvaluation evaluation,
            final boolean hasUsePermission,
            final boolean canBypass,
            final @Nullable Component cachedNoPermissionMsg,
            final @Nullable String cachedPlainFree
    ) {
        if (!hasUsePermission) {
            final Component noPerm = cachedNoPermissionMsg != null
                    ? cachedNoPermissionMsg
                    : messages.get(player, MessageKey.ERROR_NO_WARP_PERMISSION);
            return WarpEvaluation.denied(
                    evaluation.costDisplay(),
                    noPerm,
                    WarpEvaluation.DenyReason.NO_PERMISSION
            );
        }
        if (canBypass) {
            final Component originalCost = evaluation.costDisplay();
            final String plainCost = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(originalCost);
            final String plainFree = cachedPlainFree != null
                    ? cachedPlainFree
                    : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(messages.freeCostLabel(player));
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

            final Optional<Component> distanceError = checkPlacementDistance(player, world, blockX, blockY, blockZ, normalizedVar);
            if (distanceError.isPresent()) {
                pendingPlacements.remove(ownerId);
                player.sendMessage(distanceError.get());
                return;
            }

            final boolean creative = player.getGameMode() == GameMode.CREATIVE;
            final boolean bypassCost = Permissions.canBypassPlaceCost(player);
            final boolean canBypassDistance = Permissions.canBypassPlacementDistance(player);
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
                    if (!chargeResult.unrefundedItems().isEmpty()) {
                        recordPendingRefund(player.getUniqueId(), chargeResult.unrefundedItems());
                    }
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
            final AtomicBoolean regionTaskEntered = new AtomicBoolean(false);
            try {
                Bukkit.getRegionScheduler().execute(plugin, targetLocation, () -> {
                    regionTaskEntered.set(true);
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
                            normalizedVar,
                            player.getName()
                    );

                    final int effectiveMaxGlobal = bypassLimit ? -1 : globalMaxPerPlayerSupplier.getAsInt();
                    final int effectiveMaxVariation = bypassLimit ? -1 : variation.limit().maxPerPlayer();
                    final double effectiveMinDist = canBypassDistance ? 0.0 : effectiveMinDistance(normalizedVar);
                    final RegistrationOutcome outcome = registry.tryRegister(
                            world,
                            aetheryte,
                            effectiveMaxGlobal,
                            effectiveMaxVariation,
                            effectiveMinDist
                    );

                    if (outcome != RegistrationOutcome.REGISTERED) {
                        refundPlacement(player, targetLocation, normalizedVar, creative, bypassCost, placementCostBundle, chargedPlacementQuote, activeEconomy);
                        pendingPlacements.remove(ownerId);
                        switch (outcome) {
                            case ALREADY_EXISTS -> messages.send(player, MessageKey.ERROR_ALREADY_EXISTS);
                            case TOO_CLOSE -> {
                                final Component distComp = Component.text(String.format(java.util.Locale.ROOT, "%.1f", effectiveMinDist));
                                player.sendMessage(messages.get(player, MessageKey.ERROR_PLACEMENT_TOO_CLOSE, Placeholder.component("distance", distComp)));
                            }
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
                    final ScheduledTask postPlaceScheduled = player.getScheduler().run(plugin, postPlaceTask -> {
                        try {
                            recordPlacementCooldown(player, normalizedVar);
                            player.sendMessage(messages.aetheryteCreated(player, aetheryte));
                            if (!chargedPlacementQuote.isFree()) {
                                final Component paidDisplay = CostFormatter.format(player, chargedPlacementQuote, activeEconomy, messages, true);
                                player.sendMessage(messages.get(player, MessageKey.AETHERYTE_COST_PAID, Placeholder.component("cost", paidDisplay)));
                            }
                        } finally {
                            pendingPlacements.remove(ownerId);
                        }
                    }, () -> pendingPlacements.remove(ownerId));
                    if (postPlaceScheduled == null) {
                        pendingPlacements.remove(ownerId);
                    }
                } catch (final Throwable t) {
                    try {
                        if (placedAetheryte != null) {
                            final Aetheryte rollbackTarget = placedAetheryte;
                            runRollbackCleanup("unregister", rollbackTarget.id(), () -> registry.unregister(world, rollbackTarget.id()));
                            runRollbackCleanup("removeMarkerEntitiesAt", rollbackTarget.id(), () -> removeMarkerEntitiesAt(world, rollbackTarget));
                            runRollbackCleanup("unregisterModel", rollbackTarget.id(), () -> modelService.unregisterAetheryte(rollbackTarget.id()));
                            runRollbackCleanup("hideHologram", rollbackTarget.id(), () -> hologramService.hideAetheryte(rollbackTarget.id()));
                        }
                        try {
                            refundPlacement(player, targetLocation, normalizedVar, creative, bypassCost, placementCostBundle, chargedPlacementQuote, activeEconomy);
                        } catch (final Throwable refundEx) {
                            plugin.getSLF4JLogger().error("Failed to refund placement cost during rollback for player {}", player.getName(), refundEx);
                        }
                    } finally {
                        pendingPlacements.remove(ownerId);
                    }
                    throw t;
                }
                });
            } catch (final Throwable t) {
                if (!regionTaskEntered.get()) {
                    try {
                        refundPlacement(player, targetLocation, normalizedVar, creative, bypassCost, placementCostBundle, chargedPlacementQuote, activeEconomy);
                    } finally {
                        pendingPlacements.remove(ownerId);
                    }
                }
                throw t;
            }
        }, null);
    }

    private void runRollbackCleanup(final String step, final UUID aetheryteId, final Runnable action) {
        try {
            action.run();
        } catch (final Throwable cleanupEx) {
            plugin.getSLF4JLogger().warn("Rollback cleanup '{}' failed for Aetheryte {}", step, aetheryteId, cleanupEx);
        }
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
                                recordPendingRefund(player.getUniqueId(), result.unrefundedItems());
                                plugin.getSLF4JLogger().warn(
                                        "Placement refund failed for player {}. Queued for next login.",
                                        player.getUniqueId());
                            }
                        },
                        () -> {
                            recordPendingRefund(player.getUniqueId(), costQuote.items());
                            plugin.getSLF4JLogger().warn(
                                    "Player {} disconnected before placement refund could be applied directly. Queued for next login.",
                                    player.getUniqueId());
                        }
                );
                if (scheduled == null) {
                    recordPendingRefund(player.getUniqueId(), costQuote.items());
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
        removeAetheryte(player, aetheryteId, true);
    }

    /**
     * Removes an existing Aetheryte, optionally refunding its creation item (#38, #57).
     */
    public void removeAetheryte(final Player player, final UUID aetheryteId, final boolean refundItem) {
        final Optional<Aetheryte> opt = registry.getById(aetheryteId);
        if (opt.isEmpty()) {
            messages.send(player, MessageKey.ERROR_NOT_FOUND);
            return;
        }
        final Aetheryte aetheryte = opt.get();
        if (!refundItem && !canAdmin(player)) {
            messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return;
        }
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
            final Optional<Aetheryte> latestOpt = registry.getById(aetheryte.id());
            if (latestOpt.isEmpty()) {
                messages.send(player, MessageKey.ERROR_NOT_FOUND);
                return;
            }
            final Aetheryte latest = latestOpt.get();
            if (!refundItem && !canAdmin(player)) {
                messages.send(player, MessageKey.ERROR_NO_PERMISSION);
                return;
            }
            if (!canRemove(player, latest)) {
                messages.send(player, MessageKey.ERROR_NOT_OWNER);
                return;
            }
            final Optional<Aetheryte> removed = registry.unregister(world, latest.id());
            if (removed.isEmpty()) {
                messages.send(player, MessageKey.ERROR_NOT_FOUND);
                return;
            }
            final Aetheryte removedTarget = removed.get();

            removeMarkerEntitiesAt(world, removedTarget);

            // Clean up legacy Lodestone block if present from v0.1 worlds
            final Block block = world.getBlockAt(removedTarget.blockX(), removedTarget.blockY(), removedTarget.blockZ());
            if (block.getType() == Material.LODESTONE) {
                block.setType(Material.AIR, true);
            }

            modelService.unregisterAetheryte(removedTarget.id());
            hologramService.hideAetheryte(removedTarget.id());
            playSoundSafely(() -> world.playSound(blockLoc, Sound.BLOCK_LODESTONE_BREAK, 1.0f, 1.0f));

            if (refundItem && player.getGameMode() != GameMode.CREATIVE) {
                refundItemToPlayer(player, blockLoc, removedTarget.variation());
            }
            if (refundItem) {
                player.sendMessage(messages.aetheryteRemoved(player, removedTarget.name()));
            } else {
                player.sendMessage(messages.get(player, MessageKey.AETHERYTE_FORCE_REMOVED, Placeholder.unparsed("name", removedTarget.name())));
            }
        });
    }

    /**
     * Renames an existing Aetheryte if the player is its owner or holds {@link Permissions#RENAME_ANY} (#57).
     */
    public void renameAetheryte(final Player player, final UUID aetheryteId, final String rawName) {
        final Optional<Aetheryte> opt = registry.getById(aetheryteId);
        if (opt.isEmpty()) {
            messages.send(player, MessageKey.ERROR_NOT_FOUND);
            return;
        }
        final Aetheryte current = opt.get();
        if (!canRename(player, current)) {
            messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return;
        }
        final Optional<String> validName = sanitizeName(rawName);
        if (validName.isEmpty()) {
            messages.send(player, MessageKey.ERROR_INVALID_NAME);
            return;
        }
        final String newName = validName.get();
        executeAetheryteUpdate(
                player,
                current,
                latest -> canRename(player, latest) ? null : MessageKey.ERROR_NO_PERMISSION,
                a -> a.withName(newName),
                (world, previous, updated) -> {
                    hologramService.hideAetheryte(updated.id());
                    hologramService.showAetheryte(updated);
                    player.sendMessage(messages.get(
                            player,
                            MessageKey.AETHERYTE_RENAMED,
                            Placeholder.unparsed("old_name", previous.name()),
                            Placeholder.unparsed("name", updated.name())
                    ));
                }
        );
    }

    /**
     * Transfers ownership of an Aetheryte to another player (Admin only) (#57).
     */
    public void transferOwnership(final Player actor, final UUID aetheryteId, final String targetPlayerName) {
        if (!canAdmin(actor)) {
            messages.send(actor, MessageKey.ERROR_NO_PERMISSION);
            return;
        }
        final Optional<Aetheryte> opt = registry.getById(aetheryteId);
        if (opt.isEmpty()) {
            messages.send(actor, MessageKey.ERROR_NOT_FOUND);
            return;
        }
        final ResolvedPlayer target = playerByNameLookup.apply(targetPlayerName);
        if (target == null) {
            actor.sendMessage(messages.get(actor, MessageKey.ERROR_PLAYER_NOT_FOUND, Placeholder.unparsed("player", targetPlayerName.strip())));
            return;
        }
        executeAetheryteUpdate(
                actor,
                opt.get(),
                latest -> canAdmin(actor) ? null : MessageKey.ERROR_NO_PERMISSION,
                a -> a.withOwner(target.id(), target.name()),
                (world, previous, updated) -> actor.sendMessage(messages.get(
                        actor,
                        MessageKey.AETHERYTE_OWNER_TRANSFERRED,
                        Placeholder.unparsed("name", updated.name()),
                        Placeholder.unparsed("owner", target.name())
                ))
        );
    }

    /**
     * Changes the variation of an existing Aetheryte and refreshes its visuals (Admin only) (#57).
     */
    public void changeVariation(final Player actor, final UUID aetheryteId, final String variationId) {
        if (!canAdmin(actor)) {
            messages.send(actor, MessageKey.ERROR_NO_PERMISSION);
            return;
        }
        final Optional<Aetheryte> opt = registry.getById(aetheryteId);
        if (opt.isEmpty()) {
            messages.send(actor, MessageKey.ERROR_NOT_FOUND);
            return;
        }
        final String normalizedVar = Aetheryte.normalizeVariation(variationId);
        final Optional<AetheryteVariation> variationOpt = variationRegistry.find(normalizedVar);
        if (variationOpt.isEmpty()) {
            actor.sendMessage(messages.commandUnknownVariation(actor, variationId));
            return;
        }
        final AetheryteVariation variation = variationOpt.get();
        executeAetheryteUpdate(
                actor,
                opt.get(),
                latest -> canAdmin(actor) ? null : MessageKey.ERROR_NO_PERMISSION,
                a -> a.withVariation(normalizedVar),
                (world, previous, updated) -> {
                    refreshAllVisuals(world, updated, variation);
                    actor.sendMessage(messages.get(
                            actor,
                            MessageKey.AETHERYTE_VARIATION_CHANGED,
                            Placeholder.unparsed("name", updated.name()),
                            Placeholder.unparsed("variation", normalizedVar)
                    ));
                }
        );
    }

    /**
     * Re-registers client-side hologram and BetterModel entities for an Aetheryte (Admin only) (#57).
     */
    public void resyncVisuals(final Player actor, final UUID aetheryteId) {
        if (!canAdmin(actor)) {
            messages.send(actor, MessageKey.ERROR_NO_PERMISSION);
            return;
        }
        final Optional<Aetheryte> opt = registry.getById(aetheryteId);
        if (opt.isEmpty()) {
            messages.send(actor, MessageKey.ERROR_NOT_FOUND);
            return;
        }
        final Aetheryte target = opt.get();
        final World world = Bukkit.getWorld(target.worldId());
        if (world == null) {
            messages.send(actor, MessageKey.ERROR_NOT_FOUND);
            return;
        }
        Bukkit.getRegionScheduler().execute(plugin, target.blockLocation(world), () -> {
            final Optional<Aetheryte> latestOpt = registry.getById(target.id());
            if (latestOpt.isEmpty()) {
                messages.send(actor, MessageKey.ERROR_NOT_FOUND);
                return;
            }
            if (!canAdmin(actor)) {
                messages.send(actor, MessageKey.ERROR_NO_PERMISSION);
                return;
            }
            final Aetheryte latest = latestOpt.get();
            final AetheryteVariation variation = variationRegistry.getOrDefault(latest.variation());
            refreshAllVisuals(world, latest, variation);
            actor.sendMessage(messages.get(actor, MessageKey.AETHERYTE_VISUALS_RESYNCED, Placeholder.unparsed("name", latest.name())));
        });
    }

    @FunctionalInterface
    private interface AetheryteUpdateCallback {
        void onUpdated(World world, Aetheryte previous, Aetheryte updated);
    }

    private void executeAetheryteUpdate(
            final Player actor,
            final Aetheryte target,
            final Function<Aetheryte, @Nullable MessageKey> regionValidator,
            final UnaryOperator<Aetheryte> updater,
            final AetheryteUpdateCallback onUpdated
    ) {
        final World world = Bukkit.getWorld(target.worldId());
        if (world == null) {
            messages.send(actor, MessageKey.ERROR_NOT_FOUND);
            return;
        }
        Bukkit.getRegionScheduler().execute(plugin, target.blockLocation(world), () -> {
            final Optional<Aetheryte> latestOpt = registry.getById(target.id());
            if (latestOpt.isEmpty()) {
                messages.send(actor, MessageKey.ERROR_NOT_FOUND);
                return;
            }
            final MessageKey validationError = regionValidator.apply(latestOpt.get());
            if (validationError != null) {
                messages.send(actor, validationError);
                return;
            }
            final AtomicReference<@Nullable Aetheryte> previousRef = new AtomicReference<>();
            final Optional<Aetheryte> updatedOpt = registry.update(world, target.id(), existing -> {
                previousRef.set(existing);
                return updater.apply(existing);
            });
            final Aetheryte previous = previousRef.get();
            if (updatedOpt.isEmpty() || previous == null) {
                messages.send(actor, MessageKey.ERROR_NOT_FOUND);
                return;
            }
            final Aetheryte updated = updatedOpt.get();
            updateMarkerDataAt(world, updated);
            onUpdated.onUpdated(world, previous, updated);
        });
    }

    private static void playSoundSafely(final Runnable action) {
        try {
            action.run();
        } catch (final Throwable ignored) {
            // Sound playback is non-fatal if sound registry is unavailable in test environments
        }
    }

    private void refreshAllVisuals(final World world, final Aetheryte aetheryte, final AetheryteVariation variation) {
        modelService.unregisterAetheryte(aetheryte.id());
        modelService.registerAetheryte(aetheryte, variation, world);
        hologramService.hideAetheryte(aetheryte.id());
        hologramService.showAetheryte(aetheryte);
    }

    /**
     * Removes all backing {@link Marker} entities associated with the given Aetheryte in its chunk.
     */
    private static void removeMarkerEntitiesAt(final World world, final Aetheryte target) {
        forEachMarkerEntityAt(world, target, Entity::remove);
    }

    /**
     * Updates PDC data on all backing {@link Marker} entities associated with the given Aetheryte in its chunk.
     */
    private static void updateMarkerDataAt(final World world, final Aetheryte updated) {
        forEachMarkerEntityAt(world, updated, marker -> AetheryteRegistry.writeMarkerData(marker, updated));
    }

    private static void forEachMarkerEntityAt(
            final World world,
            final Aetheryte target,
            final Consumer<Marker> action
    ) {
        final @Nullable Chunk chunk = world.getChunkAt(target.blockX() >> 4, target.blockZ() >> 4);
        if (chunk == null) {
            return;
        }
        for (final Entity entity : chunk.getEntities()) {
            if (entity instanceof Marker marker) {
                final Optional<Aetheryte> data = AetheryteRegistry.readMarkerData(marker);
                if (data.isPresent() && data.get().id().equals(target.id())) {
                    action.accept(marker);
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

        final AtomicBoolean regionTaskEntered = new AtomicBoolean(false);
        try {
            Bukkit.getRegionScheduler().execute(plugin, checkLoc, () -> {
                regionTaskEntered.set(true);
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

                final ScheduledTask warpTask = player.getScheduler().run(plugin, task -> {
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

                        final WarpEvaluation finalEval = evaluateWarp(player, source, destination);
                        if (!finalEval.allowed()) {
                            if (playerId != null) {
                                inFlightWarps.remove(playerId);
                            }
                            final Component reason = finalEval.denyReason() != null
                                    ? finalEval.denyReason()
                                    : messages.get(player, MessageKey.ERROR_INSUFFICIENT_COST);
                            player.sendMessage(messages.errorWarpDenied(player, reason));
                            return;
                        }

                        if (!bypassCost) {
                            paid = activeEvaluator.charge(player, source, destination);
                            if (!paid) {
                                final List<CostItemQuote> unrefunded = activeEvaluator.popFailedChargeUnrefunded(player, destination);
                                if (!unrefunded.isEmpty()) {
                                    recordPendingRefund(player.getUniqueId(), unrefunded);
                                    plugin.getSLF4JLogger().warn(
                                            "Warp charge rollback partially failed for player {}. Queued {} unrefunded item(s) for next login.",
                                            player.getUniqueId(),
                                            unrefunded.size()
                                    );
                                }
                                if (playerId != null) {
                                    inFlightWarps.remove(playerId);
                                }
                                player.sendMessage(messages.errorWarpDenied(player, messages.get(player, MessageKey.ERROR_INSUFFICIENT_COST)));
                                return;
                            }
                        } else {
                            paid = true;
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
                                    final List<CostItemQuote> peeked = activeEvaluator.peekPendingCharge(player, destination);
                                    final List<CostItemQuote> chargedItems = !peeked.isEmpty()
                                            ? peeked
                                            : activeEvaluator.popPendingCharge(player, destination);
                                    activeEvaluator.commit(player, source, destination);
                                    final CostQuote paidQuote = new CostQuote(chargedItems);
                                    if (!paidQuote.isFree()) {
                                        final Component paidDisplay = CostFormatter.format(player, paidQuote, this.economyProvider, messages, true);
                                        player.sendMessage(messages.get(player, MessageKey.WARP_COST_PAID, Placeholder.component("cost", paidDisplay)));
                                    }
                                }
                                modelService.triggerAnimation(destination.id(), ModelAnimationTrigger.ON_WARP_ARRIVE);
                                player.sendMessage(messages.warpedTo(player, destination));
                                player.getScheduler().run(plugin, postTask -> {
                                    playSoundSafely(() -> player.playSound(destinationLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f));
                                    refreshPlayerVisuals(player, targetWorld);
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
                if (warpTask == null && playerId != null) {
                    inFlightWarps.remove(playerId);
                }
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
        } catch (final Throwable t) {
            if (playerId != null) {
                inFlightWarps.remove(playerId);
            }
            if (!regionTaskEntered.get()) {
                plugin.getSLF4JLogger().error(
                        "Failed to schedule warp landing resolution for player {} to {}",
                        player.getName(),
                        destination.id(),
                        t
                );
                messages.send(player, MessageKey.ERROR_NOT_FOUND);
            }
        }
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
                        recordPendingRefund(player.getUniqueId(), unrefunded);
                        plugin.getSLF4JLogger().warn("Warp refund failed for player {}. Queued for next login.", player.getUniqueId());
                    }
                }
            }
            messages.send(player, errorKey);
        }, () -> {
            if (!bypassCost) {
                final List<CostItemQuote> unrefunded = evaluator.popPendingCharge(player, destination);
                if (!unrefunded.isEmpty()) {
                    recordPendingRefund(player.getUniqueId(), unrefunded);
                    plugin.getSLF4JLogger().warn(
                            "Player {} disconnected before warp refund could execute. Queued for next login.",
                            player.getUniqueId());
                }
            }
        });

        if (task == null && !bypassCost) {
            final List<CostItemQuote> unrefunded = evaluator.popPendingCharge(player, destination);
            if (!unrefunded.isEmpty()) {
                recordPendingRefund(player.getUniqueId(), unrefunded);
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

    /**
     * Refreshes client-side visuals for a player with staggered updates to ensure entities render
     * cleanly across dimension loading screens.
     */
    public void refreshPlayerVisuals(final Player player, final World world) {
        final UUID worldId = world.getUID();
        refreshPlayerVisualsNow(player, worldId);
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.getWorld().getUID().equals(worldId)) {
                refreshPlayerVisualsNow(player, worldId);
            }
        }, null, 5L);
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.getWorld().getUID().equals(worldId)) {
                refreshPlayerVisualsNow(player, worldId);
            }
        }, null, 20L);
    }

    private void refreshPlayerVisualsNow(final Player player, final UUID worldId) {
        final List<Aetheryte> visible = collectVisibleAetherytes(registry, player, worldId);
        hologramService.refreshPlayer(player, visible);
        modelService.refreshPlayer(player, visible);
    }

    /**
     * Collects Aetherytes within the player's chunk view distance for visual refresh (#20 Option A),
     * falling back to all Aetherytes in {@code worldId} when player location is unavailable (e.g. unit test mocks).
     */
    public static List<Aetheryte> collectVisibleAetherytes(
            final AetheryteRegistry registry,
            final Player player,
            final UUID worldId
    ) {
        final Location loc = player.getLocation();
        if (loc == null) {
            return registry.getByWorld(worldId);
        }
        final int viewDistance = resolveEffectiveViewDistance(player);
        final int centerChunkX = loc.getBlockX() >> 4;
        final int centerChunkZ = loc.getBlockZ() >> 4;
        final List<Aetheryte> visible = new ArrayList<>();
        for (int cx = centerChunkX - viewDistance; cx <= centerChunkX + viewDistance; cx++) {
            for (int cz = centerChunkZ - viewDistance; cz <= centerChunkZ + viewDistance; cz++) {
                final List<Aetheryte> chunkList = registry.getByChunk(worldId, cx, cz);
                if (!chunkList.isEmpty()) {
                    visible.addAll(chunkList);
                }
            }
        }
        return visible;
    }

    private static int resolveEffectiveViewDistance(final Player player) {
        final int playerDist = player.getSendViewDistance();
        if (playerDist > 0) {
            return Math.min(playerDist, 64);
        }
        final org.bukkit.Server server = Bukkit.getServer();
        if (server != null && server.getViewDistance() > 0) {
            return Math.min(server.getViewDistance(), 64);
        }
        return 10;
    }
}
