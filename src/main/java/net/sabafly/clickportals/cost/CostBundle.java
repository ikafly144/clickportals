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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sabafly.clickportals.cost.expr.Expression;
import net.sabafly.clickportals.economy.EconomyProvider;
import org.bukkit.entity.Player;

/**
 * Composite container of {@link CostEntry} specifications enforcing all-or-nothing transactions (#7).
 */
public final class CostBundle {

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

        final List<CostItemQuote> quotes = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            final CostEntry entry = entries.get(i);
            final Expression expr = compiledExpressions.get(i);
            final double rawAmount = Math.max(0.0, expr.evaluate(variables));
            final CostType type = entry.type();

            if (type == CostType.VAULT && !economy.isAvailable()) {
                // Soft depend: currency costs are disabled when Vault is missing (#16)
                quotes.add(new CostItemQuote(type, 0.0, entry.required(), true, 0.0, true));
                continue;
            }

            final double current = getCurrentResource(player, type, economy);
            final boolean affordable = current >= rawAmount;
            quotes.add(new CostItemQuote(type, rawAmount, entry.required(), affordable, current, false));
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

        // Verify all mandatory requirements are still satisfied on the live player
        for (final CostItemQuote item : quote.items()) {
            if (item.disabled()) {
                continue;
            }
            final double current = getCurrentResource(player, item.type(), economy);
            if (item.required() && current < item.amount()) {
                return false;
            }
        }

        final List<CostItemQuote> applied = new ArrayList<>();
        for (final CostItemQuote item : quote.items()) {
            if (item.disabled() || item.amount() <= 0.0) {
                continue;
            }
            final double toDeduct = item.effectiveDeduction();
            if (toDeduct <= 0.0) {
                continue;
            }

            final boolean success = deductResource(player, item.type(), toDeduct, economy);
            if (!success) {
                // Rollback previously applied deductions
                rollback(player, applied, economy);
                return false;
            }
            applied.add(new CostItemQuote(item.type(), toDeduct, item.required(), true, 0.0, false));
        }

        return true;
    }

    /**
     * Refunds previously charged costs back to the player (e.g. when placement fails after charge) (#15).
     * Must be called on the player's entity thread.
     */
    public void refund(final Player player, final CostQuote quote, final EconomyProvider economy) {
        rollback(player, quote.items(), economy);
    }

    private static void rollback(
            final Player player,
            final List<CostItemQuote> items,
            final EconomyProvider economy
    ) {
        for (final CostItemQuote item : items) {
            if (item.disabled()) {
                continue;
            }
            final double amount = item.effectiveDeduction();
            if (amount <= 0.0) {
                continue;
            }
            refundResource(player, item.type(), amount, economy);
        }
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

    private static boolean deductResource(
            final Player player,
            final CostType type,
            final double amount,
            final EconomyProvider economy
    ) {
        return switch (type) {
            case VAULT -> economy.withdraw(player, amount);
            case EXP_POINTS -> {
                final int current = player.calculateTotalExperiencePoints();
                final int next = Math.max(0, current - (int) Math.round(amount));
                player.setExperienceLevelAndProgress(next);
                yield true;
            }
            case EXP_LEVELS -> {
                final int current = player.getLevel();
                final int next = Math.max(0, current - (int) Math.round(amount));
                player.setLevel(next);
                yield true;
            }
            case FOOD -> {
                final int current = player.getFoodLevel();
                final int next = Math.max(0, current - (int) Math.round(amount));
                player.setFoodLevel(next);
                yield true;
            }
        };
    }

    private static void refundResource(
            final Player player,
            final CostType type,
            final double amount,
            final EconomyProvider economy
    ) {
        switch (type) {
            case VAULT -> economy.deposit(player, amount);
            case EXP_POINTS -> {
                final int current = player.calculateTotalExperiencePoints();
                player.setExperienceLevelAndProgress(current + (int) Math.round(amount));
            }
            case EXP_LEVELS -> {
                final int current = player.getLevel();
                player.setLevel(current + (int) Math.round(amount));
            }
            case FOOD -> {
                final int current = player.getFoodLevel();
                player.setFoodLevel(Math.min(20, current + (int) Math.round(amount)));
            }
        }
    }
}
