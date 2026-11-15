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
import java.util.List;
import java.util.Objects;
import net.sabafly.clickportals.aetheryte.AetheryteVisibility;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.expr.ExpressionException;
import net.sabafly.clickportals.cost.expr.ExpressionParser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

/**
 * Per-visibility cost override lists for placement and warping (#25).
 *
 * <p>Each field may be:
 * <ul>
 *     <li>{@code null}: fall back to / inherit from the base cost (or global override),</li>
 *     <li>{@code []}: explicitly free for that visibility tier, or</li>
 *     <li>a non-empty list of {@link CostEntry}: explicit cost entries used instead of the base cost.</li>
 * </ul>
 */
public record VisibilityCostSection(
        @Nullable List<CostEntry> publicCost,
        @Nullable List<CostEntry> inviteOnlyCost,
        @Nullable List<CostEntry> ownerOnlyCost
) {

    /**
     * Sentinel string emitted before YAML saving so explicit {@code null} entries are written as {@code null}
     * instead of being omitted by Configurate.
     */
    public static final String NULL_SENTINEL = "__CLICKPORTALS_NULL__";

    public VisibilityCostSection {
        publicCost = publicCost == null ? null : List.copyOf(publicCost);
        inviteOnlyCost = inviteOnlyCost == null ? null : List.copyOf(inviteOnlyCost);
        ownerOnlyCost = ownerOnlyCost == null ? null : List.copyOf(ownerOnlyCost);
    }

    /**
     * Global defaults (all tiers {@code null} -> fall back to base cost).
     */
    public static VisibilityCostSection defaults() {
        return new VisibilityCostSection(null, null, null);
    }

    /**
     * Variation override defaults (all tiers {@code null} -> inherit base / global).
     */
    public static VisibilityCostSection inherit() {
        return new VisibilityCostSection(null, null, null);
    }

    /**
     * Returns the configured override list for {@code visibility}, or {@code null} if inheriting/falling back.
     */
    public @Nullable List<CostEntry> costFor(final AetheryteVisibility visibility) {
        Objects.requireNonNull(visibility, "visibility");
        return switch (visibility) {
            case PUBLIC -> publicCost;
            case INVITE_ONLY -> inviteOnlyCost;
            case OWNER_ONLY -> ownerOnlyCost;
        };
    }

    /**
     * Resolves per-visibility overrides against {@code fallback}, keeping non-null overrides in this instance.
     */
    public VisibilityCostSection resolve(final VisibilityCostSection fallback) {
        final List<CostEntry> pub = publicCost != null ? publicCost : fallback.publicCost();
        final List<CostEntry> inv = inviteOnlyCost != null ? inviteOnlyCost : fallback.inviteOnlyCost();
        final List<CostEntry> own = ownerOnlyCost != null ? ownerOnlyCost : fallback.ownerOnlyCost();
        return new VisibilityCostSection(pub, inv, own);
    }

    /**
     * Returns {@code true} if any visibility tier specifies an explicit cost list (including {@code []}).
     */
    public boolean hasCustomOverrides() {
        return publicCost != null || inviteOnlyCost != null || ownerOnlyCost != null;
    }

    /**
     * Validates all non-null cost entry lists, logging warnings for unknown types or invalid expressions.
     */
    public VisibilityCostSection validated(final String prefix, final Logger logger) {
        validateEntries(publicCost, prefix + ".public", logger);
        validateEntries(inviteOnlyCost, prefix + ".invite-only", logger);
        validateEntries(ownerOnlyCost, prefix + ".owner-only", logger);
        return this;
    }

    private static void validateEntries(
            final @Nullable List<CostEntry> entries,
            final String path,
            final Logger logger
    ) {
        if (entries == null) {
            return;
        }
        for (final CostEntry entry : entries) {
            if (entry.type() == null) {
                logger.warn("Unknown cost type '{}' in {}; this cost will fail affordability checks.", entry.rawType(), path);
                continue;
            }
            try {
                ExpressionParser.parse(entry.amount(), entry.type().resourceVariable());
            } catch (final ExpressionException e) {
                logger.warn("Invalid cost expression '{}' in {}: {}", entry.amount(), path, e.getMessage());
            }
        }
    }

    /**
     * Configurate serializer preserving {@code null} vs {@code []} vs populated {@link CostEntry} lists.
     */
    public static final class Serializer implements TypeSerializer<VisibilityCostSection> {

        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public VisibilityCostSection deserialize(final Type type, final ConfigurationNode node)
                throws SerializationException {
            if (node == null || node.virtual() || node.raw() == null) {
                return inherit();
            }
            return new VisibilityCostSection(
                    readCostList(node.node("public")),
                    readCostList(node.node("invite-only")),
                    readCostList(node.node("owner-only"))
            );
        }

        private static @Nullable List<CostEntry> readCostList(final ConfigurationNode child)
                throws SerializationException {
            if (child == null || child.virtual() || child.raw() == null || NULL_SENTINEL.equals(child.raw())) {
                return null;
            }
            final List<CostEntry> list = child.getList(CostEntry.class, List.of());
            return List.copyOf(list);
        }

        @Override
        public void serialize(
                final Type type,
                final @Nullable VisibilityCostSection obj,
                final ConfigurationNode node
        ) throws SerializationException {
            final VisibilityCostSection target = obj != null ? obj : defaults();
            node.raw(java.util.Collections.emptyMap());
            writeCostList(node.node("public"), target.publicCost());
            writeCostList(node.node("invite-only"), target.inviteOnlyCost());
            writeCostList(node.node("owner-only"), target.ownerOnlyCost());
        }

        private static void writeCostList(final ConfigurationNode child, final @Nullable List<CostEntry> list)
                throws SerializationException {
            if (list == null) {
                child.raw(null);
            } else {
                child.setList(CostEntry.class, list);
            }
        }
    }

    /**
     * Populates {@link #NULL_SENTINEL} markers for {@code null} visibility tiers right before dumping to YAML
     * so explicit {@code null} overrides remain visible on disk without skewing schema diff comparisons.
     */
    public static void applyNullSentinels(
            final ConfigurationNode node,
            final @Nullable VisibilityCostSection section
    ) throws SerializationException {
        final VisibilityCostSection target = section != null ? section : defaults();
        if (target.publicCost() == null) {
            node.node("public").set(NULL_SENTINEL);
        }
        if (target.inviteOnlyCost() == null) {
            node.node("invite-only").set(NULL_SENTINEL);
        }
        if (target.ownerOnlyCost() == null) {
            node.node("owner-only").set(NULL_SENTINEL);
        }
    }
}
