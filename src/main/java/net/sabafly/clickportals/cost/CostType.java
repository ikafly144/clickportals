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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Supported cost types for Aetheryte warping and placement (#7, #15, #16).
 */
public enum CostType {

    /**
     * Server currency managed by Vault (#16).
     */
    VAULT("vault", "balance"),

    /**
     * Total experience points (#7).
     */
    EXP_POINTS("exp-points", "exp_points"),

    /**
     * Experience levels (#7).
     */
    EXP_LEVELS("exp-levels", "exp_level"),

    /**
     * Player hunger / food level (#7).
     */
    FOOD("food", "food");

    private final String id;
    private final String resourceVariable;

    CostType(final String id, final String resourceVariable) {
        this.id = id;
        this.resourceVariable = resourceVariable;
    }

    public String id() {
        return id;
    }

    public String resourceVariable() {
        return resourceVariable;
    }

    /**
     * Checks whether this cost type represents a discrete integer Minecraft quantity (#7).
     */
    public boolean isInteger() {
        return this != VAULT;
    }

    /**
     * Normalizes the assessed amount according to this cost type's precision rules (#7).
     * Integer quantities are rounded to whole numbers; Vault currency is rounded to 2 decimal places.
     */
    public double normalize(final double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            return amount;
        }
        return isInteger()
                ? (double) Math.round(amount)
                : BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Converts a normalized amount to a {@link BigDecimal} for exact decimal aggregation (#7).
     */
    public BigDecimal toBigDecimal(final double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            return BigDecimal.ZERO;
        }
        return isInteger()
                ? BigDecimal.valueOf(Math.round(amount))
                : BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Resolves a {@link CostType} from its config string representation, returning {@code null} on unrecognized types.
     * Null or blank input defaults to {@link #VAULT}.
     */
    public static @Nullable CostType fromString(final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return VAULT;
        }
        final String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "vault", "money", "economy", "balance" -> VAULT;
            case "exp-points", "exppoints", "exp_points", "experience", "xp" -> EXP_POINTS;
            case "exp-levels", "explevels", "exp_levels", "levels", "level", "lv" -> EXP_LEVELS;
            case "food", "hunger" -> FOOD;
            default -> null;
        };
    }
}
