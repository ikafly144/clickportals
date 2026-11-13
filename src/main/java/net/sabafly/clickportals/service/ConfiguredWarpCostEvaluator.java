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

import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.config.DistanceSection;
import net.sabafly.clickportals.config.PluginConfig;
import net.sabafly.clickportals.cost.CostBundle;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.CostFormatter;
import net.sabafly.clickportals.cost.CostItemQuote;
import net.sabafly.clickportals.cost.CostQuote;
import net.sabafly.clickportals.economy.EconomyProvider;
import net.sabafly.clickportals.message.MessageKey;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.route.DimensionRouter;
import net.sabafly.clickportals.route.RoutePlan;
import net.sabafly.clickportals.service.AetheryteService.WarpCostEvaluator;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation.DenyReason;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.VariationRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Production implementation of {@link WarpCostEvaluator} handling distance limits,
 * multi-dimensional routes, and composable cost assessments (#9, #14, #16, #26).
 */
public final class ConfiguredWarpCostEvaluator implements WarpCostEvaluator {

    private static final double BASE_INTERACT_REACH = 6.0;

    private final Supplier<PluginConfig> configSupplier;
    private final VariationRegistry variationRegistry;
    private final Supplier<EconomyProvider> economySupplier;
    private final Supplier<DimensionRouter> routerSupplier;
    private final Messages messages;
    private final Function<UUID, @Nullable World> worldResolver;

    private record ChargedPayment(CostBundle bundle, CostQuote quote, EconomyProvider economy) {
    }

    private record WarpChargeKey(UUID playerId, UUID destinationId) {
    }

    private final Map<WarpChargeKey, Deque<ChargedPayment>> activeCharges = new ConcurrentHashMap<>();
    private final Map<WarpChargeKey, Deque<List<CostItemQuote>>> failedChargeUnrefunded = new ConcurrentHashMap<>();

    public ConfiguredWarpCostEvaluator(
            final Supplier<PluginConfig> configSupplier,
            final VariationRegistry variationRegistry,
            final Supplier<EconomyProvider> economySupplier,
            final Supplier<DimensionRouter> routerSupplier,
            final Messages messages
    ) {
        this(configSupplier, variationRegistry, economySupplier, routerSupplier, messages, Bukkit::getWorld);
    }

    public ConfiguredWarpCostEvaluator(
            final Supplier<PluginConfig> configSupplier,
            final VariationRegistry variationRegistry,
            final Supplier<EconomyProvider> economySupplier,
            final Supplier<DimensionRouter> routerSupplier,
            final Messages messages,
            final Function<UUID, @Nullable World> worldResolver
    ) {
        this.configSupplier = configSupplier;
        this.variationRegistry = variationRegistry;
        this.economySupplier = economySupplier;
        this.routerSupplier = routerSupplier;
        this.messages = messages;
        this.worldResolver = worldResolver;
    }

    private record EvaluationContext(
            double blockDistance,
            double horizontalDistance,
            double chunkDistance,
            double dy,
            double crossDimension,
            double hops,
            double dimensionCostAmount,
            List<CostEntry> effectiveEntries,
            @Nullable Component blockReason,
            @Nullable DenyReason blockReasonCode
    ) {
    }

    private record CachedRoute(
            boolean hasRoute,
            double originX,
            double originY,
            double originZ,
            double hops,
            double dimensionCostAmount,
            CostBundle bundle,
            boolean hasCostEntries
    ) {
    }

    @Override
    public WarpEvaluation evaluate(
            final Player player,
            final @Nullable Aetheryte source,
            final Aetheryte destination
    ) {
        final EvaluationContext ctx = resolveContext(player, source, destination);
        final PluginConfig config = configSupplier.get();
        final EconomyProvider economy = economySupplier.get();

        final CostBundle bundle = new CostBundle(ctx.effectiveEntries());
        final Map<String, Double> vars = buildVariables(ctx);
        final CostQuote quote = bundle.quote(player, vars, economy);
        final Locale playerLocale = messages.locale(player);
        final Component costDisplay = CostFormatter.format(playerLocale, quote, economy, messages, config.showOptionalCosts());

        return toWarpEvaluation(playerLocale, quote, economy, costDisplay, ctx.blockReason(), ctx.blockReasonCode());
    }

    @Override
    public List<WarpEvaluation> evaluateBatch(
            final Player player,
            final @Nullable Aetheryte source,
            final List<Aetheryte> destinations
    ) {
        if (destinations.isEmpty()) {
            return List.of();
        }

        final PluginConfig config = configSupplier.get();
        final EconomyProvider economy = economySupplier.get();
        final DimensionRouter router = routerSupplier.get();
        final boolean showOptionalCosts = config.showOptionalCosts();
        final Locale playerLocale = messages.locale(player);

        final List<CostEntry> baseEntries = resolveBaseCostEntries(source, config);
        final CostBundle baseBundle = new CostBundle(baseEntries);
        final boolean baseHasEntries = !baseEntries.isEmpty();
        final DistanceSection effectiveDistanceLimits = resolveDistanceLimits(source, config);

        final @Nullable World sourceWorld = source != null ? worldResolver.apply(source.worldId()) : player.getWorld();
        final @Nullable Location playerLoc = player.getLocation();
        final Location fallbackSrc = playerLoc != null
                ? playerLoc
                : new Location(sourceWorld, 0.0, 64.0, 0.0);
        final @Nullable Location fixedSrcLoc = source != null
                ? (sourceWorld != null ? new Location(sourceWorld, source.blockX(), source.blockY(), source.blockZ()) : null)
                : fallbackSrc;

        final @Nullable DenyReason sourceReachError = resolveSourceReachError(player, source, sourceWorld, playerLoc);
        final boolean applyCrossDimLimits = effectiveDistanceLimits.crossDimensionOrDefault(true);
        final double minDist = effectiveDistanceLimits.minOrDefault(0.0);
        final double maxDist = effectiveDistanceLimits.maxOrDefault(-1.0);

        final @Nullable UUID sourceWorldId = sourceWorld != null ? sourceWorld.getUID() : null;
        final Location sameWorldOriginLoc = fixedSrcLoc != null ? fixedSrcLoc : fallbackSrc;
        final double sameWorldOriginX = sameWorldOriginLoc.getX();
        final double sameWorldOriginY = sameWorldOriginLoc.getY();
        final double sameWorldOriginZ = sameWorldOriginLoc.getZ();

        final Map<UUID, Optional<World>> worldCache = new HashMap<>();
        if (sourceWorld != null && sourceWorldId != null) {
            worldCache.put(sourceWorldId, Optional.of(sourceWorld));
        }
        final Map<UUID, CachedRoute> routeCache = new HashMap<>();
        final EnumMap<DenyReason, Component> errorCache = new EnumMap<>(DenyReason.class);
        final Map<String, Double> vars = new HashMap<>(16);

        CostBundle.@Nullable PlayerResourceSnapshot snapshot = null;
        @Nullable Component cachedFreeCostDisplay = null;
        final List<WarpEvaluation> results = new ArrayList<>(destinations.size());

        for (final Aetheryte destination : destinations) {
            final UUID destWorldId = destination.worldId();
            final boolean sameWorld = sourceWorldId != null && destWorldId.equals(sourceWorldId);

            final double originX;
            final double originY;
            final double originZ;
            final double crossDim;
            final double hops;
            final double dimCostAmt;
            final CostBundle bundle;
            final boolean bundleHasEntries;
            @Nullable DenyReason blockReasonCode = null;

            if (sameWorld) {
                originX = sameWorldOriginX;
                originY = sameWorldOriginY;
                originZ = sameWorldOriginZ;
                crossDim = 0.0;
                hops = 0.0;
                dimCostAmt = 0.0;
                bundle = baseBundle;
                bundleHasEntries = baseHasEntries;
            } else {
                final @Nullable World targetWorld = worldCache
                        .computeIfAbsent(destWorldId, id -> Optional.ofNullable(worldResolver.apply(id)))
                        .orElse(null);

                if (targetWorld == null) {
                    final CostQuote quote;
                    if (baseHasEntries) {
                        if (snapshot == null) {
                            snapshot = CostBundle.PlayerResourceSnapshot.capture(player, economy);
                            snapshot.populateVariables(vars);
                        }
                        populateContextVariables(vars, 0, 0, 0, 0, 0, 0, 0);
                        quote = baseBundle.quoteWithPopulatedVariables(snapshot, vars);
                    } else {
                        quote = CostQuote.FREE;
                    }
                    final Component costDisplay;
                    if (quote.items().isEmpty()) {
                        if (cachedFreeCostDisplay == null) {
                            cachedFreeCostDisplay = CostFormatter.format(playerLocale, quote, economy, messages, showOptionalCosts);
                        }
                        costDisplay = cachedFreeCostDisplay;
                    } else {
                        costDisplay = CostFormatter.format(playerLocale, quote, economy, messages, showOptionalCosts);
                    }
                    results.add(WarpEvaluation.denied(
                            costDisplay,
                            resolveErrorComponent(errorCache, playerLocale, DenyReason.NOT_FOUND),
                            DenyReason.NOT_FOUND
                    ));
                    continue;
                }

                if (sourceWorld == null || fixedSrcLoc == null) {
                    originX = 0.0;
                    originY = 0.0;
                    originZ = 0.0;
                    crossDim = 0.0;
                    hops = 0.0;
                    dimCostAmt = 0.0;
                    bundle = baseBundle;
                    bundleHasEntries = baseHasEntries;
                    blockReasonCode = DenyReason.NO_ROUTE;
                } else {
                    final CachedRoute cachedRoute = routeCache.computeIfAbsent(destWorldId, id -> {
                        final Optional<RoutePlan> plan = router.findRoute(sourceWorld, targetWorld, fixedSrcLoc);
                        if (plan.isEmpty()) {
                            return new CachedRoute(false, 0.0, 0.0, 0.0, 0.0, 0.0, baseBundle, baseHasEntries);
                        }
                        final RoutePlan route = plan.get();
                        final Location origin = route.targetOrigin();
                        final List<CostEntry> extra = route.dimensionCostEntries();
                        final CostBundle resolvedBundle;
                        final boolean hasEntries;
                        if (extra.isEmpty()) {
                            resolvedBundle = baseBundle;
                            hasEntries = baseHasEntries;
                        } else {
                            final List<CostEntry> combined = new ArrayList<>(baseEntries.size() + extra.size());
                            combined.addAll(baseEntries);
                            combined.addAll(extra);
                            resolvedBundle = new CostBundle(combined);
                            hasEntries = true;
                        }
                        return new CachedRoute(
                                true,
                                origin.getX(),
                                origin.getY(),
                                origin.getZ(),
                                route.hops(),
                                route.dimensionCostAmount(),
                                resolvedBundle,
                                hasEntries
                        );
                    });
                    if (!cachedRoute.hasRoute()) {
                        originX = 0.0;
                        originY = 0.0;
                        originZ = 0.0;
                        crossDim = 0.0;
                        hops = 0.0;
                        dimCostAmt = 0.0;
                        bundle = baseBundle;
                        bundleHasEntries = baseHasEntries;
                        blockReasonCode = DenyReason.NO_ROUTE;
                    } else {
                        originX = cachedRoute.originX();
                        originY = cachedRoute.originY();
                        originZ = cachedRoute.originZ();
                        crossDim = 1.0;
                        hops = cachedRoute.hops();
                        dimCostAmt = cachedRoute.dimensionCostAmount();
                        bundle = cachedRoute.bundle();
                        bundleHasEntries = cachedRoute.hasCostEntries();
                    }
                }
            }

            final double blockDist;
            final double horizDist;
            final double chunkDist;
            final double dy;
            if (blockReasonCode == DenyReason.NO_ROUTE) {
                blockDist = 0.0;
                horizDist = 0.0;
                chunkDist = 0.0;
                dy = 0.0;
            } else {
                final double dx = destination.blockX() - originX;
                final double dY = destination.blockY() - originY;
                final double dz = destination.blockZ() - originZ;
                blockDist = Math.sqrt(dx * dx + dY * dY + dz * dz);
                horizDist = Math.sqrt(dx * dx + dz * dz);
                chunkDist = horizDist / 16.0;
                dy = Math.abs(dY);

                if (sourceReachError != null) {
                    blockReasonCode = sourceReachError;
                } else if (crossDim <= 0.0 || applyCrossDimLimits) {
                    if (minDist > 0.0 && blockDist < minDist) {
                        blockReasonCode = DenyReason.TOO_CLOSE;
                    } else if (maxDist >= 0.0 && blockDist > maxDist) {
                        blockReasonCode = DenyReason.TOO_FAR;
                    }
                }
            }

            final CostQuote quote;
            if (bundleHasEntries) {
                if (snapshot == null) {
                    snapshot = CostBundle.PlayerResourceSnapshot.capture(player, economy);
                    snapshot.populateVariables(vars);
                }
                populateContextVariables(vars, blockDist, horizDist, chunkDist, dy, crossDim, hops, dimCostAmt);
                quote = bundle.quoteWithPopulatedVariables(snapshot, vars);
            } else {
                quote = CostQuote.FREE;
            }

            final Component costDisplay;
            if (quote.items().isEmpty()) {
                if (cachedFreeCostDisplay == null) {
                    cachedFreeCostDisplay = CostFormatter.format(playerLocale, quote, economy, messages, showOptionalCosts);
                }
                costDisplay = cachedFreeCostDisplay;
            } else {
                costDisplay = CostFormatter.format(playerLocale, quote, economy, messages, showOptionalCosts);
            }

            final @Nullable Component blockReason = blockReasonCode != null
                    ? resolveErrorComponent(errorCache, playerLocale, blockReasonCode)
                    : null;
            results.add(toWarpEvaluation(playerLocale, quote, economy, costDisplay, blockReason, blockReasonCode));
        }

        return results;
    }

    private WarpEvaluation toWarpEvaluation(
            final Locale playerLocale,
            final CostQuote quote,
            final EconomyProvider economy,
            final Component costDisplay,
            final @Nullable Component blockReason,
            final @Nullable DenyReason blockReasonCode
    ) {
        if (blockReason != null) {
            final DenyReason code = blockReasonCode != null ? blockReasonCode : DenyReason.OTHER;
            return WarpEvaluation.denied(costDisplay, blockReason, code);
        }

        if (!quote.affordable()) {
            final Component reason = CostFormatter.formatMissingRequirements(playerLocale, quote, economy, messages);
            return WarpEvaluation.denied(costDisplay, reason, DenyReason.INSUFFICIENT_COST);
        }

        return WarpEvaluation.permitted(costDisplay);
    }

    private @Nullable DenyReason resolveSourceReachError(
            final Player player,
            final @Nullable Aetheryte source,
            final @Nullable World sourceWorld,
            final @Nullable Location playerLoc
    ) {
        if (source == null) {
            return null;
        }
        if (sourceWorld == null || !player.getWorld().getUID().equals(source.worldId()) || playerLoc == null) {
            return DenyReason.NOT_FOUND;
        }
        final AetheryteVariation sourceVar = variationRegistry.getOrDefault(source.variation());
        final double hitboxAllowance = Math.max(sourceVar.hitbox().width(), sourceVar.hitbox().height());
        final double maxReach = BASE_INTERACT_REACH + hitboxAllowance;
        if (playerLoc.distanceSquared(source.markerLocation(sourceWorld)) > maxReach * maxReach) {
            return DenyReason.TOO_FAR;
        }
        return null;
    }

    private Component resolveErrorComponent(
            final EnumMap<DenyReason, Component> cache,
            final Locale playerLocale,
            final DenyReason reason
    ) {
        return cache.computeIfAbsent(reason, r -> switch (r) {
            case NOT_FOUND -> messages.get(playerLocale, MessageKey.ERROR_NOT_FOUND);
            case NO_ROUTE -> messages.get(playerLocale, MessageKey.ERROR_NO_ROUTE);
            case TOO_CLOSE -> messages.get(playerLocale, MessageKey.ERROR_TOO_CLOSE);
            case TOO_FAR -> messages.get(playerLocale, MessageKey.ERROR_TOO_FAR);
            case INSUFFICIENT_COST, NO_PERMISSION, OTHER -> messages.get(playerLocale, MessageKey.ERROR_NOT_FOUND);
        });
    }

    @Override
    public boolean charge(
            final Player player,
            final @Nullable Aetheryte source,
            final Aetheryte destination
    ) {
        final EvaluationContext ctx = resolveContext(player, source, destination);
        if (ctx.blockReason() != null) {
            return false;
        }

        final CostBundle bundle = new CostBundle(ctx.effectiveEntries());
        final Map<String, Double> vars = buildVariables(ctx);
        final EconomyProvider economy = economySupplier.get();
        final CostQuote quote = bundle.quote(player, vars, economy);

        final CostBundle.ChargeResult result = bundle.chargeWithResult(player, quote, economy);
        final WarpChargeKey key = new WarpChargeKey(player.getUniqueId(), destination.id());
        if (result.success()) {
            if (!result.chargedQuote().isFree()) {
                activeCharges.compute(key, (k, existing) -> {
                    final Deque<ChargedPayment> deque = existing != null ? existing : new ConcurrentLinkedDeque<>();
                    deque.push(new ChargedPayment(bundle, result.chargedQuote(), economy));
                    return deque;
                });
            }
        } else if (!result.unrefundedItems().isEmpty()) {
            failedChargeUnrefunded.compute(key, (k, existing) -> {
                final Deque<List<CostItemQuote>> deque = existing != null ? existing : new ConcurrentLinkedDeque<>();
                deque.push(result.unrefundedItems());
                return deque;
            });
        }
        return result.success();
    }

    @Override
    public boolean refund(
            final Player player,
            final @Nullable Aetheryte source,
            final Aetheryte destination
    ) {
        final WarpChargeKey key = new WarpChargeKey(player.getUniqueId(), destination.id());
        final AtomicReference<@Nullable ChargedPayment> polled = new AtomicReference<>();
        activeCharges.computeIfPresent(key, (k, deque) -> {
            polled.set(deque.poll());
            return deque.isEmpty() ? null : deque;
        });
        final ChargedPayment payment = polled.get();
        if (payment == null) {
            return true;
        }
        final CostBundle.RefundResult result = payment.bundle().refundWithResult(player, payment.quote(), payment.economy());
        if (!result.success()) {
            final CostQuote unrefundedQuote = new CostQuote(result.unrefundedItems(), true);
            activeCharges.compute(key, (k, existing) -> {
                final Deque<ChargedPayment> deque = existing != null ? existing : new ConcurrentLinkedDeque<>();
                deque.push(new ChargedPayment(payment.bundle(), unrefundedQuote, payment.economy()));
                return deque;
            });
            return false;
        }
        return true;
    }

    @Override
    public void commit(
            final Player player,
            final @Nullable Aetheryte source,
            final Aetheryte destination
    ) {
        final WarpChargeKey key = new WarpChargeKey(player.getUniqueId(), destination.id());
        activeCharges.computeIfPresent(key, (k, deque) -> {
            deque.poll();
            return deque.isEmpty() ? null : deque;
        });
    }

    @Override
    public List<CostItemQuote> popPendingCharge(final Player player, final Aetheryte destination) {
        final WarpChargeKey key = new WarpChargeKey(player.getUniqueId(), destination.id());
        final AtomicReference<@Nullable ChargedPayment> polled = new AtomicReference<>();
        activeCharges.computeIfPresent(key, (k, deque) -> {
            polled.set(deque.poll());
            return deque.isEmpty() ? null : deque;
        });
        final ChargedPayment payment = polled.get();
        return payment != null ? payment.quote().items() : List.of();
    }

    @Override
    public List<CostItemQuote> peekPendingCharge(final Player player, final Aetheryte destination) {
        final WarpChargeKey key = new WarpChargeKey(player.getUniqueId(), destination.id());
        final Deque<ChargedPayment> deque = activeCharges.get(key);
        if (deque == null) {
            return List.of();
        }
        final ChargedPayment payment = deque.peek();
        return payment != null ? payment.quote().items() : List.of();
    }

    @Override
    public List<CostItemQuote> popFailedChargeUnrefunded(final Player player, final Aetheryte destination) {
        final WarpChargeKey key = new WarpChargeKey(player.getUniqueId(), destination.id());
        final AtomicReference<@Nullable List<CostItemQuote>> polled = new AtomicReference<>();
        failedChargeUnrefunded.computeIfPresent(key, (k, deque) -> {
            polled.set(deque.poll());
            return deque.isEmpty() ? null : deque;
        });
        final List<CostItemQuote> items = polled.get();
        return items != null ? items : List.of();
    }

    public void clearPayment(final Player player) {
        final UUID playerId = player.getUniqueId();
        activeCharges.keySet().removeIf(k -> k.playerId().equals(playerId));
        failedChargeUnrefunded.keySet().removeIf(k -> k.playerId().equals(playerId));
    }

    private EvaluationContext resolveContext(
            final Player player,
            final @Nullable Aetheryte source,
            final Aetheryte destination
    ) {
        final PluginConfig config = configSupplier.get();
        final World targetWorld = worldResolver.apply(destination.worldId());
        final World sourceWorld = source != null ? worldResolver.apply(source.worldId()) : player.getWorld();

        final List<CostEntry> baseEntries = resolveBaseCostEntries(source, config);
        final List<CostEntry> extraEntries = new ArrayList<>();

        if (targetWorld == null) {
            return new EvaluationContext(0, 0, 0, 0, 0, 0, 0, baseEntries,
                    messages.get(player, MessageKey.ERROR_NOT_FOUND), WarpEvaluation.DenyReason.NOT_FOUND);
        }

        final boolean sameWorld = sourceWorld != null && sourceWorld.getUID().equals(targetWorld.getUID());
        final double blockDist;
        final double horizDist;
        final double chunkDist;
        final double dy;
        final double crossDim;
        final double hops;
        final double dimCostAmt;

        final Location playerLoc = player.getLocation();
        final Location fallbackSrc = playerLoc != null
                ? playerLoc
                : new Location(sourceWorld != null ? sourceWorld : targetWorld, 0.0, 64.0, 0.0);

        if (sameWorld) {
            final Location srcLoc = source != null
                    ? new Location(sourceWorld, source.blockX(), source.blockY(), source.blockZ())
                    : fallbackSrc;
            final double dx = destination.blockX() - srcLoc.getX();
            final double dY = destination.blockY() - srcLoc.getY();
            final double dz = destination.blockZ() - srcLoc.getZ();
            blockDist = Math.sqrt(dx * dx + dY * dY + dz * dz);
            horizDist = Math.sqrt(dx * dx + dz * dz);
            chunkDist = horizDist / 16.0;
            dy = Math.abs(dY);
            crossDim = 0.0;
            hops = 0.0;
            dimCostAmt = 0.0;
        } else {
            if (sourceWorld == null) {
                return new EvaluationContext(0, 0, 0, 0, 0, 0, 0, baseEntries,
                        messages.get(player, MessageKey.ERROR_NO_ROUTE), WarpEvaluation.DenyReason.NO_ROUTE);
            }
            final Location srcLoc = source != null
                    ? new Location(sourceWorld, source.blockX(), source.blockY(), source.blockZ())
                    : fallbackSrc;
            final DimensionRouter router = routerSupplier.get();
            final Optional<RoutePlan> plan = router.findRoute(sourceWorld, targetWorld, srcLoc);
            if (plan.isEmpty()) {
                return new EvaluationContext(0, 0, 0, 0, 0, 0, 0, baseEntries,
                        messages.get(player, MessageKey.ERROR_NO_ROUTE), WarpEvaluation.DenyReason.NO_ROUTE);
            }

            final RoutePlan route = plan.get();
            final Location origin = route.targetOrigin();
            final double dx = destination.blockX() - origin.getX();
            final double dY = destination.blockY() - origin.getY();
            final double dz = destination.blockZ() - origin.getZ();
            blockDist = Math.sqrt(dx * dx + dY * dY + dz * dz);
            horizDist = Math.sqrt(dx * dx + dz * dz);
            chunkDist = horizDist / 16.0;
            dy = Math.abs(dY);
            crossDim = 1.0;
            hops = route.hops();
            dimCostAmt = route.dimensionCostAmount();
            extraEntries.addAll(route.dimensionCostEntries());
        }

        final List<CostEntry> allEntries = new ArrayList<>(baseEntries);
        allEntries.addAll(extraEntries);

        // Validate source reach and world match if source Aetheryte was provided (#40)
        if (source != null) {
            if (sourceWorld == null || !player.getWorld().getUID().equals(source.worldId())) {
                return new EvaluationContext(blockDist, horizDist, chunkDist, dy, crossDim, hops, dimCostAmt, allEntries,
                        messages.get(player, MessageKey.ERROR_NOT_FOUND), WarpEvaluation.DenyReason.NOT_FOUND);
            }
            final Location pLoc = player.getLocation();
            if (pLoc == null) {
                return new EvaluationContext(blockDist, horizDist, chunkDist, dy, crossDim, hops, dimCostAmt, allEntries,
                        messages.get(player, MessageKey.ERROR_NOT_FOUND), WarpEvaluation.DenyReason.NOT_FOUND);
            }
            final AetheryteVariation sourceVar = variationRegistry.getOrDefault(source.variation());
            final double hitboxAllowance = Math.max(sourceVar.hitbox().width(), sourceVar.hitbox().height());
            final double maxReach = BASE_INTERACT_REACH + hitboxAllowance;
            if (pLoc.distanceSquared(source.markerLocation(sourceWorld)) > maxReach * maxReach) {
                return new EvaluationContext(blockDist, horizDist, chunkDist, dy, crossDim, hops, dimCostAmt, allEntries,
                        messages.get(player, MessageKey.ERROR_TOO_FAR), WarpEvaluation.DenyReason.TOO_FAR);
            }
        }

        // Distance limits (#26)
        final DistanceSection effectiveDistanceLimits = resolveDistanceLimits(source, config);
        final boolean applyDistanceLimits = crossDim <= 0.0 || effectiveDistanceLimits.crossDimensionOrDefault(true);

        if (applyDistanceLimits) {
            final double minDist = effectiveDistanceLimits.minOrDefault(0.0);
            final double maxDist = effectiveDistanceLimits.maxOrDefault(-1.0);

            if (minDist > 0.0 && blockDist < minDist) {
                return new EvaluationContext(blockDist, horizDist, chunkDist, dy, crossDim, hops, dimCostAmt, allEntries,
                        messages.get(player, MessageKey.ERROR_TOO_CLOSE), WarpEvaluation.DenyReason.TOO_CLOSE);
            }
            if (maxDist >= 0.0 && blockDist > maxDist) {
                return new EvaluationContext(blockDist, horizDist, chunkDist, dy, crossDim, hops, dimCostAmt, allEntries,
                        messages.get(player, MessageKey.ERROR_TOO_FAR), WarpEvaluation.DenyReason.TOO_FAR);
            }
        }

        return new EvaluationContext(blockDist, horizDist, chunkDist, dy, crossDim, hops, dimCostAmt, allEntries, null, null);
    }

    private DistanceSection resolveDistanceLimits(final @Nullable Aetheryte source, final PluginConfig config) {
        if (source != null) {
            final AetheryteVariation var = variationRegistry.getOrDefault(source.variation());
            final DistanceSection override = var.warp().distance();
            final double min = override.minOrDefault(config.warpDistanceMin());
            double max = override.maxOrDefault(config.warpDistanceMax());
            if (min >= 0.0 && max >= 0.0 && min > max) {
                // Effective min exceeds effective max, clamp max to unlimited (-1.0) to prevent deadlocks (#26)
                max = -1.0;
            }
            final boolean crossDim = override.crossDimensionOrDefault(config.warpDistanceCrossDimension());
            return new DistanceSection(min, max, crossDim);
        }
        return new DistanceSection(config.warpDistanceMin(), config.warpDistanceMax(), config.warpDistanceCrossDimension());
    }

    private List<CostEntry> resolveBaseCostEntries(final @Nullable Aetheryte source, final PluginConfig config) {
        if (source != null) {
            final AetheryteVariation var = variationRegistry.getOrDefault(source.variation());
            final List<CostEntry> override = var.warp().cost();
            if (override != null) {
                return override;
            }
        }
        return config.warpCost();
    }

    private static Map<String, Double> buildVariables(final EvaluationContext ctx) {
        final Map<String, Double> map = new HashMap<>(16);
        populateContextVariables(
                map,
                ctx.blockDistance(),
                ctx.horizontalDistance(),
                ctx.chunkDistance(),
                ctx.dy(),
                ctx.crossDimension(),
                ctx.hops(),
                ctx.dimensionCostAmount()
        );
        return map;
    }

    private static void populateContextVariables(
            final Map<String, Double> map,
            final double blockDistance,
            final double horizontalDistance,
            final double chunkDistance,
            final double dy,
            final double crossDimension,
            final double hops,
            final double dimensionCostAmount
    ) {
        map.put("block_distance", blockDistance);
        map.put("horizontal_distance", horizontalDistance);
        map.put("chunk_distance", chunkDistance);
        map.put("dy", dy);
        map.put("cross_dimension", crossDimension);
        map.put("hops", hops);
        map.put("dimension_cost", dimensionCostAmount);
        map.put("amount", dimensionCostAmount);
    }
}
