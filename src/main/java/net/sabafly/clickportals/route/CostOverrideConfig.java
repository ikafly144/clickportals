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

import java.util.List;
import net.sabafly.clickportals.cost.CostEntry;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/**
 * Route-specific cost override (#9).
 */
@ConfigSerializable
public record CostOverrideConfig(
        @Setting("amount")
        @Comment("Amount formula for override (e.g. '$default + 300' or fixed number)")
        String amount,

        @Setting("cost")
        @Comment("Custom cost entries to override default types (optional; if empty, default dimension cost types are used)")
        @Nullable List<CostEntry> cost
) {

    public CostOverrideConfig {
        if (amount == null || amount.isBlank()) {
            amount = "$default";
        }
    }

    public static CostOverrideConfig of(final String amount) {
        return new CostOverrideConfig(amount, null);
    }
}
