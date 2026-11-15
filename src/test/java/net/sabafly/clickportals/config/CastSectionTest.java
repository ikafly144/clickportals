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

import java.util.Objects;
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
                false,
                new CastCancelSection(true, false, false)
        );
        final CastSection.Resolved resolved = CastSection.inherit().resolve(global);

        assertThat(resolved.ticks()).isEqualTo(60);
        assertThat(resolved.hasCastTime()).isTrue();
        assertThat(resolved.playerModel()).isEqualTo("custom_limb");
        assertThat(resolved.castAnimation()).isEqualTo("cast_loop");
        assertThat(resolved.arriveAnimation()).isEqualTo("arrive_once");
        assertThat(resolved.immobilize()).isFalse();
        assertThat(resolved.cancelOnMove()).isTrue();
        assertThat(resolved.cancelOnDamage()).isFalse();
        assertThat(resolved.cancelOnInteract()).isFalse();
    }

    @Test
    void variationOverridesSelectiveFieldsAndDisablesWithEmptyStringOrZero() {
        final CastSection global = new CastSection(
                60,
                "steve",
                "cast_anim",
                "arrive_anim",
                false,
                new CastCancelSection(true, true, false)
        );
        final CastSection variation = new CastSection(
                0,
                null,
                "",
                "custom_arrive",
                true,
                new CastCancelSection(false, null, true)
        );

        final CastSection.Resolved resolved = variation.resolve(global);
        assertThat(resolved.ticks()).isZero();
        assertThat(resolved.hasCastTime()).isFalse();
        assertThat(resolved.playerModel()).isEqualTo("steve");
        assertThat(resolved.castAnimation()).isEmpty();
        assertThat(resolved.arriveAnimation()).isEqualTo("custom_arrive");
        assertThat(resolved.immobilize()).isTrue();
        assertThat(resolved.cancelOnMove()).isFalse();
        assertThat(resolved.cancelOnDamage()).isTrue();
        assertThat(resolved.cancelOnInteract()).isTrue();
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
        assertThat(cast.cancelOnInteractOrDefault(false)).isTrue();
        assertThat(cast.immobilizeOrDefault(false)).isTrue();
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

    @Test
    void resolveChainAppliesFourTierHubToNetworkCastFallback() {
        final CastSection globalNormal = new CastSection(
                80, "steve", "global_cast", "global_arrive", true, new CastCancelSection(true, true, false)
        );
        final CastSection variationNormal = new CastSection(
                60, "alex", null, "var_arrive", false, new CastCancelSection(false, null, true)
        );
        final CastSection globalHubToNet = new CastSection(
                null, "global_hub_model", "global_hub_cast", null, null, new CastCancelSection(null, false, null)
        );
        final CastSection variationHubToNet = new CastSection(
                10, null, "", null, true, new CastCancelSection(true, null, null)
        );

        final CastSection.Resolved tier1 = globalNormal.resolve(CastSection.defaults());
        final CastSection.Resolved tier2 = variationNormal.resolve(tier1);
        final CastSection.Resolved tier3 = globalHubToNet.resolve(tier2);
        final CastSection.Resolved resolved = variationHubToNet.resolve(tier3);

        assertThat(resolved.ticks()).isEqualTo(10);
        assertThat(resolved.playerModel()).isEqualTo("global_hub_model");
        assertThat(resolved.castAnimation()).isEmpty();
        assertThat(resolved.arriveAnimation()).isEqualTo("var_arrive");
        assertThat(resolved.immobilize()).isTrue();
        assertThat(resolved.cancelOnMove()).isTrue();
        assertThat(resolved.cancelOnDamage()).isFalse();
        assertThat(resolved.cancelOnInteract()).isTrue();
    }

    @Test
    void validatedOverrideClampsNegativeTicksAndTrimsBlankFields() {
        final Logger logger = mock(Logger.class);
        final CastSection raw = new CastSection(
                -5,
                "   ",
                "   ",
                " custom_arrive ",
                false,
                new CastCancelSection(true, null, false)
        );

        final CastSection validated = raw.validatedOverride("warp.hub-to-network-cast", logger);
        assertThat(validated.ticks()).isEqualTo(-1);
        assertThat(validated.playerModel()).isNull();
        assertThat(validated.castAnimation()).isEmpty();
        assertThat(validated.arriveAnimation()).isEqualTo("custom_arrive");
        assertThat(validated.immobilize()).isFalse();
        final CastCancelSection cancel = Objects.requireNonNull(validated.cancel());
        assertThat(cancel.onMove()).isTrue();
        assertThat(cancel.onDamage()).isNull();
        assertThat(cancel.onInteract()).isFalse();
    }
}
