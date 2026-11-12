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

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Assessed quote for an individual cost requirement (#7).
 *
 * @param type cost type
 * @param amount calculated charge amount
 * @param required whether failure to meet this requirement blocks the operation
 * @param affordable whether the player currently holds enough resources
 * @param currentResource the player's resource amount at the time of quotation
 * @param disabled whether this cost is disabled (e.g. Vault currency without Vault installed)
 * @param transactionId unique identifier for idempotent pending refund tracking, or {@code null}
 */
public record CostItemQuote(
        @Nullable CostType type,
        double amount,
        boolean required,
        boolean affordable,
        double currentResource,
        boolean disabled,
        @Nullable UUID transactionId
) {

    /**
     * Backwards-compatible constructor without a transaction ID.
     */
    public CostItemQuote(
            final @Nullable CostType type,
            final double amount,
            final boolean required,
            final boolean affordable,
            final double currentResource,
            final boolean disabled
    ) {
        this(type, amount, required, affordable, currentResource, disabled, null);
    }

    /**
     * Returns a copy of this quote with the specified transaction ID.
     */
    public CostItemQuote withTransactionId(final UUID id) {
        return new CostItemQuote(type, amount, required, affordable, currentResource, disabled, id);
    }

    /**
     * Effective amount that will be deducted, capped by current resource if non-required.
     */
    public double effectiveDeduction() {
        if (disabled || type == null || amount <= 0.0 || !Double.isFinite(amount)) {
            return 0.0;
        }
        if (!required) {
            return Math.min(amount, Math.max(0.0, currentResource));
        }
        return amount;
    }
}
