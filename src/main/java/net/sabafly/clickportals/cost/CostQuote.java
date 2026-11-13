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

import java.util.List;

/**
 * Combined quote assessed for an operation (#7, #14, #15).
 *
 * @param items individual assessed cost quotes
 * @param affordable whether all mandatory requirements are satisfied
 */
public record CostQuote(
        List<CostItemQuote> items,
        boolean affordable
) {

    public CostQuote(final List<CostItemQuote> items) {
        this(List.copyOf(items), computeAffordability(items));
    }

    private static boolean computeAffordability(final List<CostItemQuote> items) {
        for (final CostItemQuote item : items) {
            if (item.disabled()) {
                continue;
            }
            if (item.required() && !item.affordable()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether this quote requires zero resources across all active components.
     */
    public boolean isFree() {
        for (final CostItemQuote item : items) {
            if (!item.disabled() && item.amount() > 0.0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the list of mandatory items that the player cannot afford.
     */
    public List<CostItemQuote> missingRequirements() {
        return items.stream()
                .filter(i -> !i.disabled() && i.required() && !i.affordable())
                .toList();
    }

    /**
     * Empty free quote.
     */
    public static final CostQuote FREE = new CostQuote(List.of(), true);
}
