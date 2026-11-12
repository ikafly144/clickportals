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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.sabafly.clickportals.cost.expr.Expression;
import net.sabafly.clickportals.economy.EconomyProvider;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composite container of {@link CostEntry} specifications enforcing all-or-nothing transactions (#7).
 */
public final class CostBundle {

    private static final Logger LOGGER = LoggerFactory.getLogger(CostBundle.class);

    private final List<CostEntry> entries;
    private final List<Expression> compiledExpressions;

    public CostBundle(final List<CostEntry> entries) {
        this.entries = List.copyOf(entries);
        final List<Expression> compiled = new ArrayList<>(entries.size());
        for (final CostEntry entry : entries) {
            compiled.add(entry.compile());
        }
        this.compiledExpressions = List.copyOf(compiled);
    }

    public List<CostEntry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Assesses this bundle against the given player and contextual variables.
     *
     * @param player player to assess against
     * @param externalVariables contextual variables (e.g. {@code block_distance})
     * @param economy active economy provider
     * @return assessed {@link CostQuote}
     */
    public CostQuote quote(
            final Player player,
            final Map<String, Double> externalVariables,
            final EconomyProvider economy
    ) {
        if (entries.isEmpty()) {
            return CostQuote.FREE;
        }

        final Map<String, Double> variables = new HashMap<>(externalVariables);
        populatePlayerVariables(player, economy, variables);

        // First pass: evaluate raw amounts and aggregate required sums by CostType (#7)
        final double[] evaluatedAmounts = new double[entries.size()];
        final Map<CostType, BigDecimal> totalRequiredByType = new EnumMap<>(CostType.class);

        for (int i = 0; i < entries.size(); i++) {
            final CostEntry entry = entries.get(i);
            final Expression expr = compiledExpressions.get(i);
            final CostType type = entry.type();

            if (type == null || Objects.equals(expr, Expression.INVALID)) {
                evaluatedAmounts[i] = Double.POSITIVE_INFINITY;
                continue;
            }

            final double rawAmount = expr.evaluate(variables);
            if (!Double.isFinite(rawAmount) || rawAmount < 0.0) {
                evaluatedAmounts[i] = Double.POSITIVE_INFINITY;
                continue;
            }

            final double normalizedAmount = type.normalize(rawAmount);
            evaluatedAmounts[i] = normalizedAmount;
            if (entry.required() && !(type == CostType.VAULT && !economy.isAvailable())) {
                totalRequiredByType.merge(type, type.toBigDecimal(normalizedAmount), BigDecimal::add);
            }
        }

        // Check aggregate affordability per CostType and reserve required costs (#7)
        final Map<CostType, Boolean> aggregateAffordability = new EnumMap<>(CostType.class);
        final Map<CostType, Double> remainingByType = new EnumMap<>(CostType.class);
        final Map<CostType, Double> remainingRequiredByType = new EnumMap<>(CostType.class);
        for (final CostType type : CostType.values()) {
            final double current = getCurrentResource(player, type, economy);
            remainingByType.put(type, current);
            final BigDecimal requiredBd = totalRequiredByType.getOrDefault(type, BigDecimal.ZERO);
            final double required = requiredBd.doubleValue();
            final BigDecimal currentBd = type.toBigDecimal(current);
            aggregateAffordability.put(type, currentBd.compareTo(requiredBd) >= 0);
            remainingRequiredByType.put(type, required);
        }

        final List<CostItemQuote> quotes = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            final CostEntry entry = entries.get(i);
            final CostType type = entry.type();
            final double amount = evaluatedAmounts[i];

            if (type == null || !Double.isFinite(amount)) {
                // Invalid cost specification: mark as mandatory unaffordable so transaction is rejected
                quotes.add(new CostItemQuote(type, Double.POSITIVE_INFINITY, true, false, 0.0, false));
                continue;
            }

            if (type == CostType.VAULT && !economy.isAvailable()) {
                // Soft depend: currency costs are disabled when Vault is missing (#16)
                quotes.add(new CostItemQuote(type, 0.0, entry.required(), true, 0.0, true));
                continue;
            }

            final double remaining = remainingByType.getOrDefault(type, 0.0);

            final boolean affordable;
            final double deductionForSubsequent;
            if (entry.required()) {
                affordable = Boolean.TRUE.equals(aggregateAffordability.get(type));
                quotes.add(new CostItemQuote(type, amount, true, affordable, remaining, false));
                deductionForSubsequent = amount;
                remainingRequiredByType.merge(type, -amount, (oldVal, change) -> Math.max(0.0, oldVal + change));
            } else {
                affordable = true;
                final double unreserved = Math.max(0.0, remaining - remainingRequiredByType.getOrDefault(type, 0.0));
                quotes.add(new CostItemQuote(type, amount, false, true, unreserved, false));
                deductionForSubsequent = Math.min(amount, unreserved);
            }

            remainingByType.put(type, Math.max(0.0, remaining - deductionForSubsequent));
        }

        return new CostQuote(quotes);
    }

    /**
     * Represents the outcome of an execution charge, capturing actual deductions for exact refunds (#7, #15).
     *
     * @param success whether the charge fully succeeded
     * @param chargedQuote quote containing the actual deducted amounts per item
     */
    public record ChargeResult(boolean success, CostQuote chargedQuote) {
        public static ChargeResult failure() {
            return new ChargeResult(false, new CostQuote(List.of(), false));
        }

        public static ChargeResult success(final List<CostItemQuote> applied) {
            return new ChargeResult(true, new CostQuote(applied, true));
        }
    }

    /**
     * Charges the player according to the quote, rolling back any partial deductions if a component fails (#7).
     * Must be called on the player's entity thread.
     *
     * @param player player to charge
     * @param quote assessed quote to execute
     * @param economy active economy provider
     * @return {@code true} if all charges succeeded, {@code false} if aborted and rolled back
     */
    public boolean charge(final Player player, final CostQuote quote, final EconomyProvider economy) {
        return chargeWithResult(player, quote, economy).success();
    }

    /**
     * Executes charge with a detailed result capturing actual deducted amounts (#7, #15).
     *
     * @param player player to charge
     * @param quote assessed quote to execute
     * @param economy active economy provider
     * @return charge result containing success flag and charged quote
     */
    public ChargeResult chargeWithResult(final Player player, final CostQuote quote, final EconomyProvider economy) {
        if (!quote.affordable()) {
            return ChargeResult.failure();
        }

        // Verify all mandatory requirements are still satisfied in aggregate on the live player (#7)
        final Map<CostType, BigDecimal> liveRequiredSums = new EnumMap<>(CostType.class);
        for (final CostItemQuote item : quote.items()) {
            if (item.disabled() || item.type() == null) {
                continue;
            }
            if (item.required() && item.amount() > 0.0) {
                liveRequiredSums.merge(item.type(), item.type().toBigDecimal(item.amount()), BigDecimal::add);
            }
        }
        for (final Map.Entry<CostType, BigDecimal> entry : liveRequiredSums.entrySet()) {
            final double current = getCurrentResource(player, entry.getKey(), economy);
            final BigDecimal currentBd = entry.getKey().toBigDecimal(current);
            if (currentBd.compareTo(entry.getValue()) < 0) {
                return ChargeResult.failure();
            }
        }

        final Map<CostType, Double> remainingRequired = new EnumMap<>(CostType.class);
        liveRequiredSums.forEach((k, v) -> remainingRequired.put(k, v.doubleValue()));
        final List<CostItemQuote> applied = new ArrayList<>();
        for (final CostItemQuote item : quote.items()) {
            if (item.disabled() || item.type() == null || item.amount() <= 0.0) {
                continue;
            }

            final double current = getCurrentResource(player, item.type(), economy);
            final double unreserved = Math.max(0.0, current - remainingRequired.getOrDefault(item.type(), 0.0));

            final double toDeduct;
            if (item.required()) {
                toDeduct = item.amount();
            } else {
                toDeduct = Math.min(item.amount(), unreserved);
            }

            if (toDeduct <= 0.0) {
                continue;
            }

            final double actualDeducted = deductResource(player, item.type(), toDeduct, economy);
            if (actualDeducted < 0.0
                    || (item.required() && item.type().toBigDecimal(actualDeducted).compareTo(item.type().toBigDecimal(toDeduct)) < 0)) {
                if (actualDeducted > 0.0) {
                    applied.add(new CostItemQuote(item.type(), actualDeducted, item.required(), true, actualDeducted, false));
                }
                // Rollback previously applied deductions
                final boolean rollbackSuccess = rollback(player, applied, economy);
                if (!rollbackSuccess) {
                    LOGGER.error("CRITICAL: Failed to rollback charges cleanly for player {} ({})",
                            player.getName(), player.getUniqueId());
                }
                return ChargeResult.failure();
            }

            if (item.required()) {
                remainingRequired.merge(item.type(), -actualDeducted, (oldVal, change) -> Math.max(0.0, oldVal + change));
            }

            if (actualDeducted > 0.0) {
                applied.add(new CostItemQuote(item.type(), actualDeducted, item.required(), true, actualDeducted, false));
            }
        }

        return ChargeResult.success(applied);
    }

    /**
     * Outcome of a refund operation tracking whether all items were restored and itemized tracking of refunded/unrefunded items (#15).
     *
     * @param success whether all items were successfully refunded
     * @param refundedItems list of items that were successfully refunded
     * @param unrefundedItems list of items that could not be refunded
     */
    public record RefundResult(
            boolean success,
            List<CostItemQuote> refundedItems,
            List<CostItemQuote> unrefundedItems
    ) {
        public static RefundResult successful(final List<CostItemQuote> refunded) {
            return new RefundResult(true, List.copyOf(refunded), List.of());
        }

        public static RefundResult failure(final List<CostItemQuote> refunded, final List<CostItemQuote> unrefunded) {
            return new RefundResult(false, List.copyOf(refunded), List.copyOf(unrefunded));
        }
    }

    /**
     * Refunds previously charged costs back to the player, returning detailed itemized failure tracking (#15).
     * Must be called on the player's entity thread.
     *
     * @param player player to refund
     * @param quote quote containing items to refund
     * @param economy active economy provider
     * @return {@link RefundResult} containing success status and list of unrefunded items
     */
    public RefundResult refundWithResult(final Player player, final CostQuote quote, final EconomyProvider economy) {
        return rollbackWithResult(player, quote.items(), economy);
    }

    /**
     * Refunds previously charged costs back to the player (e.g. when placement fails after charge) (#15).
     * Must be called on the player's entity thread.
     *
     * @return {@code true} if all refunds succeeded, {@code false} if any refund failed
     */
    public boolean refund(final Player player, final CostQuote quote, final EconomyProvider economy) {
        return refundWithResult(player, quote, economy).success();
    }

    private static boolean rollback(
            final Player player,
            final List<CostItemQuote> items,
            final EconomyProvider economy
    ) {
        return rollbackWithResult(player, items, economy).success();
    }

    private static RefundResult rollbackWithResult(
            final Player player,
            final List<CostItemQuote> items,
            final EconomyProvider economy
    ) {
        final List<CostItemQuote> refunded = new ArrayList<>();
        final List<CostItemQuote> unrefunded = new ArrayList<>();
        boolean allSuccess = true;
        for (final CostItemQuote item : items) {
            if (item.disabled() || item.type() == null) {
                refunded.add(item);
                continue;
            }
            final double amount = item.effectiveDeduction();
            if (amount <= 0.0) {
                refunded.add(item);
                continue;
            }
            final boolean success = refundResource(player, item.type(), amount, economy);
            if (!success) {
                allSuccess = false;
                unrefunded.add(item);
            } else {
                refunded.add(item);
            }
        }
        return allSuccess ? RefundResult.successful(refunded) : RefundResult.failure(refunded, unrefunded);
    }

    private static void populatePlayerVariables(
            final Player player,
            final EconomyProvider economy,
            final Map<String, Double> variables
    ) {
        variables.put("balance", economy.isAvailable() ? economy.getBalance(player) : 0.0);
        variables.put("exp_points", (double) player.calculateTotalExperiencePoints());
        variables.put("exp_level", (double) player.getLevel());
        variables.put("food", (double) player.getFoodLevel());
    }

    private static double getCurrentResource(
            final Player player,
            final CostType type,
            final EconomyProvider economy
    ) {
        return switch (type) {
            case VAULT -> economy.isAvailable() ? economy.getBalance(player) : 0.0;
            case EXP_POINTS -> player.calculateTotalExperiencePoints();
            case EXP_LEVELS -> player.getLevel();
            case FOOD -> player.getFoodLevel();
        };
    }

    private static double deductResource(
            final Player player,
            final CostType type,
            final double amount,
            final EconomyProvider economy
    ) {
        return switch (type) {
            case VAULT -> {
                final boolean success = economy.withdraw(player, amount);
                yield success ? amount : -1.0;
            }
            case EXP_POINTS -> {
                final int current = player.calculateTotalExperiencePoints();
                final int toDeduct = (int) Math.round(amount);
                final int actual = Math.min(current, toDeduct);
                player.setExperienceLevelAndProgress(current - actual);
                yield actual;
            }
            case EXP_LEVELS -> {
                final int current = player.getLevel();
                final int toDeduct = (int) Math.round(amount);
                final int actual = Math.min(current, toDeduct);
                player.setLevel(current - actual);
                yield actual;
            }
            case FOOD -> {
                final int current = player.getFoodLevel();
                final int toDeduct = (int) Math.round(amount);
                final int actual = Math.min(current, toDeduct);
                player.setFoodLevel(current - actual);
                yield actual;
            }
        };
    }

    private static boolean refundResource(
            final Player player,
            final CostType type,
            final double amount,
            final EconomyProvider economy
    ) {
        return switch (type) {
            case VAULT -> {
                final boolean success = economy.deposit(player, amount);
                if (!success) {
                    LOGGER.error(
                            "CRITICAL: Failed to deposit refund of {} Vault currency to player {} ({})",
                            amount, player.getName(), player.getUniqueId());
                }
                yield success;
            }
            case EXP_POINTS -> {
                final int current = player.calculateTotalExperiencePoints();
                player.setExperienceLevelAndProgress(current + (int) Math.round(amount));
                yield true;
            }
            case EXP_LEVELS -> {
                final int current = player.getLevel();
                player.setLevel(current + (int) Math.round(amount));
                yield true;
            }
            case FOOD -> {
                final int current = player.getFoodLevel();
                player.setFoodLevel(Math.min(20, current + (int) Math.round(amount)));
                yield true;
            }
        };
    }
}
