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

import org.bukkit.entity.Player;

/**
 * Abstraction layer for server economy providers (#16).
 */
public interface EconomyProvider {

    /**
     * Checks whether an economy plugin is active and available.
     */
    boolean isAvailable();

    /**
     * Returns the currency balance of the specified player.
     */
    double getBalance(Player player);

    /**
     * Checks whether the player holds at least {@code amount} currency.
     */
    boolean has(Player player, double amount);

    /**
     * Withdraws the specified {@code amount} from the player.
     * Must be called on the player's entity thread.
     *
     * @return {@code true} if successful, {@code false} if insufficient funds or failed
     */
    boolean withdraw(Player player, double amount);

    /**
     * Deposits the specified {@code amount} to the player (used for rollbacks and refunds).
     * Must be called on the player's entity thread.
     *
     * @return {@code true} if successful
     */
    boolean deposit(Player player, double amount);

    /**
     * Formats the currency amount into a human-readable display string (e.g. {@code "$10.00"}).
     */
    String format(double amount);
}
