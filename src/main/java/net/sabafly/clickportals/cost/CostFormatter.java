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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
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
     * Items of the same {@link CostType} are aggregated together into a single total.
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
        return format(messages.locale(player), quote, economy, messages, showOptionalCosts);
    }

    /**
     * Formats the quote into a readable label using a pre-resolved {@link Locale} (#7, #20).
     */
    public static Component format(
            final Locale locale,
            final CostQuote quote,
            final EconomyProvider economy,
            final Messages messages,
            final boolean showOptionalCosts
    ) {
        if (quote.isFree()) {
            return messages.freeCostLabel(locale);
        }

        final List<CostItemQuote> items = quote.items();
        if (items.size() == 1) {
            final CostItemQuote item = items.get(0);
            if (item.disabled() || item.amount() <= 0.0 || (!showOptionalCosts && !item.required())) {
                return messages.freeCostLabel(locale);
            }
            if (item.type() == null || !Double.isFinite(item.amount())) {
                return messages.get(locale, MessageKey.ERROR_WARP_REQUIREMENTS_NOT_MET);
            }
            return formatSingleItem(locale, item.type(), item.amount(), economy, messages);
        }

        final Map<CostType, BigDecimal> aggregated = new LinkedHashMap<>();
        boolean hasInvalid = false;
        for (final CostItemQuote item : quote.items()) {
            if (item.disabled() || item.amount() <= 0.0) {
                continue;
            }
            if (!showOptionalCosts && !item.required()) {
                continue;
            }
            if (item.type() == null || !Double.isFinite(item.amount())) {
                hasInvalid = true;
                continue;
            }
            aggregated.merge(item.type(), item.type().toBigDecimal(item.amount()), BigDecimal::add);
        }

        if (aggregated.isEmpty()) {
            return hasInvalid
                    ? messages.get(locale, MessageKey.ERROR_WARP_REQUIREMENTS_NOT_MET)
                    : messages.freeCostLabel(locale);
        }

        final List<Component> parts = new ArrayList<>();
        if (hasInvalid) {
            parts.add(messages.get(locale, MessageKey.ERROR_WARP_REQUIREMENTS_NOT_MET));
        }
        for (final Map.Entry<CostType, BigDecimal> entry : aggregated.entrySet()) {
            final CostType type = entry.getKey();
            final double amount = entry.getValue().doubleValue();
            parts.add(formatSingleItem(locale, type, amount, economy, messages));
        }

        final Component separator = messages.get(locale, MessageKey.COST_SEPARATOR);
        Component result = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            result = result.append(separator).append(parts.get(i));
        }
        return result;
    }

    /**
     * Formats the specific insufficient cost reasons into a descriptive error {@link Component} (#7).
     * Items of the same {@link CostType} are aggregated together.
     */
    public static Component formatMissingRequirements(
            final Player player,
            final CostQuote quote,
            final EconomyProvider economy,
            final Messages messages
    ) {
        return formatMissingRequirements(messages.locale(player), quote, economy, messages);
    }

    /**
     * Formats the specific insufficient cost reasons using a pre-resolved {@link Locale} (#7, #20).
     */
    public static Component formatMissingRequirements(
            final Locale locale,
            final CostQuote quote,
            final EconomyProvider economy,
            final Messages messages
    ) {
        final List<CostItemQuote> missing = quote.missingRequirements();
        if (missing.isEmpty()) {
            return messages.get(locale, MessageKey.ERROR_INSUFFICIENT_COST);
        }

        final Map<CostType, BigDecimal> aggregatedMissing = new LinkedHashMap<>();
        boolean hasInvalid = false;
        for (final CostItemQuote item : missing) {
            if (item.type() == null || !Double.isFinite(item.amount())) {
                hasInvalid = true;
                continue;
            }
            aggregatedMissing.merge(item.type(), item.type().toBigDecimal(item.amount()), BigDecimal::add);
        }

        final List<Component> reasons = new ArrayList<>();
        if (hasInvalid) {
            reasons.add(messages.get(locale, MessageKey.ERROR_WARP_REQUIREMENTS_NOT_MET));
        }
        for (final Map.Entry<CostType, BigDecimal> entry : aggregatedMissing.entrySet()) {
            final CostType type = entry.getKey();
            final double amount = entry.getValue().doubleValue();
            final String formattedAmount = formatAmountString(type, amount, economy);
            final MessageKey key = switch (type) {
                case VAULT -> MessageKey.COST_INSUFFICIENT_VAULT;
                case EXP_POINTS -> MessageKey.COST_INSUFFICIENT_EXP_POINTS;
                case EXP_LEVELS -> MessageKey.COST_INSUFFICIENT_EXP_LEVELS;
                case FOOD -> MessageKey.COST_INSUFFICIENT_FOOD;
            };
            reasons.add(messages.renderAmountPlaceholder(locale, key, formattedAmount));
        }

        if (reasons.isEmpty()) {
            return messages.get(locale, MessageKey.ERROR_INSUFFICIENT_COST);
        }

        final Component separator = messages.get(locale, MessageKey.COST_SEPARATOR);
        Component result = reasons.get(0);
        for (int i = 1; i < reasons.size(); i++) {
            result = result.append(separator).append(reasons.get(i));
        }
        return result;
    }

    private static Component formatSingleItem(
            final Locale locale,
            final CostType type,
            final double amount,
            final EconomyProvider economy,
            final Messages messages
    ) {
        final String formattedAmount = formatAmountString(type, amount, economy);
        final MessageKey key = switch (type) {
            case VAULT -> MessageKey.COST_VAULT;
            case EXP_POINTS -> MessageKey.COST_EXP_POINTS;
            case EXP_LEVELS -> MessageKey.COST_EXP_LEVELS;
            case FOOD -> MessageKey.COST_FOOD;
        };
        return messages.renderAmountPlaceholder(locale, key, formattedAmount);
    }

    private static String formatAmountString(final CostType type, final double amount, final EconomyProvider economy) {
        if (!Double.isFinite(amount)) {
            return "---";
        }
        if (type == CostType.VAULT && economy.isAvailable()) {
            return economy.format(amount);
        }
        if (type == CostType.VAULT) {
            return String.format(java.util.Locale.ROOT, "%.2f", amount);
        }
        return Long.toString(Math.round(amount));
    }
}
