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
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.UseCooldown;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.CustomModelDataSection;
import net.sabafly.clickportals.variation.AetheryteVariation.ItemSection;
import net.sabafly.clickportals.variation.VariationRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/**
 * Factory and recipe registrar for variation-aware Aetheryte creation items (#6, #12, #38).
 */
@SuppressWarnings("UnstableApiUsage")
public final class AetheryteItemFactory {

    public static final NamespacedKey ITEM_MARKER_KEY = new NamespacedKey("clickportals", "aetheryte_item");
    public static final NamespacedKey VARIATION_KEY = new NamespacedKey("clickportals", "variation");
    /**
     * Cooldown group resource location key shared by all Aetheryte creation items for client-side visual cooldowns (#43).
     */
    public static final Key COOLDOWN_GROUP_KEY = Key.key("clickportals", "aetheryte_place");
    private static final float VISUAL_COOLDOWN_PLACEHOLDER_SECONDS = 1.0f;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

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
                DataComponentTypes.DAMAGE,
                DataComponentTypes.MAX_DAMAGE,
                DataComponentTypes.ENCHANTABLE,
                DataComponentTypes.REPAIRABLE
        );
    }

    private static final Set<NamespacedKey> REGISTERED_RECIPE_KEYS = ConcurrentHashMap.newKeySet();
    private static volatile VariationRegistry configuredVariations = VariationRegistry.defaults();
    private static volatile Messages configuredMessages = Messages.defaults();

    private AetheryteItemFactory() {
    }

    /**
     * Creates a new Aetheryte item stack for the {@code default} variation with the given amount.
     */
    public static ItemStack createItem(final int amount) {
        return createItem(Aetheryte.DEFAULT_VARIATION, amount);
    }

    /**
     * Creates a new Aetheryte item stack for the specified variation ID and amount using the active
     * {@link VariationRegistry} and {@link Messages}.
     */
    public static ItemStack createItem(final String variationId, final int amount) {
        final String normalizedId = Aetheryte.normalizeVariation(variationId);
        final AetheryteVariation variation = configuredVariations.getOrDefault(normalizedId);
        return createItem(normalizedId, variation, amount, configuredMessages);
    }

    /**
     * Creates a new Aetheryte creation item stack for the specified variation definition and amount (#6, #38).
     */
    public static ItemStack createItem(
            final String variationId,
            final AetheryteVariation variation,
            final int amount,
            final Messages messages
    ) {
        final String normalizedId = Aetheryte.normalizeVariation(variationId);
        final ItemSection itemCfg = variation.item();
        final Material baseMaterial = itemCfg.bukkitMaterial();

        final ItemStack stack = ItemStack.of(baseMaterial, Math.max(1, amount));
        for (final DataComponentType componentType : MaskedComponentHolder.MASKED_COMPONENTS) {
            stack.unsetData(componentType);
        }
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build());

        final Component itemName = itemCfg.name().isBlank()
                ? messages.aetheryteItemName()
                : MINI_MESSAGE.deserialize(itemCfg.name()).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        stack.setData(DataComponentTypes.ITEM_NAME, itemName);

        final List<Component> loreLines;
        if (itemCfg.lore().isEmpty()) {
            loreLines = messages.aetheryteItemLore();
        } else {
            final List<Component> parsedLore = new ArrayList<>(itemCfg.lore().size());
            for (final String line : itemCfg.lore()) {
                parsedLore.add(
                        MINI_MESSAGE.deserialize(line)
                                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
                );
            }
            loreLines = List.copyOf(parsedLore);
        }
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(loreLines));
        stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, itemCfg.glint());
        stack.setData(
                DataComponentTypes.USE_COOLDOWN,
                UseCooldown.useCooldown(VISUAL_COOLDOWN_PLACEHOLDER_SECONDS)
                        .cooldownGroup(COOLDOWN_GROUP_KEY)
                        .build()
        );

        applyModelComponents(stack, itemCfg.itemModel(), itemCfg.customModelData());

        stack.editPersistentDataContainer(pdc -> {
            pdc.set(ITEM_MARKER_KEY, PersistentDataType.BYTE, (byte) 1);
            pdc.set(VARIATION_KEY, PersistentDataType.STRING, normalizedId);
        });
        return stack;
    }

    /**
     * Applies optional {@code minecraft:item_model} and {@code minecraft:custom_model_data} components to {@code stack}.
     */
    @SuppressWarnings("PatternValidation")
    private static void applyModelComponents(
            final ItemStack stack,
            final String itemModel,
            final CustomModelDataSection cmdSection
    ) {
        if (!itemModel.isBlank()) {
            try {
                stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(itemModel.strip()));
            } catch (final InvalidKeyException ignored) {
                // Ignore invalid resource location syntax
            }
        }
        if (!cmdSection.isEmpty()) {
            final CustomModelData.Builder builder = CustomModelData.customModelData();
            if (!cmdSection.floats().isEmpty()) {
                builder.addFloats(cmdSection.floats());
            }
            if (!cmdSection.flags().isEmpty()) {
                builder.addFlags(cmdSection.flags());
            }
            if (!cmdSection.strings().isEmpty()) {
                builder.addStrings(cmdSection.strings());
            }
            if (!cmdSection.colors().isEmpty()) {
                builder.addColors(cmdSection.bukkitColors());
            }
            stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, builder.build());
        }
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
     * Extracts the variation identifier from an Aetheryte creation item stack, returning
     * {@link Aetheryte#DEFAULT_VARIATION} for legacy items without a variation tag, or {@link Optional#empty()}
     * if the stack is not an Aetheryte item (#38).
     */
    public static Optional<String> variationIdOf(@Nullable final ItemStack stack) {
        if (!isAetheryteItem(stack) || stack == null) {
            return Optional.empty();
        }
        final String stored = stack.getPersistentDataContainer().get(VARIATION_KEY, PersistentDataType.STRING);
        return Optional.of(Aetheryte.normalizeVariation(stored));
    }

    /**
     * Checks whether the given item stack is an Aetheryte creation item matching the specified variation ID (#38).
     */
    public static boolean isAetheryteItemOfVariation(@Nullable final ItemStack stack, final String expectedVariationId) {
        final Optional<String> actual = variationIdOf(stack);
        return actual.isPresent() && actual.get().equals(Aetheryte.normalizeVariation(expectedVariationId));
    }

    /**
     * Registers (or re-registers) crafting recipes for all enabled variations in {@link VariationRegistry} (#38).
     */
    public static void registerRecipes(final VariationRegistry variationRegistry, final Messages messages) {
        configuredVariations = variationRegistry;
        configuredMessages = messages;
        unregisterRecipes();

        for (final Map.Entry<String, AetheryteVariation> entry : variationRegistry.all().entrySet()) {
            final String id = entry.getKey();
            final AetheryteVariation variation = entry.getValue();
            if (!variation.recipe().enabled()) {
                continue;
            }
            final NamespacedKey recipeKey = new NamespacedKey("clickportals", "aetheryte_" + id);
            final ItemStack result = createItem(id, variation, 1, messages);
            final ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
            recipe.setCategory(CraftingBookCategory.MISC);
            recipe.shape(variation.recipe().shape().toArray(new String[0]));
            for (final Map.Entry<Character, Material> ing : variation.recipe().bukkitIngredients().entrySet()) {
                recipe.setIngredient(ing.getKey(), ing.getValue());
            }
            Bukkit.addRecipe(recipe);
            REGISTERED_RECIPE_KEYS.add(recipeKey);
        }
    }

    /**
     * Unregisters all currently registered Aetheryte crafting recipes.
     */
    public static void unregisterRecipes() {
        for (final NamespacedKey key : REGISTERED_RECIPE_KEYS) {
            Bukkit.removeRecipe(key);
        }
        REGISTERED_RECIPE_KEYS.clear();
    }
}
