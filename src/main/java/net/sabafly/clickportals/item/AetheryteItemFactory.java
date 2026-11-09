package net.sabafly.clickportals.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
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
 * Factory and recipe registrar for the Aetheryte creation item (custom Lodestone) (#6, #12).
 */
@SuppressWarnings("UnstableApiUsage")
public final class AetheryteItemFactory {

    public static final NamespacedKey ITEM_MARKER_KEY = new NamespacedKey("clickportals", "aetheryte_item");
    public static final NamespacedKey RECIPE_KEY = new NamespacedKey("clickportals", "aetheryte_recipe");

    private AetheryteItemFactory() {
    }

    /**
     * Creates a new Aetheryte item stack with the given amount.
     */
    public static ItemStack createItem(final int amount) {
        final ItemStack stack = ItemStack.of(Material.LODESTONE, Math.max(1, amount));
        stack.setData(DataComponentTypes.ITEM_NAME, Messages.aetheryteItemName());
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(Messages.aetheryteItemLore()));
        stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        stack.editPersistentDataContainer(pdc -> pdc.set(ITEM_MARKER_KEY, PersistentDataType.BYTE, (byte) 1));
        return stack;
    }

    /**
     * Checks whether the given item stack is an Aetheryte creation item.
     */
    public static boolean isAetheryteItem(@Nullable final ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getType() != Material.LODESTONE) {
            return false;
        }
        final Byte flag = stack.getPersistentDataContainer().get(ITEM_MARKER_KEY, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    /**
     * Registers (or re-registers) the crafting recipe configured in {@link PluginConfig}.
     */
    public static void registerRecipe(final PluginConfig config) {
        Bukkit.removeRecipe(RECIPE_KEY);
        if (!config.recipeEnabled()) {
            return;
        }
        final ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, createItem(1));
        recipe.setCategory(CraftingBookCategory.MISC);
        recipe.shape(config.recipeShape().toArray(new String[0]));
        for (final Map.Entry<Character, Material> entry : config.recipeIngredients().entrySet()) {
            recipe.setIngredient(entry.getKey(), entry.getValue());
        }
        Bukkit.addRecipe(recipe);
    }

    public static void unregisterRecipe() {
        Bukkit.removeRecipe(RECIPE_KEY);
    }
}
