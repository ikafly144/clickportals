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
package net.sabafly.clickportals.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vault API-backed {@link EconomyProvider} (#16).
 */
public final class VaultEconomyProvider implements EconomyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(VaultEconomyProvider.class);

    private final Economy economy;

    public VaultEconomyProvider(final Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean isAvailable() {
        try {
            return economy.isEnabled();
        } catch (final Throwable t) {
            LOGGER.warn("Vault economy isEnabled check failed", t);
            return false;
        }
    }

    @Override
    public double getBalance(final Player player) {
        try {
            return economy.getBalance(player);
        } catch (final Throwable t) {
            LOGGER.warn("Vault economy getBalance failed for player {}", player.getName(), t);
            return 0.0;
        }
    }

    @Override
    public boolean has(final Player player, final double amount) {
        try {
            return economy.has(player, amount);
        } catch (final Throwable t) {
            LOGGER.warn("Vault economy has failed for player {} (amount: {})", player.getName(), amount, t);
            return false;
        }
    }

    @Override
    public boolean withdraw(final Player player, final double amount) {
        if (amount <= 0.0) {
            return true;
        }
        try {
            final EconomyResponse response = economy.withdrawPlayer(player, amount);
            return response != null && response.transactionSuccess();
        } catch (final Throwable t) {
            LOGGER.warn("Vault economy withdrawPlayer failed for player {} (amount: {})", player.getName(), amount, t);
            return false;
        }
    }

    @Override
    public boolean deposit(final Player player, final double amount) {
        if (amount <= 0.0) {
            return true;
        }
        try {
            final EconomyResponse response = economy.depositPlayer(player, amount);
            return response != null && response.transactionSuccess();
        } catch (final Throwable t) {
            LOGGER.warn("Vault economy depositPlayer failed for player {} (amount: {})", player.getName(), amount, t);
            return false;
        }
    }

    @Override
    public String format(final double amount) {
        try {
            return economy.format(amount);
        } catch (final Throwable t) {
            LOGGER.debug("Vault economy format failed", t);
            return String.format(java.util.Locale.ROOT, "%.2f", amount);
        }
    }
}
