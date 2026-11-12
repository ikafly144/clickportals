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

/**
 * Vault API-backed {@link EconomyProvider} (#16).
 */
public final class VaultEconomyProvider implements EconomyProvider {

    private final Economy economy;

    public VaultEconomyProvider(final Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean isAvailable() {
        return economy.isEnabled();
    }

    @Override
    public double getBalance(final Player player) {
        return economy.getBalance(player);
    }

    @Override
    public boolean has(final Player player, final double amount) {
        return economy.has(player, amount);
    }

    @Override
    public boolean withdraw(final Player player, final double amount) {
        if (amount <= 0.0) {
            return true;
        }
        final EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    @Override
    public boolean deposit(final Player player, final double amount) {
        if (amount <= 0.0) {
            return true;
        }
        final EconomyResponse response = economy.depositPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    @Override
    public String format(final double amount) {
        return economy.format(amount);
    }
}
