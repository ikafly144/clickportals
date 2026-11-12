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

import java.util.Locale;
import org.bukkit.entity.Player;

/**
 * No-op fallback economy provider when Vault or an economy hook is unavailable (#16).
 */
public final class NoopEconomyProvider implements EconomyProvider {

    public static final NoopEconomyProvider INSTANCE = new NoopEconomyProvider();

    private NoopEconomyProvider() {
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public double getBalance(final Player player) {
        return 0.0;
    }

    @Override
    public boolean has(final Player player, final double amount) {
        return false;
    }

    @Override
    public boolean withdraw(final Player player, final double amount) {
        return false;
    }

    @Override
    public boolean deposit(final Player player, final double amount) {
        return false;
    }

    @Override
    public String format(final double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }
}
