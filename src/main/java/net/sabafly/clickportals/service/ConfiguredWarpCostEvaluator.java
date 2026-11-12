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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.config.DistanceSection;
import net.sabafly.clickportals.config.PluginConfig;
import net.sabafly.clickportals.cost.CostBundle;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.CostFormatter;
import net.sabafly.clickportals.cost.CostQuote;
import net.sabafly.clickportals.economy.EconomyProvider;
import net.sabafly.clickportals.message.MessageKey;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.route.DimensionRouter;
import net.sabafly.clickportals.route.RoutePlan;
import net.sabafly.clickportals.service.AetheryteService.WarpCostEvaluator;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation;
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

    private final Supplier<PluginConfig> configSupplier;
    private final VariationRegistry variationRegistry;
    private final Supplier<EconomyProvider> economySupplier;
    private final Supplier<DimensionRouter> routerSupplier;
    private final Messages messages;
    private final Function<UUID, @Nullable World> worldResolver;

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
            @Nullable Component blockReason
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

        if (ctx.blockReason() != null) {
            return WarpEvaluation.denied(messages.freeCostLabel(player), ctx.blockReason());
        }

        final CostBundle bundle = new CostBundle(ctx.effectiveEntries());
        final Map<String, Double> vars = buildVariables(ctx);
        final CostQuote quote = bundle.quote(player, vars, economy);
        final Component costDisplay = CostFormatter.format(player, quote, economy, messages, config.showOptionalCosts());

        if (!quote.affordable()) {
            final Component reason = CostFormatter.formatMissingRequirements(player, quote, economy, messages);
            return WarpEvaluation.denied(costDisplay, reason);
        }

        return WarpEvaluation.permitted(costDisplay);
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

        return bundle.charge(player, quote, economy);
    }

    private EvaluationContext resolveContext(
            final Player player,
            final @Nullable Aetheryte source,
            final Aetheryte destination
    ) {
        final PluginConfig config = configSupplier.get();
        final World targetWorld = worldResolver.apply(destination.worldId());
        final World sourceWorld = source != null ? worldResolver.apply(source.worldId()) : player.getWorld();

        if (targetWorld == null) {
            return new EvaluationContext(0, 0, 0, 0, 0, 0, 0, List.of(), messages.get(player, MessageKey.ERROR_NOT_FOUND));
        }

        final boolean sameWorld = sourceWorld != null && sourceWorld.getUID().equals(targetWorld.getUID());
        final double blockDist;
        final double horizDist;
        final double chunkDist;
        final double dy;
        final double crossDim;
        final double hops;
        final double dimCostAmt;
        final List<CostEntry> extraEntries = new ArrayList<>();

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
                return new EvaluationContext(0, 0, 0, 0, 0, 0, 0, List.of(), messages.get(player, MessageKey.ERROR_NO_ROUTE));
            }
            final Location srcLoc = source != null
                    ? new Location(sourceWorld, source.blockX(), source.blockY(), source.blockZ())
                    : fallbackSrc;
            final DimensionRouter router = routerSupplier.get();
            final Optional<RoutePlan> plan = router.findRoute(sourceWorld, targetWorld, srcLoc);
            if (plan.isEmpty()) {
                return new EvaluationContext(0, 0, 0, 0, 0, 0, 0, List.of(), messages.get(player, MessageKey.ERROR_NO_ROUTE));
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

        // Distance limits (#26)
        final DistanceSection effectiveDistanceLimits = resolveDistanceLimits(source, config);
        final double minDist = effectiveDistanceLimits.min();
        final double maxDist = effectiveDistanceLimits.max();

        if (minDist > 0.0 && blockDist < minDist) {
            return new EvaluationContext(blockDist, horizDist, chunkDist, dy, crossDim, hops, dimCostAmt, List.of(),
                    messages.get(player, MessageKey.ERROR_TOO_CLOSE));
        }
        if (maxDist >= 0.0 && blockDist > maxDist) {
            return new EvaluationContext(blockDist, horizDist, chunkDist, dy, crossDim, hops, dimCostAmt, List.of(),
                    messages.get(player, MessageKey.ERROR_TOO_FAR));
        }

        // Resolve base cost entries
        final List<CostEntry> baseEntries = resolveBaseCostEntries(source, config);
        final List<CostEntry> allEntries = new ArrayList<>(baseEntries);
        allEntries.addAll(extraEntries);

        return new EvaluationContext(blockDist, horizDist, chunkDist, dy, crossDim, hops, dimCostAmt, allEntries, null);
    }

    private DistanceSection resolveDistanceLimits(final @Nullable Aetheryte source, final PluginConfig config) {
        if (source != null) {
            final AetheryteVariation var = variationRegistry.getOrDefault(source.variation());
            final DistanceSection override = var.warp().distance();
            final double min = override.min() >= 0.0 ? override.min() : config.warpDistanceMin();
            final double max = override.max() >= 0.0 ? override.max() : config.warpDistanceMax();
            return new DistanceSection(min, max);
        }
        return new DistanceSection(config.warpDistanceMin(), config.warpDistanceMax());
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
        final Map<String, Double> map = new HashMap<>();
        map.put("block_distance", ctx.blockDistance());
        map.put("horizontal_distance", ctx.horizontalDistance());
        map.put("chunk_distance", ctx.chunkDistance());
        map.put("dy", ctx.dy());
        map.put("cross_dimension", ctx.crossDimension());
        map.put("hops", ctx.hops());
        map.put("dimension_cost", ctx.dimensionCostAmount());
        return map;
    }
}
