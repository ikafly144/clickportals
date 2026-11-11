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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(AetheryteItemFactory.class);

    private record RecipeSignature(
            List<String> shape,
            Map<Character, Material> ingredients,
            Material resultMaterial
    ) {}

    /**
     * Functional interface building crafting {@link Recipe} instances for variations (#38).
     */
    @FunctionalInterface
    interface RecipeBuilder {
        Recipe build(NamespacedKey recipeKey, String variationId, AetheryteVariation variation, Messages messages);
    }

    private static final Map<NamespacedKey, RecipeSignature> REGISTERED_RECIPES = new ConcurrentHashMap<>();
    private static volatile VariationRegistry configuredVariations = VariationRegistry.defaults();
    private static volatile Messages configuredMessages = Messages.defaults();
    private static RecipeBuilder recipeBuilder = (recipeKey, id, variation, messages) -> {
        final ItemStack result = createItem(id, variation, 1, messages);
        final ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        recipe.setCategory(CraftingBookCategory.MISC);
        recipe.shape(variation.recipe().shape().toArray(new String[0]));
        for (final Map.Entry<Character, Material> ing : variation.recipe().bukkitIngredients().entrySet()) {
            recipe.setIngredient(ing.getKey(), ing.getValue());
        }
        return recipe;
    };

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
        if (stack == null) {
            return false;
        }
        try {
            if (stack.isEmpty()) {
                return false;
            }
            final Byte flag = stack.getPersistentDataContainer().get(ITEM_MARKER_KEY, PersistentDataType.BYTE);
            return flag != null && flag == (byte) 1;
        } catch (final Throwable ignored) {
            return false;
        }
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
     * Registers (or updates) crafting recipes for all enabled variations in {@link VariationRegistry} (#38).
     *
     * <p>To prevent Folia multi-threading race conditions (such as {@link java.util.ConcurrentModificationException}
     * when synchronizing advancements to online players), recipes are diffed against currently registered signatures.
     * If no recipe definitions have changed, Bukkit's recipe registry is not modified.</p>
     */
    public static void registerRecipes(final VariationRegistry variationRegistry, final Messages messages) {
        configuredVariations = variationRegistry;
        configuredMessages = messages;

        final Map<NamespacedKey, RecipeSignature> desiredSignatures = new LinkedHashMap<>();
        final Map<NamespacedKey, Recipe> desiredRecipes = new LinkedHashMap<>();

        for (final Map.Entry<String, AetheryteVariation> entry : variationRegistry.all().entrySet()) {
            final String id = entry.getKey();
            final AetheryteVariation variation = entry.getValue();
            if (!variation.recipe().enabled()) {
                continue;
            }
            final NamespacedKey recipeKey = new NamespacedKey("clickportals", "aetheryte_" + id);
            final RecipeSignature signature = new RecipeSignature(
                    List.copyOf(variation.recipe().shape()),
                    Map.copyOf(variation.recipe().bukkitIngredients()),
                    variation.item().bukkitMaterial()
            );
            desiredSignatures.put(recipeKey, signature);

            final Recipe recipe = recipeBuilder.build(recipeKey, id, variation, messages);
            desiredRecipes.put(recipeKey, recipe);
        }

        final List<NamespacedKey> toRemove = new ArrayList<>();
        for (final Map.Entry<NamespacedKey, RecipeSignature> current : REGISTERED_RECIPES.entrySet()) {
            final RecipeSignature desired = desiredSignatures.get(current.getKey());
            if (desired == null || !desired.equals(current.getValue())) {
                toRemove.add(current.getKey());
            }
        }

        final List<Recipe> toAdd = new ArrayList<>();
        for (final Map.Entry<NamespacedKey, RecipeSignature> desired : desiredSignatures.entrySet()) {
            final RecipeSignature current = REGISTERED_RECIPES.get(desired.getKey());
            if (current == null || !current.equals(desired.getValue())) {
                final Recipe recipe = desiredRecipes.get(desired.getKey());
                if (recipe != null) {
                    toAdd.add(recipe);
                }
            }
        }

        if (toRemove.isEmpty() && toAdd.isEmpty()) {
            LOGGER.debug("Aetheryte crafting recipes are unchanged; skipping Bukkit recipe update.");
            return;
        }

        try {
            if (!toRemove.isEmpty()) {
                Bukkit.removeRecipes(toRemove);
                for (final NamespacedKey key : toRemove) {
                    REGISTERED_RECIPES.remove(key);
                }
            }
            if (!toAdd.isEmpty()) {
                Bukkit.addRecipes(toAdd);
                for (final Map.Entry<NamespacedKey, RecipeSignature> entry : desiredSignatures.entrySet()) {
                    REGISTERED_RECIPES.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (final Throwable t) {
            LOGGER.warn("Failed to synchronize crafting recipes with online players (Folia multi-threading): {}", t.getMessage());
        }
    }

    /**
     * Unregisters all currently registered Aetheryte crafting recipes.
     */
    public static void unregisterRecipes() {
        if (REGISTERED_RECIPES.isEmpty()) {
            return;
        }
        try {
            Bukkit.removeRecipes(List.copyOf(REGISTERED_RECIPES.keySet()));
        } catch (final Throwable t) {
            LOGGER.debug("Failed to unregister crafting recipes during shutdown: {}", t.getMessage());
        }
        REGISTERED_RECIPES.clear();
    }

    /**
     * Package-private helper returning the count of currently registered recipe signatures for testing.
     */
    static int registeredRecipeCount() {
        return REGISTERED_RECIPES.size();
    }

    /**
     * Injects a recipe builder for testing.
     */
    static void setRecipeBuilderForTesting(final RecipeBuilder builder) {
        recipeBuilder = builder;
    }

    /**
     * Package-private helper resetting state for testing.
     */
    static void resetForTesting() {
        REGISTERED_RECIPES.clear();
        configuredVariations = VariationRegistry.defaults();
        configuredMessages = Messages.defaults();
        recipeBuilder = (recipeKey, id, variation, messages) -> {
            final ItemStack result = createItem(id, variation, 1, messages);
            final ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
            recipe.setCategory(CraftingBookCategory.MISC);
            recipe.shape(variation.recipe().shape().toArray(new String[0]));
            for (final Map.Entry<Character, Material> ing : variation.recipe().bukkitIngredients().entrySet()) {
                recipe.setIngredient(ing.getKey(), ing.getValue());
            }
            return recipe;
        };
    }
}
