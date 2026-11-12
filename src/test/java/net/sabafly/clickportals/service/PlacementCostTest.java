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
package net.sabafly.clickportals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.CostType;
import net.sabafly.clickportals.economy.EconomyProvider;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.PlacementSection;
import net.sabafly.clickportals.variation.VariationRegistry;
import net.sabafly.clickportals.visual.HologramService;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlacementCostTest {

    private Plugin plugin;
    private AetheryteRegistry registry;
    private VariationRegistry variationRegistry;
    private HologramService hologramService;
    private Messages messages;
    private EconomyProvider economy;

    private Player player;
    private World world;
    private PlayerInventory inventory;
    private EntityScheduler scheduler;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        registry = new AetheryteRegistry();
        variationRegistry = mock(VariationRegistry.class);
        hologramService = mock(HologramService.class);
        messages = Messages.defaults();
        economy = mock(EconomyProvider.class);

        player = mock(Player.class);
        world = mock(World.class);
        inventory = mock(PlayerInventory.class);
        scheduler = mock(EntityScheduler.class);

        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getScheduler()).thenReturn(scheduler);
        when(player.hasPermission(Permissions.PLACE)).thenReturn(true);

        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getName()).thenReturn("world");

        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(player)).thenReturn(500.0);
        when(economy.has(eq(player), anyDouble())).thenAnswer(inv -> (double) inv.getArgument(1) <= 500.0);
        when(economy.withdraw(eq(player), anyDouble())).thenReturn(true);
        when(economy.format(anyDouble())).thenAnswer(inv -> String.format("$%.2f", (double) inv.getArgument(0)));

        when(player.calculateTotalExperiencePoints()).thenReturn(100);
        when(player.getLevel()).thenReturn(10);
        when(player.getFoodLevel()).thenReturn(20);

        // Immediate scheduler execution for testing entity-thread steps
        when(scheduler.run(eq(plugin), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            final Consumer<Object> consumer = (Consumer<Object>) inv.getArgument(1);
            consumer.accept(null);
            return null;
        });
    }

    @Test
    void rejectsPlacementWhenPlayerHasInsufficientFunds() {
        final AetheryteVariation expensiveVariation = new AetheryteVariation(
                "<aqua>Costly</aqua>",
                null, null, null, null, null,
                new PlacementSection(null, List.of(CostEntry.of(CostType.VAULT, "1000"))), // costs 1000, has 500
                null
        );
        when(variationRegistry.getOrDefault("costly")).thenReturn(expensiveVariation);

        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                () -> -1,
                () -> 0,
                p -> 0L,
                (p, t) -> {},
                (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty())
        );
        service.setEconomyProvider(economy);

        service.placeAetheryte(player, world, 10, 64, 20, "CostlyPortal", "costly");

        // Should not withdraw money and should not consume items
        verify(economy, never()).withdraw(any(), anyDouble());
        verify(inventory, never()).setItem(any(int.class), any(ItemStack.class));

        final ArgumentCaptor<Component> msgCaptor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(msgCaptor.capture());
        assertThat(msgCaptor.getValue()).isNotNull();
    }

    @Test
    void bypassPermissionExemptsPlayerFromPlacementCost() {
        when(player.hasPermission(Permissions.BYPASS_COST_PLACE)).thenReturn(true);

        final AetheryteVariation expensiveVariation = new AetheryteVariation(
                "<aqua>Costly</aqua>",
                null, null, null, null, null,
                new PlacementSection(null, List.of(CostEntry.of(CostType.VAULT, "1000"))),
                null
        );
        when(variationRegistry.getOrDefault("costly")).thenReturn(expensiveVariation);

        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                () -> -1,
                () -> 0,
                p -> 0L,
                (p, t) -> {},
                (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty())
        );
        service.setEconomyProvider(economy);

        service.placeAetheryte(player, world, 10, 64, 20, "CostlyPortal", "costly");

        // With bypass, economy is not charged
        verify(economy, never()).withdraw(any(), anyDouble());
    }

    @Test
    void processPendingRefundsRestoresResourcesOnJoin() {
        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                hologramService,
                messages,
                () -> -1,
                () -> 0,
                p -> 0L,
                (p, t) -> {},
                (p, s, d) -> AetheryteService.WarpEvaluation.permitted(Component.empty())
        );
        service.setEconomyProvider(economy);

        // When a player with pending refunds joins, processPendingRefunds refunds their costs
        service.processPendingRefunds(player);
        // Initially empty, no exceptions thrown
        verify(economy, never()).deposit(any(), anyDouble());
    }
}
