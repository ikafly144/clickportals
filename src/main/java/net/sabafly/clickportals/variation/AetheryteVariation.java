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
import net.sabafly.clickportals.config.CastSection;
import net.sabafly.clickportals.config.DistanceSection;
import net.sabafly.clickportals.config.PetsWarpSection;
import net.sabafly.clickportals.config.VisibilityCostSection;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.expr.ExpressionException;
import net.sabafly.clickportals.cost.expr.ExpressionParser;
import net.sabafly.clickportals.sound.SoundsConfig.SoundsSection;
import org.bukkit.Color;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

/**
 * Type-safe Configurate model representing a single Aetheryte variation loaded from
 * {@code <dataDir>/aetheryte/<type>.yml} (#27, #36, #37, #38, #39, #40, #41, #42, #43).
 */
@ConfigSerializable
public record AetheryteVariation(
        @Setting("display-name")
        @Comment("Display name of this Aetheryte variation in MiniMessage format")
        String displayName,

        @Setting("type")
        @Comment("Variation type: 'normal' (standard) or 'network' (player-owned world network) (#27)")
        VariationType type,

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
        WarpOverrideSection warp,

        @Setting("network")
        @Comment("Network-type Aetheryte limits and costs (#27)")
        NetworkSection network,

        @Setting("bettermodel")
        @Comment("BetterModel animated 3D model engine settings (#46)")
        BetterModelSection bettermodel,

        @Setting("sounds")
        @Comment("Optional per-variation event and ambient sound overrides (null fields inherit global config.yml) (#77)")
        SoundsSection sounds
) {

    /**
     * Minimum allowed horizontal radius for teleport landing search (#62).
     */
    public static final int MIN_HORIZONTAL_RADIUS = 1;

    /**
     * Maximum allowed horizontal radius for teleport landing search and placement clearance.
     */
    public static final int MAX_HORIZONTAL_RADIUS = 8;

    /**
     * Categorizes a variation as standard ({@link #NORMAL}) or player-network ({@link #NETWORK}) (#27).
     */
    public enum VariationType {
        NORMAL("normal"),
        NETWORK("network");

        private final String id;

        VariationType(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static VariationType fromString(final @Nullable String raw) {
            if (raw == null || raw.isBlank()) {
                return NORMAL;
            }
            return "network".equalsIgnoreCase(raw.strip()) ? NETWORK : NORMAL;
        }
    }

    /**
     * Serializer for {@link VariationType} emitting lowercase YAML identifiers ({@code normal} / {@code network}).
     */
    public static final class VariationTypeSerializer implements TypeSerializer<VariationType> {
        public static final VariationTypeSerializer INSTANCE = new VariationTypeSerializer();

        @Override
        public VariationType deserialize(final java.lang.reflect.Type type, final ConfigurationNode node) {
            if (node == null || node.virtual() || node.raw() == null) {
                return VariationType.NORMAL;
            }
            return VariationType.fromString(node.getString());
        }

        @Override
        public void serialize(
                final java.lang.reflect.Type type,
                final @Nullable VariationType obj,
                final ConfigurationNode node
        ) throws SerializationException {
            node.set((obj != null ? obj : VariationType.NORMAL).id());
        }
    }

    /**
     * Returns whether this variation is a {@code type: network} variation (#27).
     */
    public boolean isNetwork() {
        return type == VariationType.NETWORK;
    }

    /**
     * Returns whether a {@code type: normal} Aetheryte of this variation may host player networks (#27).
     */
    public boolean canBeNetworkHub() {
        return !isNetwork() && network.allowHubOrDefault();
    }

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
     * Teleport destination ring spread and orientation settings (#41, #62).
     */
    @ConfigSerializable
    public record TeleportSection(
            @Setting("min-radius")
            @Comment("Minimum horizontal distance radius in blocks from the Aetheryte to land (inner ring radius, 1..max-radius)")
            Integer minRadius,

            @Setting("max-radius")
            @Comment("Maximum horizontal distance radius in blocks around the Aetheryte to search for a safe landing spot (outer ring radius, 1..8)")
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
            this.minRadius = minRadius == null ? MIN_HORIZONTAL_RADIUS : minRadius;
            this.maxRadius = maxRadius == null ? MIN_HORIZONTAL_RADIUS : maxRadius;
            this.faceAetheryte = faceAetheryte == null ? true : faceAetheryte;
        }

        /**
         * Returns the default teleport section (min-radius 1, max-radius 1, face-aetheryte true).
         */
        public static TeleportSection defaults() {
            return new TeleportSection(MIN_HORIZONTAL_RADIUS, MIN_HORIZONTAL_RADIUS, true);
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
            List<CostEntry> cost,

            @Setting("min-distance")
            @Comment("Minimum block distance to another Aetheryte in the same world (-1 to inherit global, 0 to disable)")
            @Nullable Double minDistance,

            @Setting("visibility-cost")
            @Comment("Optional per-variation visibility cost multiplier overrides (null fields inherit global config.yml) (#25)")
            VisibilityCostSection visibilityCost
    ) {
        public PlacementSection(
                @Nullable final ClearanceSection clearance,
                @Nullable final List<CostEntry> cost,
                @Nullable final Double minDistance,
                @Nullable final VisibilityCostSection visibilityCost
        ) {
            this.clearance = clearance == null ? ClearanceSection.defaults() : clearance;
            this.cost = cost == null ? List.of() : List.copyOf(cost);
            this.minDistance = minDistance;
            this.visibilityCost = visibilityCost == null ? VisibilityCostSection.inherit() : visibilityCost;
        }

        public PlacementSection(
                @Nullable final ClearanceSection clearance,
                @Nullable final List<CostEntry> cost,
                @Nullable final Double minDistance
        ) {
            this(clearance, cost, minDistance, VisibilityCostSection.inherit());
        }

        public PlacementSection(
                @Nullable final ClearanceSection clearance,
                @Nullable final List<CostEntry> cost
        ) {
            this(clearance, cost, -1.0, VisibilityCostSection.inherit());
        }

        public PlacementSection(@Nullable final ClearanceSection clearance) {
            this(clearance, List.of(), -1.0, VisibilityCostSection.inherit());
        }

        public double minDistanceOrDefault(final double fallback) {
            return (minDistance != null && minDistance >= 0.0) ? minDistance : fallback;
        }

        /**
         * Returns the default placement section.
         */
        public static PlacementSection defaults() {
            return new PlacementSection(ClearanceSection.defaults(), List.of(), -1.0, VisibilityCostSection.inherit());
        }
    }

    /**
     * Warp distance, cost, cast, and visibility cost overrides for this variation (#14, #25, #26).
     */
    @ConfigSerializable
    public record WarpOverrideSection(
            @Setting("distance")
            @Comment("Optional distance overrides for warps originating from this variation (#26)")
            DistanceSection distance,

            @Setting("cost")
            @Comment("Optional cost overrides for warps originating from this variation (null to inherit global, [] for free) (#14)")
            @Nullable List<CostEntry> cost,

            @Setting("cast")
            @Comment("Optional warp casting (cast time and player animation) overrides for warps originating from this variation (#65)")
            CastSection cast,

            @Setting("visibility-cost")
            @Comment("Cost overrides per destination visibility mode (null = use warp.cost/global, [] = free) (#25)")
            VisibilityCostSection visibilityCost,

            @Setting("pets")
            @Comment("Optional leashed entity warping overrides for warps originating from this variation (#22)")
            PetsWarpSection pets
    ) {
        public WarpOverrideSection(
                @Nullable final DistanceSection distance,
                @Nullable final List<CostEntry> cost
        ) {
            this(distance, cost, CastSection.inherit(), VisibilityCostSection.inherit(), PetsWarpSection.inherit());
        }

        public WarpOverrideSection(
                @Nullable final DistanceSection distance,
                @Nullable final List<CostEntry> cost,
                @Nullable final CastSection cast
        ) {
            this(distance, cost, cast, VisibilityCostSection.inherit(), PetsWarpSection.inherit());
        }

        public WarpOverrideSection(
                @Nullable final DistanceSection distance,
                @Nullable final List<CostEntry> cost,
                @Nullable final CastSection cast,
                @Nullable final VisibilityCostSection visibilityCost
        ) {
            this(distance, cost, cast, visibilityCost, PetsWarpSection.inherit());
        }

        public WarpOverrideSection(
                @Nullable final DistanceSection distance,
                @Nullable final List<CostEntry> cost,
                @Nullable final CastSection cast,
                @Nullable final VisibilityCostSection visibilityCost,
                @Nullable final PetsWarpSection pets
        ) {
            this.distance = distance == null ? DistanceSection.inherit() : distance;
            this.cost = cost == null ? null : List.copyOf(cost);
            this.cast = cast == null ? CastSection.inherit() : cast;
            this.visibilityCost = visibilityCost == null ? VisibilityCostSection.inherit() : visibilityCost;
            this.pets = pets == null ? PetsWarpSection.inherit() : pets;
        }

        public static WarpOverrideSection defaults() {
            return new WarpOverrideSection(
                    DistanceSection.inherit(),
                    null,
                    CastSection.inherit(),
                    VisibilityCostSection.inherit(),
                    PetsWarpSection.inherit()
            );
        }
    }

    /**
     * Custom {@link TypeSerializer} for {@link WarpOverrideSection} ensuring {@code cost: null} and per-visibility
     * overrides are preserved cleanly (#14, #25, #26).
     */
    public static final class WarpOverrideSectionSerializer implements TypeSerializer<WarpOverrideSection> {
        public static final WarpOverrideSectionSerializer INSTANCE = new WarpOverrideSectionSerializer();

        @Override
        public WarpOverrideSection deserialize(final java.lang.reflect.Type type, final ConfigurationNode node)
                throws SerializationException {
            final DistanceSection distance = node.node("distance").get(DistanceSection.class, DistanceSection.inherit());
            final ConfigurationNode costNode = node.node("cost");
            final List<CostEntry> cost;
            if (costNode.isNull() || costNode.raw() == null) {
                cost = null;
            } else if (costNode.isList()) {
                cost = costNode.getList(CostEntry.class);
            } else {
                cost = null;
            }
            final ConfigurationNode castNode = node.node("cast");
            final CastSection cast = (castNode.virtual() || castNode.isNull() || castNode.raw() == null)
                    ? CastSection.inherit()
                    : castNode.get(CastSection.class, CastSection.inherit());
            final ConfigurationNode visCostNode = node.node("visibility-cost");
            final VisibilityCostSection visibilityCost = (visCostNode.virtual() || visCostNode.isNull() || visCostNode.raw() == null)
                    ? VisibilityCostSection.inherit()
                    : VisibilityCostSection.Serializer.INSTANCE.deserialize(VisibilityCostSection.class, visCostNode);
            final ConfigurationNode petsNode = node.node("pets");
            final PetsWarpSection pets = (petsNode.virtual() || petsNode.isNull() || petsNode.raw() == null)
                    ? PetsWarpSection.inherit()
                    : PetsWarpSection.Serializer.INSTANCE.deserialize(PetsWarpSection.class, petsNode);
            return new WarpOverrideSection(distance, cost, cast, visibilityCost, pets);
        }

        @Override
        public void serialize(
                final java.lang.reflect.Type type,
                final @Nullable WarpOverrideSection obj,
                final ConfigurationNode node
        ) throws SerializationException {
            if (obj == null) {
                node.raw(null);
                return;
            }
            node.node("distance").set(DistanceSection.class, obj.distance());
            if (obj.cost() == null) {
                node.node("cost").raw(null);
            } else {
                node.node("cost").setList(CostEntry.class, obj.cost());
            }
            node.node("cast").set(CastSection.class, obj.cast());
            VisibilityCostSection.Serializer.INSTANCE.serialize(
                    VisibilityCostSection.class,
                    obj.visibilityCost(),
                    node.node("visibility-cost")
            );
            PetsWarpSection.Serializer.INSTANCE.serialize(
                    PetsWarpSection.class,
                    obj.pets(),
                    node.node("pets")
            );
        }
    }

    /**
     * Network limits, hub capability, and costs applied to {@code type: network} and Hub variations (#27).
     */
    @ConfigSerializable
    public record NetworkSection(
            @Setting("limit")
            @Comment("Per-player and per-network limits for network-type variations (#27)")
            NetworkLimitSection limit,

            @Setting("cost")
            @Comment("Additional costs charged when creating or joining a network (#27)")
            NetworkCostSection cost,

            @Setting("allow-hub")
            @Comment("Whether 'normal' Aetherytes using this variation may act as a network Hub (#27)")
            @Nullable Boolean allowHub
    ) {
        public NetworkSection(final NetworkLimitSection limit, final NetworkCostSection cost) {
            this(limit, cost, Boolean.TRUE);
        }

        public NetworkSection {
            limit = limit == null ? NetworkLimitSection.defaults() : limit;
            cost = cost == null ? NetworkCostSection.defaults() : cost;
            allowHub = allowHub == null ? Boolean.TRUE : allowHub;
        }

        public boolean allowHubOrDefault() {
            return !Boolean.FALSE.equals(allowHub);
        }

        public static NetworkSection defaults() {
            return new NetworkSection(NetworkLimitSection.defaults(), NetworkCostSection.defaults(), Boolean.TRUE);
        }
    }

    /**
     * Per-player / per-network limits for {@code type: network} variations (#27).
     */
    @ConfigSerializable
    public record NetworkLimitSection(
            @Setting("max-per-player")
            @Comment("Maximum networks of this variation a single player may own (-1 for unlimited)")
            @Nullable Integer maxPerPlayer,

            @Setting("max-aetheryte")
            @Comment("Maximum Aetherytes allowed in a single network (-1 for unlimited)")
            @Nullable Integer maxAetheryte,

            @Setting("max-distance")
            @Comment("Maximum block distance from an existing network Aetheryte when adding a new one (-1.0 to disable)")
            @Nullable Double maxDistance
    ) {
        public NetworkLimitSection {
            if (maxPerPlayer == null) {
                maxPerPlayer = -1;
            }
            if (maxAetheryte == null) {
                maxAetheryte = -1;
            }
            if (maxDistance == null) {
                maxDistance = -1.0;
            }
        }

        public int maxPerPlayerOrDefault() {
            return maxPerPlayer != null ? maxPerPlayer : -1;
        }

        public int maxAetheryteOrDefault() {
            return maxAetheryte != null ? maxAetheryte : -1;
        }

        public double maxDistanceOrDefault() {
            return maxDistance != null ? maxDistance : -1.0;
        }

        public static NetworkLimitSection defaults() {
            return new NetworkLimitSection(-1, -1, -1.0);
        }
    }

    /**
     * Extra creation/join cost lists applied to {@code type: network} variations (#27).
     */
    @ConfigSerializable
    public record NetworkCostSection(
            @Setting("create")
            @Comment("Additional cost charged when creating a new network (empty = free)")
            List<CostEntry> create,

            @Setting("join")
            @Comment("Additional cost charged when joining an existing network (empty = free)")
            List<CostEntry> join
    ) {
        public NetworkCostSection {
            create = create == null ? List.of() : List.copyOf(create);
            join = join == null ? List.of() : List.copyOf(join);
        }

        public static NetworkCostSection defaults() {
            return new NetworkCostSection(List.of(), List.of());
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
     * Animation trigger mapping for BetterModel (#46).
     */
    @ConfigSerializable
    public record AnimationSection(
            @Setting("idle")
            @Comment("Looping idle animation name")
            String idle,

            @Setting("on-interact")
            @Comment("Animation played when a player interacts with the Aetheryte")
            String onInteract,

            @Setting("on-warp-depart")
            @Comment("Animation played when a player warps away from this Aetheryte")
            String onWarpDepart,

            @Setting("on-warp-arrive")
            @Comment("Animation played when a player arrives at this Aetheryte")
            String onWarpArrive
    ) {
        /**
         * Creates an {@link AnimationSection} with null-safe default values.
         *
         * @param idle looping idle animation name
         * @param onInteract animation played on interaction
         * @param onWarpDepart animation played on warp departure
         * @param onWarpArrive animation played on warp arrival
         */
        public AnimationSection(
                @Nullable final String idle,
                @Nullable final String onInteract,
                @Nullable final String onWarpDepart,
                @Nullable final String onWarpArrive
        ) {
            this.idle = (idle == null || idle.isBlank()) ? "idle" : idle;
            this.onInteract = onInteract == null ? "" : onInteract;
            this.onWarpDepart = onWarpDepart == null ? "" : onWarpDepart;
            this.onWarpArrive = onWarpArrive == null ? "" : onWarpArrive;
        }

        /**
         * Returns an {@link AnimationSection} populated with default idle animation ("idle").
         *
         * @return default animation configuration
         */
        public static AnimationSection defaults() {
            return new AnimationSection("idle", "", "", "");
        }
    }

    /**
     * BetterModel animated 3D model engine configuration section (#46).
     */
    @ConfigSerializable
    public record BetterModelSection(
            @Setting("enabled")
            @Comment("Whether to render this variation using BetterModel")
            Boolean enabled,

            @Setting("model-id")
            @Comment("Model identifier registered in BetterModel")
            String modelId,

            @Setting("animations")
            @Comment("Event-driven animations")
            AnimationSection animations
    ) {
        /**
         * Creates a {@link BetterModelSection} with null-safe default values.
         *
         * @param enabled whether BetterModel rendering is enabled
         * @param modelId model identifier registered in BetterModel
         * @param animations event-driven animation mappings
         */
        public BetterModelSection(
                @Nullable final Boolean enabled,
                @Nullable final String modelId,
                @Nullable final AnimationSection animations
        ) {
            this.enabled = enabled == null ? false : enabled;
            this.modelId = modelId == null ? "" : modelId;
            this.animations = animations == null ? AnimationSection.defaults() : animations;
        }

        /**
         * Returns a disabled {@link BetterModelSection}.
         *
         * @return disabled BetterModel configuration
         */
        public static BetterModelSection disabled() {
            return new BetterModelSection(false, "", AnimationSection.defaults());
        }

        /**
         * Returns default {@link BetterModelSection} settings (disabled by default).
         *
         * @return default BetterModel configuration
         */
        public static BetterModelSection defaults() {
            return disabled();
        }
    }

    /**
     * Convenience constructor preserving backward compatibility for 8-argument invocations.
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
        this(
                displayName,
                item,
                recipe,
                model,
                hitbox,
                teleport,
                placement,
                limit,
                null,
                BetterModelSection.disabled(),
                SoundsSection.inherit()
        );
    }

    /**
     * Convenience constructor preserving backward compatibility for BetterModel invocations without warp.
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
            @Nullable final BetterModelSection bettermodel
    ) {
        this(
                displayName,
                item,
                recipe,
                model,
                hitbox,
                teleport,
                placement,
                limit,
                null,
                bettermodel,
                SoundsSection.inherit()
        );
    }

    /**
     * Convenience constructor preserving backward compatibility for warp invocations without BetterModel.
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
        this(
                displayName,
                VariationType.NORMAL,
                item,
                recipe,
                model,
                hitbox,
                teleport,
                placement,
                limit,
                warp,
                NetworkSection.defaults(),
                BetterModelSection.disabled(),
                SoundsSection.inherit()
        );
    }

    /**
     * Convenience constructor preserving backward compatibility for 10-argument invocations without sounds (#77).
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
            @Nullable final WarpOverrideSection warp,
            @Nullable final BetterModelSection bettermodel
    ) {
        this(
                displayName,
                VariationType.NORMAL,
                item,
                recipe,
                model,
                hitbox,
                teleport,
                placement,
                limit,
                warp,
                NetworkSection.defaults(),
                bettermodel,
                SoundsSection.inherit()
        );
    }

    /**
     * Convenience constructor preserving backward compatibility for 11-argument invocations without type/network (#27).
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
            @Nullable final WarpOverrideSection warp,
            @Nullable final BetterModelSection bettermodel,
            @Nullable final SoundsSection sounds
    ) {
        this(
                displayName,
                VariationType.NORMAL,
                item,
                recipe,
                model,
                hitbox,
                teleport,
                placement,
                limit,
                warp,
                NetworkSection.defaults(),
                bettermodel,
                sounds
        );
    }

    /**
     * Canonical constructor ensuring non-null sections when deserialized by Configurate.
     */
    public AetheryteVariation(
            @Nullable final String displayName,
            @Nullable final VariationType type,
            @Nullable final ItemSection item,
            @Nullable final RecipeSection recipe,
            @Nullable final ModelSection model,
            @Nullable final HitboxSection hitbox,
            @Nullable final TeleportSection teleport,
            @Nullable final PlacementSection placement,
            @Nullable final LimitSection limit,
            @Nullable final WarpOverrideSection warp,
            @Nullable final NetworkSection network,
            @Nullable final BetterModelSection bettermodel,
            @Nullable final SoundsSection sounds
    ) {
        this.displayName = (displayName == null || displayName.isBlank()) ? "<aqua>Aetheryte</aqua>" : displayName;
        this.type = type == null ? VariationType.NORMAL : type;
        this.item = item == null ? ItemSection.defaults() : item;
        this.recipe = recipe == null ? RecipeSection.disabled() : recipe;
        this.model = model == null ? ModelSection.defaults() : model;
        this.hitbox = hitbox == null ? HitboxSection.defaults() : hitbox;
        this.teleport = teleport == null ? TeleportSection.defaults() : teleport;
        this.placement = placement == null ? PlacementSection.defaults() : placement;
        this.limit = limit == null ? LimitSection.defaults() : limit;
        this.warp = warp == null ? WarpOverrideSection.defaults() : warp;
        this.network = network == null ? NetworkSection.defaults() : network;
        this.bettermodel = bettermodel == null ? BetterModelSection.disabled() : bettermodel;
        this.sounds = sounds == null ? SoundsSection.inherit() : sounds;
    }

    /**
     * Returns the default {@link AetheryteVariation} matching {@code default.yml}.
     */
    public static AetheryteVariation defaultVariation() {
        return new AetheryteVariation(
                "<aqua>Aetheryte</aqua>",
                VariationType.NORMAL,
                ItemSection.defaults(),
                RecipeSection.defaults(),
                ModelSection.defaults(),
                HitboxSection.defaults(),
                TeleportSection.defaults(),
                PlacementSection.defaults(),
                LimitSection.defaults(),
                WarpOverrideSection.defaults(),
                NetworkSection.defaults(),
                BetterModelSection.disabled(),
                SoundsSection.inherit()
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

        // 5. Validate teleport section (#41, #62)
        final int rawMaxRadius = teleport.maxRadius();
        int validMaxRadius = rawMaxRadius;
        if (validMaxRadius < MIN_HORIZONTAL_RADIUS || validMaxRadius > MAX_HORIZONTAL_RADIUS) {
            final int clamped = Math.clamp(validMaxRadius, MIN_HORIZONTAL_RADIUS, MAX_HORIZONTAL_RADIUS);
            logger.warn("Clamping teleport.max-radius {} to {} in variation '{}' (allowed range: {}..{})",
                    validMaxRadius, clamped, cleanId, MIN_HORIZONTAL_RADIUS, MAX_HORIZONTAL_RADIUS);
            validMaxRadius = clamped;
        }
        final int rawMinRadius = teleport.minRadius();
        int validMinRadius = rawMinRadius;
        if (validMinRadius < MIN_HORIZONTAL_RADIUS || validMinRadius > validMaxRadius) {
            final int clamped = Math.clamp(validMinRadius, MIN_HORIZONTAL_RADIUS, validMaxRadius);
            logger.warn("Clamping teleport.min-radius {} to {} in variation '{}' (allowed range: {}..{})",
                    validMinRadius, clamped, cleanId, MIN_HORIZONTAL_RADIUS, validMaxRadius);
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
        final Double rawMinDistance = placement.minDistance();
        final double validMinDistance = rawMinDistance == null || !Double.isFinite(rawMinDistance) || rawMinDistance < -1.0 ? -1.0 : rawMinDistance;
        final VisibilityCostSection validatedVisibilityCost = (placement.visibilityCost() != null
                ? placement.visibilityCost()
                : VisibilityCostSection.inherit())
                .validated("variations." + cleanId + ".placement.visibility-cost", logger);
        final PlacementSection validatedPlacement = new PlacementSection(
                new ClearanceSection(validClearanceRadius, validClearanceHeight),
                placement.cost(),
                validMinDistance,
                validatedVisibilityCost
        );

        // 7. Validate limit section (#43)
        final int rawMaxPerPlayer = limit.maxPerPlayer();
        final int validMaxPerPlayer = rawMaxPerPlayer < -1 ? -1 : rawMaxPerPlayer;
        final int rawCooldownTicks = limit.cooldownTicks();
        final int validCooldownTicks = rawCooldownTicks < -1 ? -1 : rawCooldownTicks;
        final LimitSection validatedLimit = new LimitSection(validMaxPerPlayer, validCooldownTicks);

        // 8. Validate warp override section (#14, #26)
        final DistanceSection rawWarpDist = warp.distance();
        final double validWarpMin = rawWarpDist.min() == null || !Double.isFinite(rawWarpDist.min()) || rawWarpDist.min() < -1.0 ? -1.0 : rawWarpDist.min();
        double validWarpMax = rawWarpDist.max() == null || !Double.isFinite(rawWarpDist.max()) || rawWarpDist.max() < -1.0 ? -1.0 : rawWarpDist.max();
        if (validWarpMin >= 0.0 && validWarpMax >= 0.0 && validWarpMin > validWarpMax) {
            logger.warn("warp.distance.min ({}) is greater than warp.distance.max ({}) in variation '{}'. Disabling max distance (-1.0) to prevent deadlocks.",
                    validWarpMin, validWarpMax, cleanId);
            validWarpMax = -1.0;
        }
        final Boolean validCrossDim = rawWarpDist.crossDimension();
        final DistanceSection validatedWarpDist = new DistanceSection(validWarpMin, validWarpMax, validCrossDim);

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
        final CastSection rawWarpCast = warp.cast() != null ? warp.cast() : CastSection.inherit();
        final Integer rawCastTicks = rawWarpCast.ticks();
        final Integer validCastTicks;
        if (rawCastTicks != null && rawCastTicks < -1) {
            logger.warn("Invalid warp.cast.ticks {} in variation '{}' (must be >= -1); falling back to -1 (inherit)", rawCastTicks, cleanId);
            validCastTicks = -1;
        } else {
            validCastTicks = rawCastTicks;
        }
        final String validPlayerModel = rawWarpCast.playerModel() == null ? null : rawWarpCast.playerModel().strip();
        final String validCastAnim = rawWarpCast.castAnimation() == null ? null : rawWarpCast.castAnimation().strip();
        final String validArriveAnim = rawWarpCast.arriveAnimation() == null ? null : rawWarpCast.arriveAnimation().strip();
        final CastSection validatedWarpCast = new CastSection(
                validCastTicks,
                validPlayerModel,
                validCastAnim,
                validArriveAnim,
                rawWarpCast.immobilize(),
                rawWarpCast.cancel()
        );
        final VisibilityCostSection validatedWarpVisibilityCost = (warp.visibilityCost() != null
                ? warp.visibilityCost()
                : VisibilityCostSection.inherit())
                .validated("variations." + cleanId + ".warp.visibility-cost", logger);
        final PetsWarpSection validatedWarpPets = (warp.pets() != null
                ? warp.pets()
                : PetsWarpSection.inherit())
                .validatedOverride(cleanId, logger);
        final WarpOverrideSection validatedWarp = new WarpOverrideSection(
                validatedWarpDist,
                warp.cost(),
                validatedWarpCast,
                validatedWarpVisibilityCost,
                validatedWarpPets
        );

        // 8b. Validate network section (#27)
        final NetworkLimitSection rawNetLimit = network.limit() != null ? network.limit() : NetworkLimitSection.defaults();
        final int validNetMaxPerPlayer = rawNetLimit.maxPerPlayerOrDefault() < -1 ? -1 : rawNetLimit.maxPerPlayerOrDefault();
        final int validNetMaxAetheryte = rawNetLimit.maxAetheryteOrDefault() < -1 ? -1 : rawNetLimit.maxAetheryteOrDefault();
        final double rawNetMaxDist = rawNetLimit.maxDistanceOrDefault();
        final double validNetMaxDist = !Double.isFinite(rawNetMaxDist) || rawNetMaxDist < -1.0 ? -1.0 : rawNetMaxDist;
        final NetworkCostSection rawNetCost = network.cost() != null ? network.cost() : NetworkCostSection.defaults();
        validateNetworkCostEntries(rawNetCost.create(), "network.cost.create", cleanId, logger);
        validateNetworkCostEntries(rawNetCost.join(), "network.cost.join", cleanId, logger);
        final NetworkSection validatedNetwork = new NetworkSection(
                new NetworkLimitSection(validNetMaxPerPlayer, validNetMaxAetheryte, validNetMaxDist),
                new NetworkCostSection(rawNetCost.create(), rawNetCost.join()),
                network.allowHubOrDefault()
        );

        // 9. Validate BetterModel section (#46)
        final boolean bmEnabled = Boolean.TRUE.equals(bettermodel.enabled());
        final String rawModelId = bettermodel.modelId() == null ? "" : bettermodel.modelId().strip();
        final AnimationSection rawAnims = bettermodel.animations();
        final String idleAnim = (rawAnims.idle() == null || rawAnims.idle().isBlank()) ? "idle" : rawAnims.idle().strip();
        final String onInteract = rawAnims.onInteract() == null ? "" : rawAnims.onInteract().strip();
        final String onWarpDepart = rawAnims.onWarpDepart() == null ? "" : rawAnims.onWarpDepart().strip();
        final String onWarpArrive = rawAnims.onWarpArrive() == null ? "" : rawAnims.onWarpArrive().strip();
        final BetterModelSection validatedBetterModel = new BetterModelSection(
                bmEnabled,
                rawModelId,
                new AnimationSection(idleAnim, onInteract, onWarpDepart, onWarpArrive)
        );

        final SoundsSection validatedSounds = sounds.validatedOverride(cleanId, logger);

        return new AetheryteVariation(
                validDisplayName,
                type != null ? type : VariationType.NORMAL,
                validatedItem,
                validatedRecipe,
                validatedModel,
                validatedHitbox,
                validatedTeleport,
                validatedPlacement,
                validatedLimit,
                validatedWarp,
                validatedNetwork,
                validatedBetterModel,
                validatedSounds
        );
    }

    private static void validateNetworkCostEntries(
            final List<CostEntry> entries,
            final String path,
            final String cleanId,
            final Logger logger
    ) {
        for (final CostEntry entry : entries) {
            if (entry.type() == null) {
                logger.warn("Unknown cost type '{}' in {} of variation '{}'", entry.rawType(), path, cleanId);
                continue;
            }
            try {
                ExpressionParser.parse(entry.amount(), entry.type().resourceVariable());
            } catch (final ExpressionException e) {
                logger.warn("Invalid cost expression '{}' in {} of variation '{}': {}", entry.amount(), path, cleanId, e.getMessage());
            }
        }
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
