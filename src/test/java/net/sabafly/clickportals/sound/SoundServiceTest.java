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
package net.sabafly.clickportals.sound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import net.kyori.adventure.key.Key;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.sound.SoundsConfig.AmbientSoundSection;
import net.sabafly.clickportals.sound.SoundsConfig.ResolvedAmbient;
import net.sabafly.clickportals.sound.SoundsConfig.ResolvedSound;
import net.sabafly.clickportals.sound.SoundsConfig.SoundEntry;
import net.sabafly.clickportals.sound.SoundsConfig.SoundsSection;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.VariationRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class SoundServiceTest {

    @Test
    void parseSoundKeyNormalizesAndRejectsEmptyOrInvalidKeys() {
        assertThat(SoundsConfig.parseSoundKey("block.beacon.activate")).isEqualTo(Key.key("minecraft", "block.beacon.activate"));
        assertThat(SoundsConfig.parseSoundKey("custom:portal.hum")).isEqualTo(Key.key("custom", "portal.hum"));
        assertThat(SoundsConfig.parseSoundKey("none")).isNull();
        assertThat(SoundsConfig.parseSoundKey("  ")).isNull();
        assertThat(SoundsConfig.parseSoundKey("INVALID KEY WITH SPACES!")).isNull();
        assertThat(SoundsConfig.isMalformedKey("INVALID KEY WITH SPACES!")).isTrue();
        assertThat(SoundsConfig.isMalformedKey("none")).isFalse();
    }

    @Test
    void resolvesGlobalDefaultsAndPartialVariationOverrides() {
        final SoundsSection customSounds = new SoundsSection(
                new SoundEntry("custom:place_crystal", null, 1.8f),
                new SoundEntry("none", null, null),
                SoundEntry.inherit(),
                new SoundEntry("", 0.0f, null),
                SoundEntry.inherit(),
                SoundEntry.inherit(),
                SoundEntry.inherit(),
                SoundEntry.inherit(),
                new AmbientSoundSection(null, "custom:ambient_hum", 0.4f, null, 20.0, null)
        );
        final AetheryteVariation base = AetheryteVariation.defaultVariation();
        final AetheryteVariation crystal = new AetheryteVariation(
                base.displayName(),
                base.item(),
                base.recipe(),
                base.model(),
                base.hitbox(),
                base.teleport(),
                base.placement(),
                base.limit(),
                base.warp(),
                base.bettermodel(),
                customSounds
        );
        final VariationRegistry variations = new VariationRegistry(
                Map.of(Aetheryte.DEFAULT_VARIATION, base, "crystal", crystal),
                mock(Logger.class)
        );
        final SoundService service = new SoundService(
                null,
                SoundsSection::defaults,
                variations,
                new AetheryteRegistry(),
                mock(Logger.class),
                true
        );

        final ResolvedSound crystalPlace = service.resolveEventSound(SoundEventType.PLACE, "crystal");
        assertThat(crystalPlace.sound()).isEqualTo("custom:place_crystal");
        assertThat(crystalPlace.volume()).isEqualTo(1.0f);
        assertThat(crystalPlace.pitch()).isEqualTo(1.8f);
        assertThat(crystalPlace.isEnabled()).isTrue();

        final ResolvedSound crystalDestroy = service.resolveEventSound(SoundEventType.DESTROY, "crystal");
        assertThat(crystalDestroy.isEnabled()).isFalse();

        final ResolvedSound crystalCastStart = service.resolveEventSound(SoundEventType.WARP_CAST_START, "crystal");
        assertThat(crystalCastStart.isEnabled()).isFalse();

        final ResolvedSound crystalInteract = service.resolveEventSound(SoundEventType.INTERACT, "crystal");
        assertThat(crystalInteract).isEqualTo(SoundsSection.defaults().resolveGlobal().interact());

        final ResolvedAmbient crystalAmbient = service.resolveAmbientSound("crystal");
        assertThat(crystalAmbient.enabled()).isTrue();
        assertThat(crystalAmbient.sound()).isEqualTo("custom:ambient_hum");
        assertThat(crystalAmbient.volume()).isEqualTo(0.4f);
        assertThat(crystalAmbient.pitch()).isEqualTo(AmbientSoundSection.DEFAULT_PITCH);
        assertThat(crystalAmbient.range()).isEqualTo(20.0);
        assertThat(crystalAmbient.intervalTicks()).isEqualTo(AmbientSoundSection.DEFAULT_INTERVAL_TICKS);
        assertThat(crystalAmbient.isPlayable()).isTrue();
    }

    @Test
    void validationClampsPitchAndFallsBackOnInvalidValues() {
        final Logger logger = mock(Logger.class);
        final SoundsSection invalidGlobal = new SoundsSection(
                new SoundEntry("BAD KEY!", -1.0f, 5.0f),
                SoundEntry.inherit(),
                SoundEntry.of("minecraft:block.bell.use", 0.5f, -0.5f),
                SoundEntry.inherit(),
                SoundEntry.inherit(),
                SoundEntry.inherit(),
                SoundEntry.inherit(),
                SoundEntry.inherit(),
                new AmbientSoundSection(true, "BAD AMBIENT!", -0.2f, 3.0f, -4.0, 0)
        );

        final SoundsSection validated = invalidGlobal.validatedGlobal(logger);
        assertThat(validated.place().sound()).isEqualTo("minecraft:block.beacon.activate");
        assertThat(validated.place().volume()).isEqualTo(1.0f);
        assertThat(validated.place().pitch()).isEqualTo(2.0f);
        assertThat(validated.interact().pitch()).isEqualTo(1.0f);
        assertThat(validated.destroy()).isEqualTo(SoundsSection.defaults().destroy());

        assertThat(validated.ambient().sound()).isEqualTo(AmbientSoundSection.DEFAULT_SOUND);
        assertThat(validated.ambient().volume()).isEqualTo(AmbientSoundSection.DEFAULT_VOLUME);
        assertThat(validated.ambient().pitch()).isEqualTo(2.0f);
        assertThat(validated.ambient().range()).isEqualTo(AmbientSoundSection.DEFAULT_RANGE);
        assertThat(validated.ambient().intervalTicks()).isEqualTo(AmbientSoundSection.DEFAULT_INTERVAL_TICKS);

        verify(logger).warn(
                "Invalid sound key '{}' at {}; falling back to {}",
                "BAD KEY!",
                "sounds.place",
                "minecraft:block.beacon.activate"
        );
    }

    @Test
    void ambientHelpersEvaluateDistanceChunkRadiusAndIntervals() {
        assertThat(SoundService.computeChunkRadius(0.0)).isZero();
        assertThat(SoundService.computeChunkRadius(12.0)).isEqualTo(1);
        assertThat(SoundService.computeChunkRadius(24.0)).isEqualTo(2);

        assertThat(SoundService.shouldPlayAmbient(5L, null, 80, 100.0, 12.0)).isTrue();
        assertThat(SoundService.shouldPlayAmbient(5L, null, 80, 200.0, 12.0)).isFalse();
        assertThat(SoundService.shouldPlayAmbient(50L, 10L, 80, 16.0, 12.0)).isFalse();
        assertThat(SoundService.shouldPlayAmbient(90L, 10L, 80, 16.0, 12.0)).isTrue();
    }
}
