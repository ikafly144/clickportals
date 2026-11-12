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
package net.sabafly.clickportals.route;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.expr.Expression;
import net.sabafly.clickportals.cost.expr.ExpressionParser;
import org.bukkit.Location;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * Route calculation and origin resolution engine for cross-dimension warps (#9).
 */
public final class DimensionRouter {

    private final DimensionRoutesConfig config;
    private final Map<String, List<Edge>> adjacency = new HashMap<>();

    private record Edge(
            String targetWorld,
            @Nullable OriginConfig origin,
            @Nullable CostOverrideConfig costOverride
    ) {
    }

    public DimensionRouter(final DimensionRoutesConfig config) {
        this.config = config;
        buildGraph();
    }

    private void buildGraph() {
        for (final RouteEntryConfig route : config.routes()) {
            final List<String> between = route.between();
            if (between.size() < 2) {
                continue;
            }
            final String a = between.get(0);
            final String b = between.get(1);

            final OriginConfig originA = route.origin().get(a);
            final OriginConfig originB = route.origin().get(b);
            final CostOverrideConfig overrideA = route.costOverride().get(a);
            final CostOverrideConfig overrideB = route.costOverride().get(b);

            adjacency.computeIfAbsent(a, k -> new ArrayList<>()).add(new Edge(b, originB, overrideB));
            adjacency.computeIfAbsent(b, k -> new ArrayList<>()).add(new Edge(a, originA, overrideA));
        }
    }

    /**
     * Finds a route from {@code fromWorld} to {@code toWorld} and builds a {@link RoutePlan} (#9).
     *
     * @param fromWorld origin world
     * @param toWorld target destination world
     * @param sourceLocation exact source location in {@code fromWorld}
     * @return {@link Optional} containing the route plan, or empty if no route connects the worlds
     */
    public Optional<RoutePlan> findRoute(
            final World fromWorld,
            final World toWorld,
            final Location sourceLocation
    ) {
        final String fromName = fromWorld.getName();
        final String toName = toWorld.getName();
        if (fromName.equals(toName)) {
            return Optional.empty();
        }

        final List<String> path = findShortestPath(fromName, toName);
        if (path.isEmpty()) {
            return Optional.empty();
        }

        // Trace along edges to compute origins and dimension cost
        double totalCostAmount = 0.0;
        final List<CostEntry> generatedEntries = new ArrayList<>();

        double currX = sourceLocation.getX();
        double currY = sourceLocation.getY();
        double currZ = sourceLocation.getZ();

        for (int i = 0; i < path.size() - 1; i++) {
            final String u = path.get(i);
            final String v = path.get(i + 1);
            final Edge edge = getEdge(u, v);

            // Origin transformation
            final OriginConfig origin = edge != null && edge.origin() != null
                    ? edge.origin()
                    : OriginConfig.scale(1.0);
            final Location nextLoc = origin.resolve(toWorld, currX, currY, currZ);
            currX = nextLoc.getX();
            currY = nextLoc.getY();
            currZ = nextLoc.getZ();

            // Edge cost computation
            final double uAmt = config.amounts().getOrDefault(u, 0.0);
            final double vAmt = config.amounts().getOrDefault(v, 0.0);
            final double defaultEdgeAmount = uAmt + vAmt;

            if (edge != null && edge.costOverride() != null) {
                final CostOverrideConfig override = edge.costOverride();
                final Expression expr = ExpressionParser.parse(override.amount());
                final double edgeAmt = Math.max(0.0, expr.evaluate(Map.of("default", defaultEdgeAmount)));
                totalCostAmount += edgeAmt;

                if (override.cost() != null && !override.cost().isEmpty()) {
                    for (final CostEntry ce : override.cost()) {
                        generatedEntries.add(scaleCostEntry(ce, edgeAmt));
                    }
                } else {
                    for (final CostEntry ce : config.defaultCost()) {
                        generatedEntries.add(scaleCostEntry(ce, edgeAmt));
                    }
                }
            } else {
                totalCostAmount += defaultEdgeAmount;
                for (final CostEntry ce : config.defaultCost()) {
                    generatedEntries.add(scaleCostEntry(ce, defaultEdgeAmount));
                }
            }
        }

        final Location targetOrigin = new Location(toWorld, currX, currY, currZ);
        return Optional.of(new RoutePlan(path, totalCostAmount, Collections.unmodifiableList(generatedEntries), targetOrigin));
    }

    private @Nullable Edge getEdge(final String u, final String v) {
        final List<Edge> edges = adjacency.get(u);
        if (edges == null) {
            return null;
        }
        for (final Edge e : edges) {
            if (e.targetWorld().equals(v)) {
                return e;
            }
        }
        return null;
    }

    private List<String> findShortestPath(final String start, final String target) {
        final Queue<List<String>> queue = new ArrayDeque<>();
        final Set<String> visited = new HashSet<>();

        queue.add(List.of(start));
        visited.add(start);

        while (!queue.isEmpty()) {
            final List<String> path = queue.poll();
            final String current = path.get(path.size() - 1);
            if (current.equals(target)) {
                return path;
            }

            final List<Edge> edges = adjacency.get(current);
            if (edges != null) {
                for (final Edge edge : edges) {
                    if (visited.add(edge.targetWorld())) {
                        final List<String> nextPath = new ArrayList<>(path);
                        nextPath.add(edge.targetWorld());
                        queue.add(nextPath);
                    }
                }
            }
        }

        return List.of();
    }

    private static CostEntry scaleCostEntry(final CostEntry template, final double amount) {
        return new CostEntry(template.rawType(), Double.toString(amount), template.required());
    }
}
