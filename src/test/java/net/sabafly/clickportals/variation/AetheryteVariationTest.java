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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import net.sabafly.clickportals.variation.AetheryteVariation.ClearanceSection;
import net.sabafly.clickportals.variation.AetheryteVariation.CustomModelDataSection;
import net.sabafly.clickportals.variation.AetheryteVariation.HitboxSection;
import net.sabafly.clickportals.variation.AetheryteVariation.ItemSection;
import net.sabafly.clickportals.variation.AetheryteVariation.LimitSection;
import net.sabafly.clickportals.variation.AetheryteVariation.ModelSection;
import net.sabafly.clickportals.variation.AetheryteVariation.PlacementSection;
import net.sabafly.clickportals.variation.AetheryteVariation.RecipeSection;
import net.sabafly.clickportals.variation.AetheryteVariation.TeleportSection;
import net.sabafly.clickportals.variation.AetheryteVariation.WarpOverrideSection;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class AetheryteVariationTest {

    @Test
    void invalidHitboxRadiiMaterialsAndRecipeFallBackToSafeDefaults() {
        final AetheryteVariation raw = new AetheryteVariation(
                "  ",
                new ItemSection("AIR", "", List.of(), "", CustomModelDataSection.empty(), true),
                new RecipeSection(true, List.of("SS", "SAS", "S"), Map.of("S", "STONE", "A", "INVALID_MAT")),
                new ModelSection(
                        "   ",
                        List.of(1.0, 2.0),
                        List.of(0.0),
                        Double.NaN
                ),
                new HitboxSection(-2.5f, 0.0f),
                new TeleportSection(5, 3, false),
                new PlacementSection(new ClearanceSection(-5, 99)),
                new LimitSection(-10, -5)
        );

        final AetheryteVariation validated = raw.validated("test_var", mock(Logger.class));

        assertThat(validated.displayName()).isEqualTo("<aqua>Aetheryte</aqua>");
        assertThat(validated.item().bukkitMaterial()).isEqualTo(Material.COMPASS);
        // Custom variation invalid recipe is disabled rather than colliding with default recipe (#38)
        assertThat(validated.recipe().enabled()).isFalse();
        assertThat(validated.model().block()).isEqualTo("minecraft:lodestone");
        assertThat(validated.model().scale()).containsExactly(1.0, 1.0, 1.0);
        assertThat(validated.model().translation()).containsExactly(0.0, 0.0, 0.0);
        assertThat(validated.model().nameHeight()).isEqualTo(1.35);
        assertThat(validated.hitbox().width()).isEqualTo(1.0f);
        assertThat(validated.hitbox().height()).isEqualTo(1.0f);
        assertThat(validated.teleport().minRadius()).isEqualTo(3);
        assertThat(validated.teleport().maxRadius()).isEqualTo(3);
        assertThat(validated.teleport().faceAetheryte()).isFalse();
        assertThat(validated.placement().clearance().radius()).isEqualTo(0);
        assertThat(validated.placement().clearance().height()).isEqualTo(AetheryteVariation.MAX_CLEARANCE_HEIGHT);
        assertThat(validated.limit().maxPerPlayer()).isEqualTo(-1);
        assertThat(validated.limit().cooldownTicks()).isEqualTo(-1);
    }

    @Test
    void defaultVariationInvalidRecipeFallsBackToBundledDefaultRecipe() {
        final AetheryteVariation raw = new AetheryteVariation(
                "Default",
                ItemSection.defaults(),
                new RecipeSection(true, List.of("INVALID"), Map.of()),
                ModelSection.defaults(),
                HitboxSection.defaults(),
                TeleportSection.defaults(),
                PlacementSection.defaults(),
                LimitSection.defaults()
        );

        final AetheryteVariation validated = raw.validated(net.sabafly.clickportals.aetheryte.Aetheryte.DEFAULT_VARIATION, mock(Logger.class));

        assertThat(validated.recipe().enabled()).isTrue();
        assertThat(validated.recipe().shape()).isEqualTo(RecipeSection.defaults().shape());
        assertThat(validated.recipe().bukkitIngredients()).isEqualTo(RecipeSection.defaults().bukkitIngredients());
    }

    @Test
    void omittedFieldsInSectionsResolveToDefaultsWhilePreservingExplicitZerosAndFalses() {
        final HitboxSection hitboxNull = new HitboxSection(null, null);
        assertThat(hitboxNull.width()).isEqualTo(1.0f);
        assertThat(hitboxNull.height()).isEqualTo(1.0f);

        final TeleportSection teleportNull = new TeleportSection(null, null, null);
        assertThat(teleportNull.minRadius()).isEqualTo(0);
        assertThat(teleportNull.maxRadius()).isEqualTo(0);
        assertThat(teleportNull.faceAetheryte()).isTrue();

        final TeleportSection teleportExplicitZeroFalse = new TeleportSection(0, 0, false);
        assertThat(teleportExplicitZeroFalse.faceAetheryte()).isFalse();

        final ClearanceSection clearanceNull = new ClearanceSection(null, null);
        assertThat(clearanceNull.radius()).isEqualTo(0);
        assertThat(clearanceNull.height()).isEqualTo(2);

        final LimitSection limitNull = new LimitSection(null, null);
        assertThat(limitNull.maxPerPlayer()).isEqualTo(-1);
        assertThat(limitNull.cooldownTicks()).isEqualTo(-1);

        final LimitSection limitExplicitZero = new LimitSection(0, 0);
        assertThat(limitExplicitZero.maxPerPlayer()).isEqualTo(0);
        assertThat(limitExplicitZero.cooldownTicks()).isEqualTo(0);

        final ModelSection modelNull = new ModelSection(null, null, null, null);
        assertThat(modelNull.nameHeight()).isEqualTo(1.35);
    }

    @Test
    void validCustomItemModelAndHitboxArePreserved() {
        final AetheryteVariation raw = new AetheryteVariation(
                "<gold>Grand Aetheryte</gold>",
                new ItemSection(
                        "ECHO_SHARD",
                        "<gold>Grand Shard</gold>",
                        List.of("<gray>Line 1</gray>"),
                        "myserver:grand_shard",
                        new CustomModelDataSection(List.of(1.0f), List.of(true), List.of("grand"), List.of(0xFFAA00)),
                        false
                ),
                new RecipeSection(
                        true,
                        List.of(" G ", "GEG", " G "),
                        Map.of("G", "GOLD_BLOCK", "E", "ECHO_SHARD")
                ),
                new ModelSection(
                        "minecraft:gold_block",
                        List.of(2.0, 3.0, 2.0),
                        List.of(0.0, 0.5, 0.0),
                        3.25
                ),
                new HitboxSection(2.0f, 3.5f),
                new TeleportSection(1, 3, true),
                new PlacementSection(new ClearanceSection(1, 4)),
                new LimitSection(2, 60)
        );

        final AetheryteVariation validated = raw.validated("grand", mock(Logger.class));

        assertThat(validated.displayName()).isEqualTo("<gold>Grand Aetheryte</gold>");
        assertThat(validated.item().bukkitMaterial()).isEqualTo(Material.ECHO_SHARD);
        assertThat(validated.item().itemModel()).isEqualTo("myserver:grand_shard");
        assertThat(validated.item().customModelData().floats()).containsExactly(1.0f);
        assertThat(validated.item().customModelData().bukkitColors()).hasSize(1);
        assertThat(validated.model().block()).isEqualTo("minecraft:gold_block");
        assertThat(validated.model().scale()).containsExactly(2.0, 3.0, 2.0);
        assertThat(validated.model().translation()).containsExactly(0.0, 0.5, 0.0);
        assertThat(validated.model().nameHeight()).isEqualTo(3.25);
        assertThat(validated.hitbox().width()).isEqualTo(2.0f);
        assertThat(validated.hitbox().height()).isEqualTo(3.5f);
        assertThat(validated.teleport().minRadius()).isEqualTo(1);
        assertThat(validated.teleport().maxRadius()).isEqualTo(3);
        assertThat(validated.placement().clearance().radius()).isEqualTo(1);
        assertThat(validated.placement().clearance().height()).isEqualTo(4);
        assertThat(validated.limit().maxPerPlayer()).isEqualTo(2);
        assertThat(validated.limit().cooldownTicks()).isEqualTo(60);
    }

    @Test
    void distanceValidationClampsMaxWhenMinGreaterThanMax() {
        final AetheryteVariation raw = new AetheryteVariation(
                "Custom",
                ItemSection.defaults(),
                RecipeSection.defaults(),
                ModelSection.defaults(),
                HitboxSection.defaults(),
                TeleportSection.defaults(),
                PlacementSection.defaults(),
                LimitSection.defaults(),
                new WarpOverrideSection(new net.sabafly.clickportals.config.DistanceSection(100.0, 50.0), null)
        );

        final Logger logger = mock(Logger.class);
        final AetheryteVariation validated = raw.validated("custom", logger);

        assertThat(validated.warp().distance().min()).isEqualTo(100.0);
        assertThat(validated.warp().distance().max()).isEqualTo(-1.0);
        org.mockito.Mockito.verify(logger).warn(
                org.mockito.ArgumentMatchers.contains("greater than"),
                eq(100.0),
                eq(50.0),
                eq("custom")
        );
    }

    @Test
    void omittedMinInVariationDistanceInheritsGlobal() {
        final AetheryteVariation raw = new AetheryteVariation(
                "Custom",
                ItemSection.defaults(),
                RecipeSection.defaults(),
                ModelSection.defaults(),
                HitboxSection.defaults(),
                TeleportSection.defaults(),
                PlacementSection.defaults(),
                LimitSection.defaults(),
                new WarpOverrideSection(new net.sabafly.clickportals.config.DistanceSection(null, 500.0), null)
        );

        final AetheryteVariation validated = raw.validated("custom", mock(Logger.class));

        // When min is omitted in variation, it must be -1.0 (inherit), not 0.0
        assertThat(validated.warp().distance().min()).isEqualTo(-1.0);
        assertThat(validated.warp().distance().max()).isEqualTo(500.0);
    }
}
