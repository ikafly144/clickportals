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
package net.sabafly.clickportals.variation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.config.DistanceSection;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.expr.ExpressionException;
import net.sabafly.clickportals.cost.expr.ExpressionParser;
import org.bukkit.Color;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/**
 * Type-safe Configurate model representing a single Aetheryte variation loaded from
 * {@code <dataDir>/aetheryte/<type>.yml} (#36, #37, #38, #39, #40, #41, #42, #43).
 */
@ConfigSerializable
public record AetheryteVariation(
        @Setting("display-name")
        @Comment("Display name of this Aetheryte variation in MiniMessage format")
        String displayName,

        @Setting("item")
        @Comment("Creation item settings for this variation (#38)")
        ItemSection item,

        @Setting("recipe")
        @Comment("Crafting recipe settings for this variation (#38)")
        RecipeSection recipe,

        @Setting("model")
        @Comment("Client-side visual model settings (BLOCK_DISPLAY)")
        ModelSection model,

        @Setting("hitbox")
        @Comment("Client-side INTERACTION entity hitbox dimensions in blocks (#40)")
        HitboxSection hitbox,

        @Setting("teleport")
        @Comment("Teleport destination spread and orientation settings (#41)")
        TeleportSection teleport,

        @Setting("placement")
        @Comment("Placement validation, clearance, and cost requirements (#15, #42)")
        PlacementSection placement,

        @Setting("limit")
        @Comment("Per-player placement limit for this variation (#43)")
        LimitSection limit,

        @Setting("warp")
        @Comment("Optional distance and cost overrides for warps originating from this variation (#14, #26)")
        WarpOverrideSection warp
) {

    /**
     * Maximum allowed horizontal radius for teleport landing search and placement clearance.
     */
    public static final int MAX_HORIZONTAL_RADIUS = 8;

    /**
     * Maximum allowed vertical height for placement clearance.
     */
    public static final int MAX_CLEARANCE_HEIGHT = 16;

    /**
     * CustomModelData component lists (#38).
     */
    @ConfigSerializable
    public record CustomModelDataSection(
            @Setting("floats")
            @Comment("Float values for minecraft:custom_model_data")
            List<Float> floats,

            @Setting("flags")
            @Comment("Boolean flags for minecraft:custom_model_data")
            List<Boolean> flags,

            @Setting("strings")
            @Comment("String values for minecraft:custom_model_data")
            List<String> strings,

            @Setting("colors")
            @Comment("RGB integer colors (e.g. 0x55FFFF) for minecraft:custom_model_data")
            List<Integer> colors
    ) {
        public CustomModelDataSection(
                @Nullable final List<Float> floats,
                @Nullable final List<Boolean> flags,
                @Nullable final List<String> strings,
                @Nullable final List<Integer> colors
        ) {
            this.floats = floats == null ? List.of() : List.copyOf(floats);
            this.flags = flags == null ? List.of() : List.copyOf(flags);
            this.strings = strings == null ? List.of() : List.copyOf(strings);
            this.colors = colors == null ? List.of() : List.copyOf(colors);
        }

        /**
         * Returns an empty {@link CustomModelDataSection}.
         */
        public static CustomModelDataSection empty() {
            return new CustomModelDataSection(List.of(), List.of(), List.of(), List.of());
        }

        /**
         * Returns whether all custom model data lists are empty.
         */
        public boolean isEmpty() {
            return floats.isEmpty() && flags.isEmpty() && strings.isEmpty() && colors.isEmpty();
        }

        /**
         * Converts integer RGB color values into Bukkit {@link Color} instances.
         */
        public List<Color> bukkitColors() {
            final List<Color> result = new ArrayList<>(colors.size());
            for (final Integer rgb : colors) {
                if (rgb != null) {
                    result.add(Color.fromRGB(rgb & 0xFFFFFF));
                }
            }
            return List.copyOf(result);
        }
    }

    /**
     * Creation item configuration section (#38).
     */
    @ConfigSerializable
    public record ItemSection(
            @Setting("material")
            @Comment("Base Bukkit Material for the creation item (vanilla item components are masked)")
            String material,

            @Setting("name")
            @Comment("Custom item display name in MiniMessage format (leave empty to use localized default)")
            String name,

            @Setting("lore")
            @Comment("Custom item lore lines in MiniMessage format (leave empty to use localized default)")
            List<String> lore,

            @Setting("item-model")
            @Comment("Optional resource pack item_model key (e.g. myserver:aetheryte)")
            String itemModel,

            @Setting("custom-model-data")
            @Comment("Optional minecraft:custom_model_data component values")
            CustomModelDataSection customModelData,

            @Setting("glint")
            @Comment("Whether to apply enchantment glint to the creation item")
            Boolean glint
    ) {
        public ItemSection(
                @Nullable final String material,
                @Nullable final String name,
                @Nullable final List<String> lore,
                @Nullable final String itemModel,
                @Nullable final CustomModelDataSection customModelData,
                @Nullable final Boolean glint
        ) {
            this.material = material == null ? Material.COMPASS.name() : material;
            this.name = name == null ? "" : name;
            this.lore = lore == null ? List.of() : List.copyOf(lore);
            this.itemModel = itemModel == null ? "" : itemModel;
            this.customModelData = customModelData == null ? CustomModelDataSection.empty() : customModelData;
            this.glint = glint == null ? true : glint;
        }

        /**
         * Returns the default creation item section.
         */
        public static ItemSection defaults() {
            return new ItemSection(
                    Material.COMPASS.name(),
                    "",
                    List.of(),
                    "",
                    CustomModelDataSection.empty(),
                    true
            );
        }

        /**
         * Returns the validated Bukkit {@link Material}.
         */
        public Material bukkitMaterial() {
            final Material parsed = parseMaterial(material);
            return parsed != null && !isAirMaterial(parsed) ? parsed : Material.COMPASS;
        }
    }

    /**
     * Crafting recipe configuration section (#38).
     */
    @ConfigSerializable
    public record RecipeSection(
            @Setting("enabled")
            @Comment("Whether this variation can be crafted")
            Boolean enabled,

            @Setting("shape")
            @Comment("3x3 crafting grid shape (1-3 rows, 1-3 characters per row)")
            List<String> shape,

            @Setting("ingredients")
            @Comment("Mapping of shape characters to Bukkit Material names")
            Map<String, String> ingredients
    ) {
        public RecipeSection(
                @Nullable final Boolean enabled,
                @Nullable final List<String> shape,
                @Nullable final Map<String, String> ingredients
        ) {
            this.enabled = enabled == null ? true : enabled;
            this.shape = shape == null ? List.of() : List.copyOf(shape);
            this.ingredients = ingredients == null ? Map.of() : Map.copyOf(ingredients);
        }

        /**
         * Returns a disabled crafting recipe section with no shape or ingredients.
         */
        public static RecipeSection disabled() {
            return new RecipeSection(false, List.of(), Map.of());
        }

        /**
         * Returns the default crafting recipe section.
         */
        public static RecipeSection defaults() {
            final Map<String, String> defaultIngredients = new LinkedHashMap<>();
            defaultIngredients.put("S", Material.CHISELED_STONE_BRICKS.name());
            defaultIngredients.put("A", Material.AMETHYST_SHARD.name());
            return new RecipeSection(
                    true,
                    List.of("SSS", "SAS", "SSS"),
                    Map.copyOf(defaultIngredients)
            );
        }

        /**
         * Returns the validated map of crafting ingredient characters to Bukkit {@link Material}s.
         */
        public Map<Character, Material> bukkitIngredients() {
            final Map<Character, Material> result = new LinkedHashMap<>();
            for (final Map.Entry<String, String> entry : ingredients.entrySet()) {
                final String key = entry.getKey();
                final Material mat = parseMaterial(entry.getValue());
                if (key != null && key.length() == 1 && mat != null && !isAirMaterial(mat)) {
                    result.put(key.charAt(0), mat);
                }
            }
            return Map.copyOf(result);
        }
    }

    /**
     * Client-side visual model section.
     */
    @ConfigSerializable
    public record ModelSection(
            @Setting("block")
            @Comment("Block state string displayed via BLOCK_DISPLAY (e.g. minecraft:lodestone)")
            String block,

            @Setting("scale")
            @Comment("3D scale vector [x, y, z] applied to the display entity")
            List<Double> scale,

            @Setting("translation")
            @Comment("3D translation offset [x, y, z] applied to the display entity")
            List<Double> translation,

            @Setting("name-height")
            @Comment("Vertical offset in blocks above the Aetheryte origin for the floating name hologram")
            Double nameHeight
    ) {
        public ModelSection(
                @Nullable final String block,
                @Nullable final List<Double> scale,
                @Nullable final List<Double> translation,
                @Nullable final Double nameHeight
        ) {
            this.block = block == null ? "minecraft:lodestone" : block;
            this.scale = scale == null ? List.of(1.0, 1.0, 1.0) : List.copyOf(scale);
            this.translation = translation == null ? List.of(0.0, 0.0, 0.0) : List.copyOf(translation);
            this.nameHeight = nameHeight == null ? 1.35 : nameHeight;
        }

        /**
         * Returns the default {@link ModelSection}.
         */
        public static ModelSection defaults() {
            return new ModelSection(
                    "minecraft:lodestone",
                    List.of(1.0, 1.0, 1.0),
                    List.of(0.0, 0.0, 0.0),
                    1.35
            );
        }
    }

    /**
     * Client-side {@code INTERACTION} hitbox dimensions (#40).
     */
    @ConfigSerializable
    public record HitboxSection(
            @Setting("width")
            @Comment("Width of the interaction hitbox in blocks (must be > 0, default 1.0)")
            Float width,

            @Setting("height")
            @Comment("Height of the interaction hitbox in blocks (must be > 0, default 1.0)")
            Float height
    ) {
        public HitboxSection(@Nullable final Float width, @Nullable final Float height) {
            this.width = width == null ? 1.0f : width;
            this.height = height == null ? 1.0f : height;
        }

        /**
         * Returns the default 1.0x1.0 hitbox section.
         */
        public static HitboxSection defaults() {
            return new HitboxSection(1.0f, 1.0f);
        }
    }

    /**
     * Teleport destination ring spread and orientation settings (#41).
     */
    @ConfigSerializable
    public record TeleportSection(
            @Setting("min-radius")
            @Comment("Minimum horizontal distance radius in blocks from the Aetheryte to land (inner ring radius, 0..max-radius)")
            Integer minRadius,

            @Setting("max-radius")
            @Comment("Maximum horizontal distance radius in blocks around the Aetheryte to search for a safe landing spot (outer ring radius, 0 = center/top, max 8)")
            Integer maxRadius,

            @Setting("face-aetheryte")
            @Comment("Whether to orient the arriving player to face toward the Aetheryte when landing in the surrounding ring")
            Boolean faceAetheryte
    ) {
        public TeleportSection(
                @Nullable final Integer minRadius,
                @Nullable final Integer maxRadius,
                @Nullable final Boolean faceAetheryte
        ) {
            this.minRadius = minRadius == null ? 0 : minRadius;
            this.maxRadius = maxRadius == null ? 0 : maxRadius;
            this.faceAetheryte = faceAetheryte == null ? true : faceAetheryte;
        }

        /**
         * Returns the default teleport section (min-radius 0, max-radius 0, face-aetheryte true).
         */
        public static TeleportSection defaults() {
            return new TeleportSection(0, 0, true);
        }
    }

    /**
     * Surrounding space clearance requirements when placing an Aetheryte (#42).
     */
    @ConfigSerializable
    public record ClearanceSection(
            @Setting("radius")
            @Comment("Horizontal radius in blocks around the origin that must be replaceable/empty (0 = origin column only, max 8)")
            Integer radius,

            @Setting("height")
            @Comment("Vertical height in blocks from the origin upward that must be replaceable/empty (1-16, default 2)")
            Integer height
    ) {
        public ClearanceSection(@Nullable final Integer radius, @Nullable final Integer height) {
            this.radius = radius == null ? 0 : radius;
            this.height = height == null ? 2 : height;
        }

        /**
         * Returns the default clearance section.
         */
        public static ClearanceSection defaults() {
            return new ClearanceSection(0, 2);
        }
    }

    /**
     * Placement requirements section (#15, #42).
     */
    @ConfigSerializable
    public record PlacementSection(
            @Setting("clearance")
            @Comment("Surrounding space clearance required to place this Aetheryte variation")
            ClearanceSection clearance,

            @Setting("cost")
            @Comment("Placement cost specifications for this variation (#15)")
            List<CostEntry> cost
    ) {
        public PlacementSection(
                @Nullable final ClearanceSection clearance,
                @Nullable final List<CostEntry> cost
        ) {
            this.clearance = clearance == null ? ClearanceSection.defaults() : clearance;
            this.cost = cost == null ? List.of() : List.copyOf(cost);
        }

        public PlacementSection(@Nullable final ClearanceSection clearance) {
            this(clearance, List.of());
        }

        /**
         * Returns the default placement section.
         */
        public static PlacementSection defaults() {
            return new PlacementSection(ClearanceSection.defaults(), List.of());
        }
    }

    /**
     * Warp distance and cost override section for this variation (#14, #26).
     */
    @ConfigSerializable
    public record WarpOverrideSection(
            @Setting("distance")
            @Comment("Optional distance overrides for warps originating from this variation (#26)")
            DistanceSection distance,

            @Setting("cost")
            @Comment("Optional cost overrides for warps originating from this variation (null to inherit global, [] for free) (#14)")
            @Nullable List<CostEntry> cost
    ) {
        public WarpOverrideSection(
                @Nullable final DistanceSection distance,
                @Nullable final List<CostEntry> cost
        ) {
            this.distance = distance == null ? DistanceSection.inherit() : distance;
            this.cost = cost == null ? null : List.copyOf(cost);
        }

        public static WarpOverrideSection defaults() {
            return new WarpOverrideSection(DistanceSection.inherit(), null);
        }
    }

    /**
     * Per-player placement limit and cooldown section (#43).
     */
    @ConfigSerializable
    public record LimitSection(
            @Setting("max-per-player")
            @Comment("Maximum number of Aetherytes of this variation a single player may own (-1 for unlimited)")
            Integer maxPerPlayer,

            @Setting("cooldown-ticks")
            @Comment("Cooldown in ticks after placing this variation (-1 to use global limits.placement-cooldown-ticks, 0 to disable)")
            Integer cooldownTicks
    ) {
        public LimitSection(@Nullable final Integer maxPerPlayer, @Nullable final Integer cooldownTicks) {
            this.maxPerPlayer = maxPerPlayer == null ? -1 : maxPerPlayer;
            this.cooldownTicks = cooldownTicks == null ? -1 : cooldownTicks;
        }

        /**
         * Returns the default limit section (-1 = unlimited count, -1 = inherit global cooldown).
         */
        public static LimitSection defaults() {
            return new LimitSection(-1, -1);
        }
    }

    /**
     * Constructor ensuring non-null sections when deserialized by Configurate.
     */
    public AetheryteVariation(
            @Nullable final String displayName,
            @Nullable final ItemSection item,
            @Nullable final RecipeSection recipe,
            @Nullable final ModelSection model,
            @Nullable final HitboxSection hitbox,
            @Nullable final TeleportSection teleport,
            @Nullable final PlacementSection placement,
            @Nullable final LimitSection limit
    ) {
        this(displayName, item, recipe, model, hitbox, teleport, placement, limit, null);
    }

    /**
     * Constructor ensuring non-null sections when deserialized by Configurate.
     */
    public AetheryteVariation(
            @Nullable final String displayName,
            @Nullable final ItemSection item,
            @Nullable final RecipeSection recipe,
            @Nullable final ModelSection model,
            @Nullable final HitboxSection hitbox,
            @Nullable final TeleportSection teleport,
            @Nullable final PlacementSection placement,
            @Nullable final LimitSection limit,
            @Nullable final WarpOverrideSection warp
    ) {
        this.displayName = (displayName == null || displayName.isBlank()) ? "<aqua>Aetheryte</aqua>" : displayName;
        this.item = item == null ? ItemSection.defaults() : item;
        this.recipe = recipe == null ? RecipeSection.disabled() : recipe;
        this.model = model == null ? ModelSection.defaults() : model;
        this.hitbox = hitbox == null ? HitboxSection.defaults() : hitbox;
        this.teleport = teleport == null ? TeleportSection.defaults() : teleport;
        this.placement = placement == null ? PlacementSection.defaults() : placement;
        this.limit = limit == null ? LimitSection.defaults() : limit;
        this.warp = warp == null ? WarpOverrideSection.defaults() : warp;
    }

    /**
     * Returns the default {@link AetheryteVariation} matching {@code default.yml}.
     */
    public static AetheryteVariation defaultVariation() {
        return new AetheryteVariation(
                "<aqua>Aetheryte</aqua>",
                ItemSection.defaults(),
                RecipeSection.defaults(),
                ModelSection.defaults(),
                HitboxSection.defaults(),
                TeleportSection.defaults(),
                PlacementSection.defaults(),
                LimitSection.defaults(),
                WarpOverrideSection.defaults()
        );
    }

    /**
     * Validates and normalizes all fields of this variation, logging warnings in English and falling back
     * to safe defaults for invalid values.
     */
    public AetheryteVariation validated(final String variationId, final Logger logger) {
        final String cleanId = Aetheryte.normalizeVariation(variationId);
        final String validDisplayName = (displayName == null || displayName.isBlank())
                ? "<aqua>Aetheryte</aqua>"
                : displayName.strip();

        // 1. Validate item section (#38)
        final ItemSection defaultItem = ItemSection.defaults();
        final Material itemMat = parseMaterial(item.material());
        final String validItemMat;
        if (itemMat == null || isAirMaterial(itemMat)) {
            logger.warn("Unknown or invalid item.material '{}' in variation '{}'; falling back to {}",
                    item.material(), cleanId, defaultItem.material());
            validItemMat = defaultItem.material();
        } else {
            validItemMat = itemMat.name();
        }
        final ItemSection validatedItem = new ItemSection(
                validItemMat,
                item.name() == null ? "" : item.name(),
                item.lore() == null ? List.of() : List.copyOf(item.lore()),
                item.itemModel() == null ? "" : item.itemModel().strip(),
                item.customModelData(),
                item.glint()
        );

        // 2. Validate recipe section (#38)
        final RecipeSection defaultRecipe = RecipeSection.defaults();
        final List<String> rawShape = recipe.shape();
        final List<String> shape = new ArrayList<>();
        if (rawShape.isEmpty() || rawShape.size() > 3) {
            shape.addAll(defaultRecipe.shape());
        } else {
            for (final String row : rawShape) {
                if (row != null && !row.isEmpty() && row.length() <= 3) {
                    shape.add(row);
                }
            }
            if (shape.isEmpty()) {
                shape.addAll(defaultRecipe.shape());
            }
        }

        final Map<Character, Material> parsedIngredients = new LinkedHashMap<>();
        final Map<String, String> normalizedIngredientStrings = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : recipe.ingredients().entrySet()) {
            final String key = entry.getKey();
            final String matName = entry.getValue();
            if (key == null || key.length() != 1) {
                logger.warn("Ignoring invalid recipe ingredient key '{}' in variation '{}' (must be 1 character)",
                        key, cleanId);
                continue;
            }
            final Material material = parseMaterial(matName);
            if (material == null || isAirMaterial(material)) {
                logger.warn("Unknown material '{}' for recipe ingredient '{}' in variation '{}'",
                        matName, key, cleanId);
                continue;
            }
            parsedIngredients.put(key.charAt(0), material);
            normalizedIngredientStrings.put(key, material.name());
        }

        boolean recipeEnabled = Boolean.TRUE.equals(recipe.enabled());
        if (recipeEnabled && (parsedIngredients.isEmpty() || !isValidRecipeCombination(shape, parsedIngredients))) {
            if (!Aetheryte.DEFAULT_VARIATION.equals(cleanId)) {
                if (!recipe.ingredients().isEmpty() || !rawShape.isEmpty()) {
                    logger.warn("Invalid recipe shape/ingredients in custom variation '{}'; disabling crafting recipe for this variation",
                            cleanId);
                }
                recipeEnabled = false;
                shape.clear();
                normalizedIngredientStrings.clear();
            } else {
                logger.warn("Invalid recipe in default variation; falling back to default recipe");
                shape.clear();
                shape.addAll(defaultRecipe.shape());
                normalizedIngredientStrings.clear();
                normalizedIngredientStrings.putAll(defaultRecipe.ingredients());
            }
        }
        final RecipeSection validatedRecipe = new RecipeSection(
                recipeEnabled,
                List.copyOf(shape),
                Map.copyOf(normalizedIngredientStrings)
        );

        // 3. Validate model section
        final ModelSection defaultModel = ModelSection.defaults();
        final String validBlock = (model.block() == null || model.block().isBlank())
                ? defaultModel.block()
                : model.block().strip();
        final List<Double> validScale = normalizeVector3(model.scale(), 1.0, "model.scale", cleanId, logger);
        final List<Double> validTranslation = normalizeVector3(model.translation(), 0.0, "model.translation", cleanId, logger);
        final double rawNameHeight = model.nameHeight();
        final double validNameHeight = Double.isFinite(rawNameHeight) ? rawNameHeight : 1.35;
        final ModelSection validatedModel = new ModelSection(
                validBlock,
                validScale,
                validTranslation,
                validNameHeight
        );

        // 4. Validate hitbox section (#40)
        final float rawWidth = hitbox.width();
        float validWidth = rawWidth;
        if (!Float.isFinite(validWidth) || validWidth <= 0.0f) {
            logger.warn("Invalid hitbox.width {} in variation '{}' (must be > 0); falling back to 1.0",
                    rawWidth, cleanId);
            validWidth = 1.0f;
        }
        final float rawHeight = hitbox.height();
        float validHeight = rawHeight;
        if (!Float.isFinite(validHeight) || validHeight <= 0.0f) {
            logger.warn("Invalid hitbox.height {} in variation '{}' (must be > 0); falling back to 1.0",
                    rawHeight, cleanId);
            validHeight = 1.0f;
        }
        final HitboxSection validatedHitbox = new HitboxSection(validWidth, validHeight);

        // 5. Validate teleport section (#41)
        final int rawMaxRadius = teleport.maxRadius();
        int validMaxRadius = rawMaxRadius;
        if (validMaxRadius < 0 || validMaxRadius > MAX_HORIZONTAL_RADIUS) {
            final int clamped = Math.clamp(validMaxRadius, 0, MAX_HORIZONTAL_RADIUS);
            logger.warn("Clamping teleport.max-radius {} to {} in variation '{}' (allowed range: 0..{})",
                    validMaxRadius, clamped, cleanId, MAX_HORIZONTAL_RADIUS);
            validMaxRadius = clamped;
        }
        final int rawMinRadius = teleport.minRadius();
        int validMinRadius = rawMinRadius;
        if (validMinRadius < 0 || validMinRadius > validMaxRadius) {
            final int clamped = Math.clamp(validMinRadius, 0, validMaxRadius);
            logger.warn("Clamping teleport.min-radius {} to {} in variation '{}' (allowed range: 0..{})",
                    validMinRadius, clamped, cleanId, validMaxRadius);
            validMinRadius = clamped;
        }
        final boolean validFace = teleport.faceAetheryte();
        final TeleportSection validatedTeleport = new TeleportSection(
                validMinRadius,
                validMaxRadius,
                validFace
        );

        // 6. Validate placement clearance section (#42)
        final ClearanceSection rawClearance = placement.clearance();
        final int rawClearanceRadius = rawClearance.radius();
        int validClearanceRadius = rawClearanceRadius;
        if (validClearanceRadius < 0 || validClearanceRadius > MAX_HORIZONTAL_RADIUS) {
            final int clamped = Math.clamp(validClearanceRadius, 0, MAX_HORIZONTAL_RADIUS);
            logger.warn("Clamping placement.clearance.radius {} to {} in variation '{}' (allowed range: 0..{})",
                    validClearanceRadius, clamped, cleanId, MAX_HORIZONTAL_RADIUS);
            validClearanceRadius = clamped;
        }
        final int rawClearanceHeight = rawClearance.height();
        int validClearanceHeight = rawClearanceHeight;
        if (validClearanceHeight < 1 || validClearanceHeight > MAX_CLEARANCE_HEIGHT) {
            final int clamped = Math.clamp(validClearanceHeight, 1, MAX_CLEARANCE_HEIGHT);
            logger.warn("Clamping placement.clearance.height {} to {} in variation '{}' (allowed range: 1..{})",
                    validClearanceHeight, clamped, cleanId, MAX_CLEARANCE_HEIGHT);
            validClearanceHeight = clamped;
        }
        if (placement.cost() != null) {
            for (final CostEntry entry : placement.cost()) {
                if (entry.type() == null) {
                    logger.warn("Unknown cost type '{}' in placement.cost of variation '{}'", entry.rawType(), cleanId);
                } else {
                    try {
                        ExpressionParser.parse(entry.amount(), entry.type().resourceVariable());
                    } catch (final ExpressionException e) {
                        logger.warn("Invalid placement cost expression '{}' in variation '{}': {}", entry.amount(), cleanId, e.getMessage());
                    }
                }
            }
        }
        final PlacementSection validatedPlacement = new PlacementSection(
                new ClearanceSection(validClearanceRadius, validClearanceHeight),
                placement.cost()
        );

        // 7. Validate limit section (#43)
        final int rawMaxPerPlayer = limit.maxPerPlayer();
        final int validMaxPerPlayer = rawMaxPerPlayer < -1 ? -1 : rawMaxPerPlayer;
        final int rawCooldownTicks = limit.cooldownTicks();
        final int validCooldownTicks = rawCooldownTicks < -1 ? -1 : rawCooldownTicks;
        final LimitSection validatedLimit = new LimitSection(validMaxPerPlayer, validCooldownTicks);

        // 8. Validate warp override section (#14, #26)
        final DistanceSection rawWarpDist = warp.distance();
        final double validWarpMin = rawWarpDist.min() == null || rawWarpDist.min() < -1.0 ? -1.0 : rawWarpDist.min();
        double validWarpMax = rawWarpDist.max() == null || rawWarpDist.max() < -1.0 ? -1.0 : rawWarpDist.max();
        if (validWarpMin >= 0.0 && validWarpMax >= 0.0 && validWarpMin > validWarpMax) {
            logger.warn("warp.distance.min ({}) is greater than warp.distance.max ({}) in variation '{}'. Disabling max distance (-1.0) to prevent deadlocks.",
                    validWarpMin, validWarpMax, cleanId);
            validWarpMax = -1.0;
        }
        final DistanceSection validatedWarpDist = new DistanceSection(validWarpMin, validWarpMax);

        if (warp.cost() != null) {
            for (final CostEntry entry : warp.cost()) {
                if (entry.type() == null) {
                    logger.warn("Unknown cost type '{}' in warp.cost of variation '{}'", entry.rawType(), cleanId);
                } else {
                    try {
                        ExpressionParser.parse(entry.amount(), entry.type().resourceVariable());
                    } catch (final ExpressionException e) {
                        logger.warn("Invalid warp cost expression '{}' in variation '{}': {}", entry.amount(), cleanId, e.getMessage());
                    }
                }
            }
        }
        final WarpOverrideSection validatedWarp = new WarpOverrideSection(validatedWarpDist, warp.cost());

        return new AetheryteVariation(
                validDisplayName,
                validatedItem,
                validatedRecipe,
                validatedModel,
                validatedHitbox,
                validatedTeleport,
                validatedPlacement,
                validatedLimit,
                validatedWarp
        );
    }

    private static List<Double> normalizeVector3(
            final List<Double> raw,
            final double defaultVal,
            final String fieldName,
            final String variationId,
            final Logger logger
    ) {
        if (raw == null || raw.size() != 3) {
            logger.warn("Invalid {} in variation '{}' (expected 3 numbers); falling back to [{}, {}, {}]",
                    fieldName, variationId, defaultVal, defaultVal, defaultVal);
            return List.of(defaultVal, defaultVal, defaultVal);
        }
        final List<Double> normalized = new ArrayList<>(3);
        for (final Double val : raw) {
            if (val == null || !Double.isFinite(val)) {
                normalized.add(defaultVal);
            } else {
                normalized.add(val);
            }
        }
        return List.copyOf(normalized);
    }

    /**
     * Checks whether the given material is one of the air material variants without initializing the Paper registry.
     */
    public static boolean isAirMaterial(final Material material) {
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR;
    }

    /**
     * Parses a Bukkit {@link Material} by enum name (supporting optional {@code minecraft:} prefix).
     */
    public static @Nullable Material parseMaterial(@Nullable final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.strip().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        try {
            return Material.valueOf(normalized);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Checks that the shape rows are rectangular and that shape symbols and ingredient keys match.
     */
    public static boolean isValidRecipeCombination(
            final List<String> shape,
            final Map<Character, Material> ingredients
    ) {
        if (shape.isEmpty() || ingredients.isEmpty()) {
            return false;
        }
        final boolean rectangular = shape.stream().mapToInt(String::length).distinct().count() == 1;
        if (!rectangular) {
            return false;
        }
        final boolean allIngredientKeysInShape = ingredients.keySet().stream()
                .allMatch(key -> shape.stream().anyMatch(row -> row.indexOf(key) >= 0));
        if (!allIngredientKeysInShape) {
            return false;
        }
        for (final String row : shape) {
            for (int i = 0; i < row.length(); i++) {
                final char symbol = row.charAt(i);
                if (symbol != ' ' && !ingredients.containsKey(symbol)) {
                    return false;
                }
            }
        }
        return true;
    }
}
