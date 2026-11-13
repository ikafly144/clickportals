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
package net.sabafly.clickportals.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.sabafly.clickportals.config.CastSection.CastCancelSection;
import net.sabafly.clickportals.route.DimensionRoutesConfig;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.WarpOverrideSection;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class CastSectionTest {

    @Test
    void inheritResolvesToGlobalDefaults() {
        final CastSection global = new CastSection(
                60,
                "custom_limb",
                "cast_loop",
                "arrive_once",
                new CastCancelSection(true, false)
        );
        final CastSection.Resolved resolved = CastSection.inherit().resolve(global);

        assertThat(resolved.ticks()).isEqualTo(60);
        assertThat(resolved.hasCastTime()).isTrue();
        assertThat(resolved.playerModel()).isEqualTo("custom_limb");
        assertThat(resolved.castAnimation()).isEqualTo("cast_loop");
        assertThat(resolved.arriveAnimation()).isEqualTo("arrive_once");
        assertThat(resolved.cancelOnMove()).isTrue();
        assertThat(resolved.cancelOnDamage()).isFalse();
    }

    @Test
    void variationOverridesSelectiveFieldsAndDisablesWithEmptyStringOrZero() {
        final CastSection global = new CastSection(
                60,
                "steve",
                "cast_anim",
                "arrive_anim",
                new CastCancelSection(true, true)
        );
        final CastSection variation = new CastSection(
                0,
                null,
                "",
                "custom_arrive",
                new CastCancelSection(false, null)
        );

        final CastSection.Resolved resolved = variation.resolve(global);
        assertThat(resolved.ticks()).isZero();
        assertThat(resolved.hasCastTime()).isFalse();
        assertThat(resolved.playerModel()).isEqualTo("steve");
        assertThat(resolved.castAnimation()).isEmpty();
        assertThat(resolved.arriveAnimation()).isEqualTo("custom_arrive");
        assertThat(resolved.cancelOnMove()).isFalse();
        assertThat(resolved.cancelOnDamage()).isTrue();
    }

    @Test
    void pluginConfigValidatedClampsNegativeCastTicksAndBlankModel() {
        final Logger logger = mock(Logger.class);
        final PluginConfig raw = new PluginConfig(
                PluginConfig.LanguageSection.defaults(),
                PluginConfig.HologramSection.defaults(),
                PluginConfig.LimitsSection.defaults(),
                new PluginConfig.WarpSection(
                        DistanceSection.defaults(),
                        java.util.List.of(),
                        true,
                        new CastSection(-20, "  ", " cast ", " arrive ", new CastCancelSection(null, false))
                ),
                DimensionRoutesConfig.defaults()
        );

        final PluginConfig validated = raw.validated(logger);
        final CastSection cast = validated.warp().cast();
        assertThat(cast.ticks()).isZero();
        assertThat(cast.playerModel()).isEqualTo("steve");
        assertThat(cast.castAnimation()).isEqualTo("cast");
        assertThat(cast.arriveAnimation()).isEqualTo("arrive");
        assertThat(cast.cancelOnMoveOrDefault(false)).isTrue();
        assertThat(cast.cancelOnDamageOrDefault(true)).isFalse();
    }

    @Test
    void variationValidatedClampsTicksBelowMinusOne() {
        final Logger logger = mock(Logger.class);
        final AetheryteVariation base = AetheryteVariation.defaultVariation();
        final AetheryteVariation raw = new AetheryteVariation(
                base.displayName(),
                base.item(),
                base.recipe(),
                base.model(),
                base.hitbox(),
                base.teleport(),
                base.placement(),
                base.limit(),
                new WarpOverrideSection(
                        DistanceSection.inherit(),
                        null,
                        new CastSection(-5, " custom ", null, " arrive ", null)
                ),
                base.bettermodel()
        );

        final AetheryteVariation validated = raw.validated("test", logger);
        assertThat(validated.warp().cast().ticks()).isEqualTo(-1);
        assertThat(validated.warp().cast().playerModel()).isEqualTo("custom");
        assertThat(validated.warp().cast().castAnimation()).isNull();
        assertThat(validated.warp().cast().arriveAnimation()).isEqualTo("arrive");
    }
}
