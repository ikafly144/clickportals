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
package net.sabafly.clickportals.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.ItemSection;
import net.sabafly.clickportals.variation.AetheryteVariation.RecipeSection;
import net.sabafly.clickportals.variation.VariationRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AetheryteItemFactoryTest {

    private Server server;
    private ItemFactory itemFactory;

    @BeforeEach
    void setUp() {
        AetheryteItemFactory.resetForTesting();
        AetheryteItemFactory.setRecipeBuilderForTesting((key, id, var, msg) -> mock(org.bukkit.inventory.Recipe.class));
        server = mock(Server.class);
        itemFactory = mock(ItemFactory.class);
        when(server.getItemFactory()).thenReturn(itemFactory);
        setBukkitServer(server);
    }

    @AfterEach
    void tearDown() {
        AetheryteItemFactory.resetForTesting();
        setBukkitServer(null);
    }

    private static void setBukkitServer(final @Nullable Server server) {
        try {
            final java.lang.reflect.Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void isAetheryteItemReturnsFalseForNullOrNonAetheryteStack() {
        assertThat(AetheryteItemFactory.isAetheryteItem(null)).isFalse();
        final ItemStack emptyStack = mock(ItemStack.class);
        when(emptyStack.isEmpty()).thenReturn(true);
        assertThat(AetheryteItemFactory.isAetheryteItem(emptyStack)).isFalse();
        assertThat(AetheryteItemFactory.variationIdOf(null)).isEmpty();
    }

    @Test
    void registerRecipesDiffsAndAvoidsRedundantBukkitCalls() {
        final VariationRegistry registry = VariationRegistry.defaults();
        final Messages messages = Messages.defaults();

        // 1. First registration registers default recipe
        AetheryteItemFactory.registerRecipes(registry, messages);
        verify(server, times(1)).addRecipes(any());
        verify(server, never()).removeRecipes(any());
        assertThat(AetheryteItemFactory.registeredRecipeCount()).isEqualTo(1);

        // 2. Second registration with identical registry does NOT call Bukkit
        AetheryteItemFactory.registerRecipes(registry, messages);
        verify(server, times(1)).addRecipes(any());
        verify(server, never()).removeRecipes(any());

        // 3. Unregister calls removeRecipes
        AetheryteItemFactory.unregisterRecipes();
        verify(server, times(1)).removeRecipes(any());
        assertThat(AetheryteItemFactory.registeredRecipeCount()).isZero();
    }

    @Test
    void registerRecipesUpdatesWhenRecipeChanges() {
        final VariationRegistry initialRegistry = VariationRegistry.defaults();
        final Messages messages = Messages.defaults();

        AetheryteItemFactory.registerRecipes(initialRegistry, messages);
        verify(server, times(1)).addRecipes(any());

        // Modify recipe
        final AetheryteVariation modifiedVariation = new AetheryteVariation(
                "Default",
                ItemSection.defaults(),
                new RecipeSection(
                        true,
                        List.of(" D ", "DLD", " D "),
                        Map.of("D", "DIAMOND", "L", "LODESTONE")
                ),
                null, null, null, null, null
        );
        final VariationRegistry modifiedRegistry = new VariationRegistry(
                Map.of("default", modifiedVariation),
                mock(org.slf4j.Logger.class)
        );

        AetheryteItemFactory.registerRecipes(modifiedRegistry, messages);
        verify(server, times(1)).removeRecipes(any());
        verify(server, times(2)).addRecipes(any());
    }

    @Test
    void registerRecipesSafelyCatchesFoliaCmeWithoutPropagating() {
        doThrow(new ConcurrentModificationException("Folia concurrent advancement flush"))
                .when(server).addRecipes(any());

        assertThatCode(() ->
                AetheryteItemFactory.registerRecipes(VariationRegistry.defaults(), Messages.defaults())
        ).doesNotThrowAnyException();
    }
}
