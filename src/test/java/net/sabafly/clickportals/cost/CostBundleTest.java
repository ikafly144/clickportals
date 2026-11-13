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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sabafly.clickportals.economy.EconomyProvider;
import net.sabafly.clickportals.economy.NoopEconomyProvider;
import net.sabafly.clickportals.message.Messages;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CostBundleTest {

    private Player player;
    private EconomyProvider economy;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        economy = mock(EconomyProvider.class);

        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(player)).thenReturn(1000.0);
        when(economy.has(eq(player), anyDouble())).thenAnswer(inv -> (double) inv.getArgument(1) <= 1000.0);
        when(economy.withdraw(eq(player), anyDouble())).thenReturn(true);
        when(economy.deposit(eq(player), anyDouble())).thenReturn(true);
        when(economy.format(anyDouble())).thenAnswer(inv -> String.format("$%.2f", (double) inv.getArgument(0)));

        when(player.calculateTotalExperiencePoints()).thenReturn(500);
        when(player.getLevel()).thenReturn(30);
        when(player.getFoodLevel()).thenReturn(20);
    }

    @Test
    void quoteAndChargeAllSucceeds() {
        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.VAULT, "100"),
                CostEntry.of(CostType.EXP_LEVELS, "5"),
                CostEntry.of(CostType.FOOD, "4")
        ));

        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        assertThat(quote.affordable()).isTrue();
        assertThat(quote.items()).hasSize(3);

        final boolean charged = bundle.charge(player, quote, economy);
        assertThat(charged).isTrue();

        verify(economy).withdraw(player, 100.0);
        verify(player).setLevel(25);
        verify(player).setFoodLevel(16);
    }

    @Test
    void rollbacksWhenLaterWithdrawFails() {
        // Vault succeeds, but food deduction fails to meet requirement
        when(player.getFoodLevel()).thenReturn(2); // needs 5

        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.VAULT, "50"),
                CostEntry.of(CostType.FOOD, "5", true)
        ));

        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        assertThat(quote.affordable()).isFalse();

        // Charge aborted due to unaffordability
        final boolean charged = bundle.charge(player, quote, economy);
        assertThat(charged).isFalse();
    }

    @Test
    void nonRequiredCostDeductsAvailableAndSucceeds() {
        when(player.getFoodLevel()).thenReturn(3); // only 3 food, but cost is 10 optional

        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.FOOD, "10", false)
        ));

        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        assertThat(quote.affordable()).isTrue();

        final boolean charged = bundle.charge(player, quote, economy);
        assertThat(charged).isTrue();
        verify(player).setFoodLevel(0); // consumed all 3 available
    }

    @Test
    void noopEconomyDisablesVaultCost() {
        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.VAULT, "500"),
                CostEntry.of(CostType.EXP_LEVELS, "2")
        ));

        final CostQuote quote = bundle.quote(player, Map.of(), NoopEconomyProvider.INSTANCE);
        assertThat(quote.affordable()).isTrue();
        assertThat(quote.items().get(0).disabled()).isTrue();

        final boolean charged = bundle.charge(player, quote, NoopEconomyProvider.INSTANCE);
        assertThat(charged).isTrue();
        verify(player).setLevel(28);
    }

    @Test
    void costFormatterHidesOptionalCostsWhenConfigured() {
        final Messages messages = Messages.defaults();
        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.VAULT, "100", true),
                CostEntry.of(CostType.EXP_LEVELS, "3", false)
        ));
        final CostQuote quote = bundle.quote(player, Map.of(), economy);

        // With showOptional = true
        final Component withOpt = CostFormatter.format(player, quote, economy, messages, true);
        final String textWithOpt = PlainTextComponentSerializer.plainText().serialize(withOpt);
        assertThat(textWithOpt).contains("$100.00").contains("3 Lv");

        // With showOptional = false
        final Component withoutOpt = CostFormatter.format(player, quote, economy, messages, false);
        final String textWithoutOpt = PlainTextComponentSerializer.plainText().serialize(withoutOpt);
        assertThat(textWithoutOpt).contains("$100.00").doesNotContain("3 Lv");
    }

    @Test
    void multipleEntriesOfSameTypeAggregateRequirement() {
        when(player.getLevel()).thenReturn(5); // has 5 levels

        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.EXP_LEVELS, "4"),
                CostEntry.of(CostType.EXP_LEVELS, "4")
        ));

        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        assertThat(quote.affordable()).isFalse();
        assertThat(bundle.charge(player, quote, economy)).isFalse();
    }

    @Test
    void multipleEntriesDoNotOverdrawOrDuplicateRefund() {
        final java.util.concurrent.atomic.AtomicInteger level = new java.util.concurrent.atomic.AtomicInteger(5);
        when(player.getLevel()).thenAnswer(inv -> level.get());
        org.mockito.Mockito.doAnswer(inv -> {
            level.set((int) inv.getArgument(0));
            return null;
        }).when(player).setLevel(org.mockito.ArgumentMatchers.anyInt());

        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.EXP_LEVELS, "3", true),
                CostEntry.of(CostType.EXP_LEVELS, "4", false) // optional: asks for 4, but only 2 remain
        ));

        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        assertThat(quote.affordable()).isTrue();
        assertThat(quote.items().get(0).effectiveDeduction()).isEqualTo(3.0);
        assertThat(quote.items().get(1).effectiveDeduction()).isEqualTo(2.0);

        // Required item gets 3, optional item gets remaining 2
        final boolean charged = bundle.charge(player, quote, economy);
        assertThat(charged).isTrue();
        assertThat(level.get()).isEqualTo(0);

        // Refund gives back exactly 3 + 2 = 5
        bundle.refund(player, quote, economy);
        assertThat(level.get()).isEqualTo(5);
    }

    @Test
    void invalidExpressionRejectsWarpInsteadOfFree() {
        final CostBundle bundle = new CostBundle(List.of(
                new CostEntry("vault", "$block_distance *", true)
        ));

        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        assertThat(quote.affordable()).isFalse();
        assertThat(quote.isFree()).isFalse();
        assertThat(bundle.charge(player, quote, economy)).isFalse();
    }

    @Test
    void unknownCostTypeIsRejected() {
        assertThat(CostType.fromString("foobar_currency")).isNull();

        final CostBundle bundle = new CostBundle(List.of(
                new CostEntry("foobar_currency", "10", true)
        ));

        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        assertThat(quote.affordable()).isFalse();
        assertThat(bundle.charge(player, quote, economy)).isFalse();
    }

    @Test
    void rollbackLoggingFailureReturnsFalseWhenDepositFails() {
        when(economy.deposit(eq(player), anyDouble())).thenReturn(false);

        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.VAULT, "50")
        ));
        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        final boolean refundSuccess = bundle.refund(player, quote, economy);

        assertThat(refundSuccess).isFalse();
    }

    @Test
    void optionalCostDoesNotStarveSubsequentRequiredCost() {
        final java.util.concurrent.atomic.AtomicInteger level = new java.util.concurrent.atomic.AtomicInteger(5);
        when(player.getLevel()).thenAnswer(inv -> level.get());
        org.mockito.Mockito.doAnswer(inv -> {
            level.set((int) inv.getArgument(0));
            return null;
        }).when(player).setLevel(org.mockito.ArgumentMatchers.anyInt());

        // Optional 4 followed by Required 4 with only 5 levels total
        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.EXP_LEVELS, "4", false),
                CostEntry.of(CostType.EXP_LEVELS, "4", true)
        ));

        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        assertThat(quote.affordable()).isTrue();
        // Optional cost must reserve 4 levels for the required item, so it only gets 1
        assertThat(quote.items().get(0).effectiveDeduction()).isEqualTo(1.0);
        assertThat(quote.items().get(1).effectiveDeduction()).isEqualTo(4.0);

        final CostBundle.ChargeResult result = bundle.chargeWithResult(player, quote, economy);
        assertThat(result.success()).isTrue();
        assertThat(level.get()).isEqualTo(0);

        // Required item gets 4, optional item gets 1
        assertThat(result.chargedQuote().items().get(0).amount()).isEqualTo(1.0);
        assertThat(result.chargedQuote().items().get(1).amount()).isEqualTo(4.0);
    }

    @Test
    void refundRestoresActualChargedAmountNotQuoted() {
        final java.util.concurrent.atomic.AtomicInteger food = new java.util.concurrent.atomic.AtomicInteger(5);
        when(player.getFoodLevel()).thenAnswer(inv -> food.get());
        org.mockito.Mockito.doAnswer(inv -> {
            food.set((int) inv.getArgument(0));
            return null;
        }).when(player).setFoodLevel(org.mockito.ArgumentMatchers.anyInt());

        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.FOOD, "4", false)
        ));

        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        assertThat(quote.items().get(0).effectiveDeduction()).isEqualTo(4.0);

        // Player's food drops to 1 before charge runs
        food.set(1);

        final CostBundle.ChargeResult result = bundle.chargeWithResult(player, quote, economy);
        assertThat(result.success()).isTrue();
        assertThat(food.get()).isEqualTo(0); // 1 was deducted
        assertThat(result.chargedQuote().items().get(0).amount()).isEqualTo(1.0);

        // Refund uses actual charged quote; player gets back 1 food, not 4
        bundle.refund(player, result.chargedQuote(), economy);
        assertThat(food.get()).isEqualTo(1);
    }

    @Test
    void infiniteCostFormulaDisplaysConfigurationErrorNotInsufficientBalance() {
        final Messages messages = Messages.defaults();
        final CostBundle bundle = new CostBundle(List.of(
                new CostEntry("vault", "$block_distance *", true)
        ));

        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        assertThat(quote.affordable()).isFalse();

        final Component missing = CostFormatter.formatMissingRequirements(player, quote, economy, messages);
        final String missingText = PlainTextComponentSerializer.plainText().serialize(missing);
        // Should not say "---" or insufficient currency
        assertThat(missingText).doesNotContain("---");
        assertThat(missingText).contains("Requirements not met");
        assertThat(missingText).doesNotContain("Not enough money");
    }

    @Test
    void fractionalIntegerResourceIsNormalizedAndDoesNotAbortCharge() {
        final java.util.concurrent.atomic.AtomicInteger level = new java.util.concurrent.atomic.AtomicInteger(3);
        when(player.getLevel()).thenAnswer(inv -> level.get());
        org.mockito.Mockito.doAnswer(inv -> {
            level.set((int) inv.getArgument(0));
            return null;
        }).when(player).setLevel(org.mockito.ArgumentMatchers.anyInt());

        // Formula 2.4 for an integer resource (EXP_LEVELS) should normalize to 2
        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.EXP_LEVELS, "2.4", true)
        ));

        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        assertThat(quote.affordable()).isTrue();
        assertThat(quote.items().get(0).amount()).isEqualTo(2.0);

        // Charge must deduct 2 levels and succeed without triggering a rollback
        final CostBundle.ChargeResult result = bundle.chargeWithResult(player, quote, economy);
        assertThat(result.success()).isTrue();
        assertThat(level.get()).isEqualTo(1); // 3 - 2 = 1
    }

    @Test
    void partialRefundReturnsOnlyFailedItems() {
        final java.util.concurrent.atomic.AtomicInteger level = new java.util.concurrent.atomic.AtomicInteger(5);
        when(player.getLevel()).thenAnswer(inv -> level.get());
        org.mockito.Mockito.doAnswer(inv -> {
            level.set((int) inv.getArgument(0));
            return null;
        }).when(player).setLevel(org.mockito.ArgumentMatchers.anyInt());

        when(economy.withdraw(eq(player), anyDouble())).thenReturn(true);

        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.EXP_LEVELS, "2"),
                CostEntry.of(CostType.VAULT, "100")
        ));
        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        final CostBundle.ChargeResult chargeResult = bundle.chargeWithResult(player, quote, economy);
        assertThat(chargeResult.success()).isTrue();
        assertThat(level.get()).isEqualTo(3); // 5 - 2 = 3

        // When refunding, Vault deposit fails while Exp restore succeeds
        when(economy.deposit(eq(player), anyDouble())).thenReturn(false);

        final CostBundle.RefundResult refundResult = bundle.refundWithResult(player, chargeResult.chargedQuote(), economy);

        assertThat(refundResult.success()).isFalse();
        // Exp refund succeeded, so player's level restored back to original 5 (3 + 2 = 5)
        assertThat(level.get()).isEqualTo(5);

        // Refunded items contains the EXP_LEVELS item
        assertThat(refundResult.refundedItems()).hasSize(1);
        assertThat(refundResult.refundedItems().get(0).type()).isEqualTo(CostType.EXP_LEVELS);

        // Unrefunded items list must contain only the Vault cost item
        assertThat(refundResult.unrefundedItems()).hasSize(1);
        assertThat(refundResult.unrefundedItems().get(0).type()).isEqualTo(CostType.VAULT);
        assertThat(refundResult.unrefundedItems().get(0).amount()).isEqualTo(100.0);
    }

    @Test
    void vaultDecimalAdditionAvoidsFloatingPointPrecisionLoss() {
        // Player balance is exactly 0.3
        when(economy.getBalance(player)).thenReturn(0.3);

        // Two Vault costs: 0.1 and 0.2
        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.VAULT, "0.1"),
                CostEntry.of(CostType.VAULT, "0.2")
        ));

        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        // In IEEE 754 double, 0.1 + 0.2 = 0.30000000000000004 > 0.3
        // With BigDecimal decimal aggregation, it evaluates to exactly 0.3 and is affordable
        assertThat(quote.affordable()).isTrue();
    }

    @Test
    void costFormatterAggregatesMultipleItemsOfSameCostType() {
        when(economy.format(85.0)).thenReturn("$85.00");
        final Messages messages = Messages.defaults();
        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.VAULT, "55"),
                CostEntry.of(CostType.VAULT, "30")
        ));
        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        final Component formatted = CostFormatter.format(player, quote, economy, messages, true);
        final String text = PlainTextComponentSerializer.plainText().serialize(formatted);
        assertThat(text).isEqualTo("$85.00");
    }

    @Test
    void costFormatterAggregatesMissingRequirementsOfSameCostType() {
        when(economy.getBalance(player)).thenReturn(50.0);
        when(economy.format(85.0)).thenReturn("$85.00");
        when(economy.format(35.0)).thenReturn("$35.00");
        final Messages messages = Messages.defaults();
        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.VAULT, "55"),
                CostEntry.of(CostType.VAULT, "30")
        ));
        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        final Component missing = CostFormatter.formatMissingRequirements(player, quote, economy, messages);
        final String text = PlainTextComponentSerializer.plainText().serialize(missing);
        assertThat(text).contains("$85.00");
    }

    @Test
    void requiredAndOptionalVaultCombinationAvoidsPrecisionDrift() {
        final java.util.concurrent.atomic.AtomicReference<Double> balance = new java.util.concurrent.atomic.AtomicReference<>(0.30);
        when(economy.getBalance(player)).thenAnswer(inv -> balance.get());
        when(economy.withdraw(eq(player), anyDouble())).thenAnswer(inv -> {
            final double amount = inv.getArgument(1);
            balance.set(balance.get() - amount);
            return true;
        });

        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.VAULT, "0.20", true),
                CostEntry.of(CostType.VAULT, "0.10", false)
        ));
        final CostQuote quote = bundle.quote(player, Map.of(), economy);
        assertThat(quote.affordable()).isTrue();
        assertThat(quote.items().get(1).currentResource()).isEqualTo(0.10);

        final CostBundle.ChargeResult result = bundle.chargeWithResult(player, quote, economy);
        assertThat(result.success()).isTrue();
        verify(economy).withdraw(player, 0.20);
        verify(economy).withdraw(player, 0.10);
    }
}
