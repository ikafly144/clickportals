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
package net.sabafly.clickportals.cost;

import net.sabafly.clickportals.cost.expr.Expression;
import net.sabafly.clickportals.cost.expr.ExpressionException;
import net.sabafly.clickportals.cost.expr.ExpressionParser;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/**
 * Single cost specification configured in YAML (#7, #14, #15).
 */
@ConfigSerializable
public record CostEntry(
        @Setting("type")
        @Comment("Cost type: vault, exp-points, exp-levels, or food")
        String rawType,

        @Setting("amount")
        @Comment("Amount expression (e.g. '10', '$block_distance * 0.5 + 5', or '10%')")
        String amount,

        @Setting("required")
        @Comment("Whether this cost is mandatory (false = consume as much as possible and succeed)")
        boolean required
) {

    public CostEntry {
        if (rawType == null || rawType.isBlank()) {
            rawType = "vault";
        }
        if (amount == null) {
            amount = "0";
        }
    }

    /**
     * Creates a {@link CostEntry} with default {@code required = true}.
     */
    public CostEntry(final String rawType, final String amount) {
        this(rawType, amount, true);
    }

    /**
     * Resolves the {@link CostType} of this entry.
     */
    public CostType type() {
        return CostType.fromString(rawType);
    }

    /**
     * Compiles the amount expression against this cost type's default percentage variable (#7).
     *
     * @return compiled {@link Expression}, falling back to {@link Expression#ZERO} on syntax errors
     */
    public Expression compile() {
        try {
            return ExpressionParser.parse(amount, type().resourceVariable());
        } catch (final ExpressionException ignored) {
            return Expression.ZERO;
        }
    }

    /**
     * Convenience factory for tests and programmatic usage.
     */
    public static CostEntry of(final CostType type, final String amount, final boolean required) {
        return new CostEntry(type.id(), amount, required);
    }

    /**
     * Convenience factory for mandatory costs.
     */
    public static CostEntry of(final CostType type, final String amount) {
        return new CostEntry(type.id(), amount, true);
    }
}
