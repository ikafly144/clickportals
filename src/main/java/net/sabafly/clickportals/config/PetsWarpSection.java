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
package net.sabafly.clickportals.config;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.expr.ExpressionException;
import net.sabafly.clickportals.cost.expr.ExpressionParser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

/**
 * Leashed entity (pet) warping configuration section supporting global defaults and per-variation overrides (#22).
 */
public record PetsWarpSection(
        @Nullable Boolean enabled,
        @Nullable Integer maxEntities,
        @Nullable List<CostEntry> cost
) {

    private static final String COMMENT_SECTION = "Leashed entity warping settings (#22)";
    private static final String COMMENT_ENABLED =
            "Whether entities leashed to the warping player teleport along with them (in variations: null to inherit global)";
    private static final String COMMENT_MAX_ENTITIES =
            "Maximum leashed entities allowed to warp together (-1 for unlimited, 0 for none; in variations: -1 or null to inherit global)";
    private static final String COMMENT_COST =
            "Additional warp cost specifications when warping with leashed entities ($pet_count available; in variations: null to inherit global, [] for free)";

    public PetsWarpSection {
        cost = cost == null ? null : List.copyOf(cost);
    }

    /**
     * Concrete, non-null resolved pet warping settings after merging variation overrides with global defaults (#22).
     */
    public record Resolved(
            boolean enabled,
            int maxEntities,
            List<CostEntry> cost
    ) {
        public Resolved {
            cost = cost == null ? List.of() : List.copyOf(cost);
        }
    }

    /**
     * Returns the global default pet warping configuration.
     */
    public static PetsWarpSection defaults() {
        return new PetsWarpSection(true, 5, List.of());
    }

    /**
     * Returns a variation override section that inherits every field from global configuration.
     */
    public static PetsWarpSection inherit() {
        return new PetsWarpSection(null, null, null);
    }

    public boolean enabledOrDefault(final boolean fallback) {
        return enabled != null ? enabled : fallback;
    }

    public int maxEntitiesOrDefault(final int fallback) {
        return maxEntities != null ? maxEntities : fallback;
    }

    public List<CostEntry> costOrDefault(final List<CostEntry> fallback) {
        return cost != null ? cost : fallback;
    }

    /**
     * Validates this section for global {@code config.yml} use, filling null fields with safe defaults.
     */
    public PetsWarpSection validatedGlobal(final Logger logger) {
        final boolean validEnabled = enabled != null ? enabled : true;
        final int rawMax = maxEntities != null ? maxEntities : 5;
        final int validMax;
        if (rawMax < -1) {
            logger.warn("Invalid warp.pets.max-entities {} (must be >= -1); falling back to 5", rawMax);
            validMax = 5;
        } else {
            validMax = rawMax;
        }
        final List<CostEntry> validCost = cost != null ? cost : List.of();
        validateCostEntries(validCost, "warp.pets.cost", logger);
        return new PetsWarpSection(validEnabled, validMax, validCost);
    }

    /**
     * Validates this section for variation override use, preserving {@code null} inheritance semantics.
     */
    public PetsWarpSection validatedOverride(final String variationId, final Logger logger) {
        final Integer validMax;
        if (maxEntities != null && maxEntities < -1) {
            logger.warn(
                    "Invalid warp.pets.max-entities {} in variation '{}' (must be >= -1); falling back to -1 (inherit)",
                    maxEntities,
                    variationId
            );
            validMax = -1;
        } else {
            validMax = maxEntities;
        }
        if (cost != null) {
            validateCostEntries(cost, "variations." + variationId + ".warp.pets.cost", logger);
        }
        return new PetsWarpSection(enabled, validMax, cost);
    }

    private static void validateCostEntries(
            final List<CostEntry> entries,
            final String path,
            final Logger logger
    ) {
        for (final CostEntry entry : entries) {
            if (entry.type() == null) {
                logger.warn("Unknown cost type '{}' in {}", entry.rawType(), path);
            } else {
                try {
                    ExpressionParser.parse(entry.amount(), entry.type().resourceVariable());
                } catch (final ExpressionException e) {
                    logger.warn("Invalid cost amount expression '{}' in {}: {}", entry.amount(), path, e.getMessage());
                }
            }
        }
    }

    /**
     * Resolves this (potentially inheriting) variation override against {@code global} defaults.
     */
    public Resolved resolve(final PetsWarpSection global) {
        return resolve(
                global.enabledOrDefault(true),
                global.maxEntitiesOrDefault(5),
                global.costOrDefault(List.of())
        );
    }

    /**
     * Resolves this global section against built-in defaults.
     */
    public Resolved resolveGlobal() {
        return resolve(true, 5, List.of());
    }

    /**
     * Resolves this section against explicit global fallback values.
     */
    public Resolved resolve(
            final boolean globalEnabled,
            final int globalMaxEntities,
            final List<CostEntry> globalCost
    ) {
        final boolean resolvedEnabled = enabled != null ? enabled : globalEnabled;
        final int resolvedMax = (maxEntities != null && maxEntities >= 0) ? maxEntities : globalMaxEntities;
        final List<CostEntry> resolvedCost = cost != null ? cost : globalCost;
        return new Resolved(resolvedEnabled, resolvedMax, resolvedCost);
    }

    /**
     * Replaces {@code null} inheritance fields with {@link VisibilityCostSection#NULL_SENTINEL} so dumped
     * YAML variation files explicitly write {@code null} with comments (#22).
     */
    public static void applyNullSentinels(
            final CommentedConfigurationNode node,
            final PetsWarpSection section
    ) throws SerializationException {
        setCommentIfBlank(node, COMMENT_SECTION);
        if (section.enabled() == null) {
            final CommentedConfigurationNode enabledNode = node.node("enabled");
            enabledNode.set(VisibilityCostSection.NULL_SENTINEL);
            setCommentIfBlank(enabledNode, COMMENT_ENABLED);
        }
        if (section.maxEntities() == null) {
            final CommentedConfigurationNode maxNode = node.node("max-entities");
            maxNode.set(VisibilityCostSection.NULL_SENTINEL);
            setCommentIfBlank(maxNode, COMMENT_MAX_ENTITIES);
        }
        if (section.cost() == null) {
            final CommentedConfigurationNode costNode = node.node("cost");
            costNode.set(VisibilityCostSection.NULL_SENTINEL);
            setCommentIfBlank(costNode, COMMENT_COST);
        }
    }

    private static void setCommentIfBlank(final ConfigurationNode node, final String comment) {
        if (node instanceof CommentedConfigurationNode commented) {
            final String existing = commented.comment();
            if (existing == null || existing.isBlank()) {
                commented.comment(comment);
            }
        }
    }

    /**
     * Custom {@link TypeSerializer} preserving explicit {@code null} vs {@code []} in {@code cost},
     * non-virtual empty map structure for schema-sync idempotency, and inline comments (#22).
     */
    public static final class Serializer implements TypeSerializer<PetsWarpSection> {
        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public PetsWarpSection deserialize(final Type type, final ConfigurationNode node)
                throws SerializationException {
            if (node.virtual() || node.isNull()) {
                return inherit();
            }
            final ConfigurationNode enabledNode = node.node("enabled");
            final Boolean enabled = (enabledNode.virtual() || enabledNode.isNull() || enabledNode.raw() == null)
                    ? null
                    : enabledNode.getBoolean();

            final ConfigurationNode maxNode = node.node("max-entities");
            final Integer maxEntities = (maxNode.virtual() || maxNode.isNull() || maxNode.raw() == null)
                    ? null
                    : maxNode.get(Integer.class);

            final ConfigurationNode costNode = node.node("cost");
            final List<CostEntry> cost;
            if (costNode.virtual() || costNode.isNull() || costNode.raw() == null) {
                cost = null;
            } else if (costNode.isList()) {
                cost = costNode.getList(CostEntry.class);
            } else {
                cost = null;
            }
            return new PetsWarpSection(enabled, maxEntities, cost);
        }

        @Override
        public void serialize(
                final Type type,
                final @Nullable PetsWarpSection obj,
                final ConfigurationNode node
        ) throws SerializationException {
            final PetsWarpSection target = obj != null ? obj : inherit();
            node.raw(Collections.emptyMap());
            setCommentIfBlank(node, COMMENT_SECTION);

            final ConfigurationNode enabledNode = node.node("enabled");
            if (target.enabled() == null) {
                enabledNode.raw(null);
            } else {
                enabledNode.set(target.enabled());
                setCommentIfBlank(enabledNode, COMMENT_ENABLED);
            }

            final ConfigurationNode maxNode = node.node("max-entities");
            if (target.maxEntities() == null) {
                maxNode.raw(null);
            } else {
                maxNode.set(target.maxEntities());
                setCommentIfBlank(maxNode, COMMENT_MAX_ENTITIES);
            }

            final ConfigurationNode costNode = node.node("cost");
            if (target.cost() == null) {
                costNode.raw(null);
            } else {
                costNode.setList(CostEntry.class, target.cost());
                setCommentIfBlank(costNode, COMMENT_COST);
            }
        }
    }
}
