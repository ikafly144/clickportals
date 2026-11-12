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

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.sabafly.clickportals.economy.EconomyProvider;
import net.sabafly.clickportals.message.MessageKey;
import net.sabafly.clickportals.message.Messages;
import org.bukkit.entity.Player;

/**
 * Formats {@link CostQuote} into localized Adventure {@link Component}s (#7).
 */
public final class CostFormatter {

    private CostFormatter() {
    }

    /**
     * Formats the quote into a readable label (e.g. {@code "100.00 G + 3 Lv"}).
     * Non-mandatory costs can be hidden according to {@code showOptionalCosts}.
     *
     * @param player player to format for (for localization)
     * @param quote assessed cost quote
     * @param economy active economy provider
     * @param messages active messages service
     * @param showOptionalCosts whether non-required costs are displayed
     * @return formatted {@link Component}
     */
    public static Component format(
            final Player player,
            final CostQuote quote,
            final EconomyProvider economy,
            final Messages messages,
            final boolean showOptionalCosts
    ) {
        if (quote.isFree()) {
            return messages.freeCostLabel(player);
        }

        final List<Component> parts = new ArrayList<>();
        for (final CostItemQuote item : quote.items()) {
            if (item.disabled() || item.amount() <= 0.0) {
                continue;
            }
            if (!showOptionalCosts && !item.required()) {
                continue;
            }
            parts.add(formatSingleItem(player, item, economy, messages));
        }

        if (parts.isEmpty()) {
            return messages.freeCostLabel(player);
        }

        final Component separator = messages.get(player, MessageKey.COST_SEPARATOR);
        Component result = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            result = result.append(separator).append(parts.get(i));
        }
        return result;
    }

    /**
     * Formats the specific insufficient cost reasons into a descriptive error {@link Component} (#7).
     */
    public static Component formatMissingRequirements(
            final Player player,
            final CostQuote quote,
            final EconomyProvider economy,
            final Messages messages
    ) {
        final List<CostItemQuote> missing = quote.missingRequirements();
        if (missing.isEmpty()) {
            return messages.get(player, MessageKey.ERROR_INSUFFICIENT_COST);
        }

        final List<Component> reasons = new ArrayList<>(missing.size());
        for (final CostItemQuote item : missing) {
            final String formattedAmount = formatAmountString(item, economy);
            final MessageKey key = switch (item.type()) {
                case VAULT -> MessageKey.COST_INSUFFICIENT_VAULT;
                case EXP_POINTS -> MessageKey.COST_INSUFFICIENT_EXP_POINTS;
                case EXP_LEVELS -> MessageKey.COST_INSUFFICIENT_EXP_LEVELS;
                case FOOD -> MessageKey.COST_INSUFFICIENT_FOOD;
            };
            reasons.add(messages.get(player, key, Placeholder.unparsed("amount", formattedAmount)));
        }

        final Component separator = messages.get(player, MessageKey.COST_SEPARATOR);
        Component result = reasons.get(0);
        for (int i = 1; i < reasons.size(); i++) {
            result = result.append(separator).append(reasons.get(i));
        }
        return result;
    }

    private static Component formatSingleItem(
            final Player player,
            final CostItemQuote item,
            final EconomyProvider economy,
            final Messages messages
    ) {
        final String formattedAmount = formatAmountString(item, economy);
        final MessageKey key = switch (item.type()) {
            case VAULT -> MessageKey.COST_VAULT;
            case EXP_POINTS -> MessageKey.COST_EXP_POINTS;
            case EXP_LEVELS -> MessageKey.COST_EXP_LEVELS;
            case FOOD -> MessageKey.COST_FOOD;
        };
        return messages.get(player, key, Placeholder.unparsed("amount", formattedAmount));
    }

    private static String formatAmountString(final CostItemQuote item, final EconomyProvider economy) {
        if (item.type() == CostType.VAULT && economy.isAvailable()) {
            return economy.format(item.amount());
        }
        if (item.type() == CostType.VAULT) {
            return String.format(java.util.Locale.ROOT, "%.2f", item.amount());
        }
        return Long.toString(Math.round(item.amount()));
    }
}
