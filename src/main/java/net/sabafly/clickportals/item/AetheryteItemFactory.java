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

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemLore;
import java.util.List;
import java.util.Map;
import net.sabafly.clickportals.config.PluginConfig;
import net.sabafly.clickportals.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/**
 * Factory and recipe registrar for the Aetheryte creation item with masked item components (#6, #12, #19).
 */
@SuppressWarnings("UnstableApiUsage")
public final class AetheryteItemFactory {

    public static final NamespacedKey ITEM_MARKER_KEY = new NamespacedKey("clickportals", "aetheryte_item");
    public static final NamespacedKey RECIPE_KEY = new NamespacedKey("clickportals", "aetheryte_recipe");

    private static final class MaskedComponentHolder {
        private static final List<DataComponentType> MASKED_COMPONENTS = List.of(
                DataComponentTypes.CONSUMABLE,
                DataComponentTypes.FOOD,
                DataComponentTypes.TOOL,
                DataComponentTypes.WEAPON,
                DataComponentTypes.EQUIPPABLE,
                DataComponentTypes.BLOCKS_ATTACKS,
                DataComponentTypes.DEATH_PROTECTION,
                DataComponentTypes.GLIDER,
                DataComponentTypes.USE_REMAINDER,
                DataComponentTypes.USE_COOLDOWN,
                DataComponentTypes.DAMAGE,
                DataComponentTypes.MAX_DAMAGE,
                DataComponentTypes.ENCHANTABLE,
                DataComponentTypes.REPAIRABLE
        );
    }

    private static volatile Material configuredMaterial = Material.COMPASS;
    private static volatile Messages configuredMessages = Messages.defaults();

    private AetheryteItemFactory() {
    }

    /**
     * Creates a new Aetheryte item stack with the given amount using the configured base material
     * and masks native item components so the base item's vanilla behavior is disabled (#6).
     */
    public static ItemStack createItem(final int amount) {
        return createItem(configuredMaterial, amount, configuredMessages);
    }

    /**
     * Creates a new Aetheryte item stack with the specified base material and amount, masking its vanilla components.
     */
    public static ItemStack createItem(final Material baseMaterial, final int amount) {
        return createItem(baseMaterial, amount, configuredMessages);
    }

    /**
     * Creates a new Aetheryte item stack with the specified base material, amount, and {@link Messages} service.
     */
    public static ItemStack createItem(final Material baseMaterial, final int amount, final Messages messages) {
        final ItemStack stack = ItemStack.of(baseMaterial, Math.max(1, amount));
        for (final DataComponentType componentType : MaskedComponentHolder.MASKED_COMPONENTS) {
            stack.unsetData(componentType);
        }
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build());
        stack.setData(DataComponentTypes.ITEM_NAME, messages.aetheryteItemName());
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(messages.aetheryteItemLore()));
        stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        stack.editPersistentDataContainer(pdc -> pdc.set(ITEM_MARKER_KEY, PersistentDataType.BYTE, (byte) 1));
        return stack;
    }

    /**
     * Checks whether the given item stack is an Aetheryte creation item via its PDC marker flag (#6).
     */
    public static boolean isAetheryteItem(@Nullable final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        final Byte flag = stack.getPersistentDataContainer().get(ITEM_MARKER_KEY, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    /**
     * Registers (or re-registers) the crafting recipe configured in {@link PluginConfig}.
     */
    public static void registerRecipe(final PluginConfig config) {
        registerRecipe(config, configuredMessages);
    }

    /**
     * Registers (or re-registers) the crafting recipe configured in {@link PluginConfig} using the given {@link Messages}.
     */
    public static void registerRecipe(final PluginConfig config, final Messages messages) {
        configuredMaterial = config.itemMaterial();
        configuredMessages = messages;
        Bukkit.removeRecipe(RECIPE_KEY);
        if (!config.recipeEnabled()) {
            return;
        }
        final ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, createItem(configuredMaterial, 1, messages));
        recipe.setCategory(CraftingBookCategory.MISC);
        recipe.shape(config.recipeShape().toArray(new String[0]));
        for (final Map.Entry<Character, Material> entry : config.recipeIngredients().entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue());
        }
        Bukkit.addRecipe(recipe);
    }

    /**
     * Unregisters the Aetheryte crafting recipe.
     */
    public static void unregisterRecipe() {
        Bukkit.removeRecipe(RECIPE_KEY);
    }
}
