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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VaultEconomyProviderTest {

    private Economy economy;
    private Player player;
    private VaultEconomyProvider provider;

    @BeforeEach
    void setUp() {
        economy = mock(Economy.class);
        player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        provider = new VaultEconomyProvider(economy);
    }

    @Test
    void isAvailableReturnsTrueWhenEnabledAndFalseWhenThrows() {
        when(economy.isEnabled()).thenReturn(true);
        assertThat(provider.isAvailable()).isTrue();

        when(economy.isEnabled()).thenThrow(new RuntimeException("Vault plugin crashed"));
        assertThat(provider.isAvailable()).isFalse();
    }

    @Test
    void getBalanceReturnsBalanceAndZeroWhenThrows() {
        when(economy.getBalance(player)).thenReturn(150.0);
        assertThat(provider.getBalance(player)).isEqualTo(150.0);

        when(economy.getBalance(player)).thenThrow(new RuntimeException("Database error"));
        assertThat(provider.getBalance(player)).isEqualTo(0.0);
    }

    @Test
    void hasReturnsTrueWhenSufficientAndFalseWhenThrows() {
        when(economy.has(player, 50.0)).thenReturn(true);
        assertThat(provider.has(player, 50.0)).isTrue();

        when(economy.has(player, 50.0)).thenThrow(new RuntimeException("Database error"));
        assertThat(provider.has(player, 50.0)).isFalse();
    }

    @Test
    void withdrawReturnsTrueOnSuccessAndFalseWhenThrows() {
        when(economy.withdrawPlayer(player, 50.0))
                .thenReturn(new EconomyResponse(50.0, 100.0, EconomyResponse.ResponseType.SUCCESS, null));
        assertThat(provider.withdraw(player, 50.0)).isTrue();

        when(economy.withdrawPlayer(player, 50.0)).thenThrow(new RuntimeException("Vault lock failure"));
        assertThat(provider.withdraw(player, 50.0)).isFalse();
    }

    @Test
    void depositReturnsTrueOnSuccessAndFalseWhenThrows() {
        when(economy.depositPlayer(player, 50.0))
                .thenReturn(new EconomyResponse(50.0, 150.0, EconomyResponse.ResponseType.SUCCESS, null));
        assertThat(provider.deposit(player, 50.0)).isTrue();

        when(economy.depositPlayer(player, 50.0)).thenThrow(new RuntimeException("Vault lock failure"));
        assertThat(provider.deposit(player, 50.0)).isFalse();
    }

    @Test
    void formatReturnsFormattedStringAndFallbackWhenThrows() {
        when(economy.format(123.45)).thenReturn("$123.45");
        assertThat(provider.format(123.45)).isEqualTo("$123.45");

        when(economy.format(123.45)).thenThrow(new RuntimeException("Formatter error"));
        assertThat(provider.format(123.45)).isEqualTo("123.45");
    }
}
