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
        final Map<CostType, Double> totalRequiredByType = new EnumMap<>(CostType.class);

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

            evaluatedAmounts[i] = rawAmount;
            if (entry.required() && !(type == CostType.VAULT && !economy.isAvailable())) {
                totalRequiredByType.merge(type, rawAmount, Double::sum);
            }
        }

        // Check aggregate affordability per CostType
        final Map<CostType, Boolean> aggregateAffordability = new EnumMap<>(CostType.class);
        final Map<CostType, Double> remainingByType = new EnumMap<>(CostType.class);
        for (final CostType type : CostType.values()) {
            final double current = getCurrentResource(player, type, economy);
            remainingByType.put(type, current);
            final double required = totalRequiredByType.getOrDefault(type, 0.0);
            aggregateAffordability.put(type, current >= required);
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
            if (entry.required()) {
                affordable = Boolean.TRUE.equals(aggregateAffordability.get(type));
            } else {
                affordable = true;
            }

            quotes.add(new CostItemQuote(type, amount, entry.required(), affordable, remaining, false));

            // Decrement remaining resource for subsequent optional cost items
            final double deductionForSubsequent = entry.required() ? amount : Math.min(amount, remaining);
            remainingByType.put(type, Math.max(0.0, remaining - deductionForSubsequent));
        }

        return new CostQuote(quotes);
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
        if (!quote.affordable()) {
            return false;
        }

        // Verify all mandatory requirements are still satisfied in aggregate on the live player (#7)
        final Map<CostType, Double> liveRequiredSums = new EnumMap<>(CostType.class);
        for (final CostItemQuote item : quote.items()) {
            if (item.disabled() || item.type() == null) {
                continue;
            }
            if (item.required() && item.amount() > 0.0) {
                liveRequiredSums.merge(item.type(), item.amount(), Double::sum);
            }
        }
        for (final Map.Entry<CostType, Double> entry : liveRequiredSums.entrySet()) {
            final double current = getCurrentResource(player, entry.getKey(), economy);
            if (current < entry.getValue()) {
                return false;
            }
        }

        final List<CostItemQuote> applied = new ArrayList<>();
        for (final CostItemQuote item : quote.items()) {
            if (item.disabled() || item.type() == null || item.amount() <= 0.0) {
                continue;
            }
            final double toDeduct = item.effectiveDeduction();
            if (toDeduct <= 0.0) {
                continue;
            }

            final double actualDeducted = deductResource(player, item.type(), toDeduct, economy);
            if (actualDeducted < 0.0) {
                // Rollback previously applied deductions
                final boolean rollbackSuccess = rollback(player, applied, economy);
                if (!rollbackSuccess) {
                    LOGGER.error("CRITICAL: Failed to rollback charges cleanly for player {} ({})",
                            player.getName(), player.getUniqueId());
                }
                return false;
            }
            if (actualDeducted > 0.0) {
                applied.add(new CostItemQuote(item.type(), actualDeducted, item.required(), true, 0.0, false));
            }
        }

        return true;
    }

    /**
     * Refunds previously charged costs back to the player (e.g. when placement fails after charge) (#15).
     * Must be called on the player's entity thread.
     *
     * @return {@code true} if all refunds succeeded, {@code false} if any refund failed
     */
    public boolean refund(final Player player, final CostQuote quote, final EconomyProvider economy) {
        return rollback(player, quote.items(), economy);
    }

    private static boolean rollback(
            final Player player,
            final List<CostItemQuote> items,
            final EconomyProvider economy
    ) {
        boolean allSuccess = true;
        for (final CostItemQuote item : items) {
            if (item.disabled() || item.type() == null) {
                continue;
            }
            final double amount = item.effectiveDeduction();
            if (amount <= 0.0) {
                continue;
            }
            final boolean success = refundResource(player, item.type(), amount, economy);
            if (!success) {
                allSuccess = false;
            }
        }
        return allSuccess;
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
