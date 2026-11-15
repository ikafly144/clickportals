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

import java.util.Locale;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

/**
 * Shared sound effects and ambient sound configuration model for global {@code config.yml}
 * and per-variation overrides in {@code aetheryte/<type>.yml} (#77).
 */
public final class SoundsConfig {

    private SoundsConfig() {
    }

    /**
     * Parses a resource location sound key string into an Adventure {@link Key}.
     * Returns {@code null} for empty/{@code "none"} values or invalid key syntax.
     */
    public static @Nullable Key parseSoundKey(final @Nullable String rawSound) {
        if (rawSound == null) {
            return null;
        }
        final String stripped = rawSound.strip();
        if (stripped.isEmpty() || "none".equalsIgnoreCase(stripped)) {
            return null;
        }
        final String lower = stripped.toLowerCase(Locale.ROOT);
        final String normalized = lower.indexOf(':') >= 0 ? lower : ("minecraft:" + lower);
        try {
            return Key.key(normalized);
        } catch (final InvalidKeyException e) {
            return null;
        }
    }

    /**
     * Checks whether a configured sound string is non-empty and not explicitly disabled ({@code "none"}),
     * yet fails key parsing.
     */
    public static boolean isMalformedKey(final @Nullable String rawSound) {
        if (rawSound == null) {
            return false;
        }
        final String stripped = rawSound.strip();
        if (stripped.isEmpty() || "none".equalsIgnoreCase(stripped)) {
            return false;
        }
        return parseSoundKey(stripped) == null;
    }

    /**
     * Concrete, resolved event sound settings ready for playback.
     */
    public record ResolvedSound(String sound, float volume, float pitch) {
        public boolean isEnabled() {
            return volume > 0.0f && parseSoundKey(sound) != null;
        }
    }

    /**
     * Concrete, resolved ambient sound settings ready for scheduling.
     */
    public record ResolvedAmbient(
            boolean enabled,
            String sound,
            float volume,
            float pitch,
            double range,
            int intervalTicks
    ) {
        public boolean isPlayable() {
            return enabled
                    && volume > 0.0f
                    && range > 0.0
                    && intervalTicks > 0
                    && parseSoundKey(sound) != null;
        }
    }

    /**
     * Concrete, resolved full sound configuration for a variation or global defaults.
     */
    public record ResolvedSounds(
            ResolvedSound place,
            ResolvedSound destroy,
            ResolvedSound interact,
            ResolvedSound warpCastStart,
            ResolvedSound warpCastCancel,
            ResolvedSound warpDepart,
            ResolvedSound warpArrive,
            ResolvedSound error,
            ResolvedAmbient ambient
    ) {
        public ResolvedSound forEvent(final SoundEventType type) {
            return switch (type) {
                case PLACE -> place;
                case DESTROY -> destroy;
                case INTERACT -> interact;
                case WARP_CAST_START -> warpCastStart;
                case WARP_CAST_CANCEL -> warpCastCancel;
                case WARP_DEPART -> warpDepart;
                case WARP_ARRIVE -> warpArrive;
                case ERROR -> error;
            };
        }
    }

    /**
     * Sound effect entry for an event ({@code sound}, {@code volume}, {@code pitch}).
     * In variation files, {@code null} fields inherit from global {@code config.yml}.
     */
    @ConfigSerializable
    public record SoundEntry(
            @Setting("sound")
            @Comment("Sound key (vanilla or custom resource pack, e.g. minecraft:block.beacon.activate; empty/\"none\" to disable; null in variation to inherit)")
            @Nullable String sound,

            @Setting("volume")
            @Comment("Playback volume (>= 0.0; null in variation to inherit)")
            @Nullable Float volume,

            @Setting("pitch")
            @Comment("Playback pitch (0.5 - 2.0; null in variation to inherit)")
            @Nullable Float pitch
    ) {
        public static SoundEntry of(final String sound, final float volume, final float pitch) {
            return new SoundEntry(sound, volume, pitch);
        }

        public static SoundEntry inherit() {
            return new SoundEntry(null, null, null);
        }

        public SoundEntry validated(final String path, final @Nullable SoundEntry fallback, final Logger logger) {
            final @Nullable String fbSound = fallback != null ? fallback.sound() : null;
            final @Nullable Float fbVolume = fallback != null ? fallback.volume() : null;
            final @Nullable Float fbPitch = fallback != null ? fallback.pitch() : null;

            String validSound = sound != null ? sound.strip() : fbSound;
            if (validSound != null && isMalformedKey(validSound)) {
                logger.warn("Invalid sound key '{}' at {}; falling back to {}", validSound, path,
                        fbSound != null ? fbSound : "disabled");
                validSound = fbSound != null ? fbSound : "";
            }
            Float validVolume = volume != null ? volume : fbVolume;
            if (validVolume != null && (!Float.isFinite(validVolume) || validVolume < 0.0f)) {
                final @Nullable Float safeFbVol = fbVolume != null ? fbVolume : (fallback != null ? Float.valueOf(1.0f) : null);
                logger.warn("Invalid sound volume {} at {} (must be >= 0.0); falling back to {}",
                        validVolume, path, safeFbVol != null ? safeFbVol : 1.0f);
                validVolume = safeFbVol;
            }
            Float validPitch = pitch != null ? pitch : fbPitch;
            if (validPitch != null && (!Float.isFinite(validPitch) || validPitch <= 0.0f)) {
                final @Nullable Float safeFbPitch = fbPitch != null ? fbPitch : (fallback != null ? Float.valueOf(1.0f) : null);
                logger.warn("Invalid sound pitch {} at {} (must be > 0.0); falling back to {}",
                        validPitch, path, safeFbPitch != null ? safeFbPitch : 1.0f);
                validPitch = safeFbPitch;
            } else if (validPitch != null) {
                validPitch = Math.clamp(validPitch, 0.5f, 2.0f);
            }
            return new SoundEntry(validSound, validVolume, validPitch);
        }

        public ResolvedSound resolve(final SoundEntry global) {
            final String rawSound = sound != null ? sound.strip() : (global.sound() != null ? global.sound().strip() : "");
            final float rawVol = volume != null ? volume : (global.volume() != null ? global.volume() : 1.0f);
            final float rawPitch = pitch != null ? pitch : (global.pitch() != null ? global.pitch() : 1.0f);
            final float safeVol = Float.isFinite(rawVol) ? Math.max(0.0f, rawVol) : 1.0f;
            final float safePitch = (Float.isFinite(rawPitch) && rawPitch > 0.0f) ? Math.clamp(rawPitch, 0.5f, 2.0f) : 1.0f;
            return new ResolvedSound(rawSound, safeVol, safePitch);
        }
    }

    /**
     * Periodic spatial ambient sound configuration near placed Aetherytes (#77).
     * In variation files, {@code null} fields inherit from global {@code config.yml}.
     */
    @ConfigSerializable
    public record AmbientSoundSection(
            @Setting("enabled")
            @Comment("Whether ambient sounds play near Aetherytes (null in variation to inherit)")
            @Nullable Boolean enabled,

            @Setting("sound")
            @Comment("Ambient sound key (e.g. minecraft:block.beacon.ambient; empty/\"none\" to disable; null in variation to inherit)")
            @Nullable String sound,

            @Setting("volume")
            @Comment("Ambient sound volume (>= 0.0; null in variation to inherit)")
            @Nullable Float volume,

            @Setting("pitch")
            @Comment("Ambient sound pitch (0.5 - 2.0; null in variation to inherit)")
            @Nullable Float pitch,

            @Setting("range")
            @Comment("Audible radius in blocks around an Aetheryte (0.0 - 64.0; null in variation to inherit)")
            @Nullable Double range,

            @Setting("interval-ticks")
            @Comment("Playback interval in ticks (20 ticks = 1 second; null in variation to inherit)")
            @Nullable Integer intervalTicks
    ) {
        public static final String DEFAULT_SOUND = "minecraft:block.beacon.ambient";
        public static final float DEFAULT_VOLUME = 0.6f;
        public static final float DEFAULT_PITCH = 1.2f;
        public static final double DEFAULT_RANGE = 12.0;
        public static final double MAX_RANGE = 64.0;
        public static final int DEFAULT_INTERVAL_TICKS = 80;

        public static AmbientSoundSection defaults() {
            return new AmbientSoundSection(
                    true,
                    DEFAULT_SOUND,
                    DEFAULT_VOLUME,
                    DEFAULT_PITCH,
                    DEFAULT_RANGE,
                    DEFAULT_INTERVAL_TICKS
            );
        }

        public static AmbientSoundSection inherit() {
            return new AmbientSoundSection(null, null, null, null, null, null);
        }

        public AmbientSoundSection validated(
                final String prefix,
                final @Nullable AmbientSoundSection fallback,
                final Logger logger
        ) {
            final @Nullable Boolean fbEnabled = fallback != null ? fallback.enabled() : null;
            final @Nullable String fbSound = fallback != null ? fallback.sound() : null;
            final @Nullable Float fbVolume = fallback != null ? fallback.volume() : null;
            final @Nullable Float fbPitch = fallback != null ? fallback.pitch() : null;
            final @Nullable Double fbRange = fallback != null ? fallback.range() : null;
            final @Nullable Integer fbInterval = fallback != null ? fallback.intervalTicks() : null;

            final Boolean validEnabled = enabled != null ? enabled : fbEnabled;
            String validSound = sound != null ? sound.strip() : fbSound;
            if (validSound != null && isMalformedKey(validSound)) {
                logger.warn("Invalid ambient sound key '{}' at {}.sound; falling back to {}", validSound, prefix,
                        fbSound != null ? fbSound : "disabled");
                validSound = fbSound != null ? fbSound : "";
            }
            Float validVolume = volume != null ? volume : fbVolume;
            if (validVolume != null && (!Float.isFinite(validVolume) || validVolume < 0.0f)) {
                logger.warn("Invalid ambient sound volume {} at {}.volume (must be >= 0.0); falling back to {}",
                        validVolume, prefix, fbVolume != null ? fbVolume : DEFAULT_VOLUME);
                validVolume = fbVolume != null ? fbVolume : Float.valueOf(DEFAULT_VOLUME);
            }
            Float validPitch = pitch != null ? pitch : fbPitch;
            if (validPitch != null && (!Float.isFinite(validPitch) || validPitch <= 0.0f)) {
                logger.warn("Invalid ambient sound pitch {} at {}.pitch (must be > 0.0); falling back to {}",
                        validPitch, prefix, fbPitch != null ? fbPitch : DEFAULT_PITCH);
                validPitch = fbPitch != null ? fbPitch : Float.valueOf(DEFAULT_PITCH);
            } else if (validPitch != null) {
                validPitch = Math.clamp(validPitch, 0.5f, 2.0f);
            }
            Double validRange = range != null ? range : fbRange;
            if (validRange != null && (!Double.isFinite(validRange) || validRange < 0.0)) {
                logger.warn("Invalid ambient sound range {} at {}.range (must be >= 0.0); falling back to {}",
                        validRange, prefix, fbRange != null ? fbRange : DEFAULT_RANGE);
                validRange = fbRange != null ? fbRange : Double.valueOf(DEFAULT_RANGE);
            } else if (validRange != null && validRange > MAX_RANGE) {
                logger.warn("Ambient sound range {} at {}.range exceeds maximum {}; clamping to {}",
                        validRange, prefix, MAX_RANGE, MAX_RANGE);
                validRange = Double.valueOf(MAX_RANGE);
            }
            Integer validInterval = intervalTicks != null ? intervalTicks : fbInterval;
            if (validInterval != null && validInterval <= 0) {
                logger.warn("Invalid ambient sound interval {} at {}.interval-ticks (must be > 0); falling back to {}",
                        validInterval, prefix, fbInterval != null ? fbInterval : DEFAULT_INTERVAL_TICKS);
                validInterval = fbInterval != null ? fbInterval : Integer.valueOf(DEFAULT_INTERVAL_TICKS);
            }
            return new AmbientSoundSection(validEnabled, validSound, validVolume, validPitch, validRange, validInterval);
        }

        public ResolvedAmbient resolve(final AmbientSoundSection global) {
            final boolean resEnabled = enabled != null ? enabled : Boolean.TRUE.equals(global.enabled());
            final String resSound = sound != null ? sound.strip() : (global.sound() != null ? global.sound().strip() : "");
            final float rawVol = volume != null ? volume : (global.volume() != null ? global.volume() : DEFAULT_VOLUME);
            final float rawPitch = pitch != null ? pitch : (global.pitch() != null ? global.pitch() : DEFAULT_PITCH);
            final double rawRange = range != null ? range : (global.range() != null ? global.range() : DEFAULT_RANGE);
            final int rawInterval = intervalTicks != null
                    ? intervalTicks
                    : (global.intervalTicks() != null ? global.intervalTicks() : DEFAULT_INTERVAL_TICKS);
            return new ResolvedAmbient(
                    resEnabled,
                    resSound,
                    Float.isFinite(rawVol) ? Math.max(0.0f, rawVol) : DEFAULT_VOLUME,
                    (Float.isFinite(rawPitch) && rawPitch > 0.0f) ? Math.clamp(rawPitch, 0.5f, 2.0f) : DEFAULT_PITCH,
                    Double.isFinite(rawRange) ? Math.clamp(rawRange, 0.0, MAX_RANGE) : DEFAULT_RANGE,
                    rawInterval > 0 ? rawInterval : DEFAULT_INTERVAL_TICKS
            );
        }
    }

    /**
     * Full event and ambient sound configuration block shared between {@code config.yml}
     * and {@code aetheryte/<type>.yml} overrides (#77).
     */
    @ConfigSerializable
    public record SoundsSection(
            @Setting("place")
            @Comment("Sound played when placing an Aetheryte")
            SoundEntry place,

            @Setting("destroy")
            @Comment("Sound played when removing an Aetheryte")
            SoundEntry destroy,

            @Setting("interact")
            @Comment("Sound played when interacting with an Aetheryte")
            SoundEntry interact,

            @Setting("warp-cast-start")
            @Comment("Sound played when warp casting begins")
            SoundEntry warpCastStart,

            @Setting("warp-cast-cancel")
            @Comment("Sound played when warp casting is cancelled")
            SoundEntry warpCastCancel,

            @Setting("warp-depart")
            @Comment("Sound played at departure when a player teleports away")
            SoundEntry warpDepart,

            @Setting("warp-arrive")
            @Comment("Sound played upon teleport arrival")
            SoundEntry warpArrive,

            @Setting("error")
            @Comment("Sound played to the player on errors or denied actions")
            SoundEntry error,

            @Setting("ambient")
            @Comment("Periodic ambient sound settings for nearby Aetherytes")
            AmbientSoundSection ambient
    ) {
        public SoundsSection {
            if (place == null) {
                place = SoundEntry.inherit();
            }
            if (destroy == null) {
                destroy = SoundEntry.inherit();
            }
            if (interact == null) {
                interact = SoundEntry.inherit();
            }
            if (warpCastStart == null) {
                warpCastStart = SoundEntry.inherit();
            }
            if (warpCastCancel == null) {
                warpCastCancel = SoundEntry.inherit();
            }
            if (warpDepart == null) {
                warpDepart = SoundEntry.inherit();
            }
            if (warpArrive == null) {
                warpArrive = SoundEntry.inherit();
            }
            if (error == null) {
                error = SoundEntry.inherit();
            }
            if (ambient == null) {
                ambient = AmbientSoundSection.inherit();
            }
        }

        public static SoundsSection defaults() {
            return new SoundsSection(
                    SoundEntry.of("minecraft:block.beacon.activate", 1.0f, 1.2f),
                    SoundEntry.of("minecraft:block.beacon.deactivate", 1.0f, 0.9f),
                    SoundEntry.of("minecraft:block.amethyst_block.chime", 1.0f, 1.0f),
                    SoundEntry.of("minecraft:block.portal.trigger", 0.5f, 1.5f),
                    SoundEntry.of("minecraft:block.note_block.bass", 0.8f, 0.7f),
                    SoundEntry.of("minecraft:entity.enderman.teleport", 0.9f, 1.1f),
                    SoundEntry.of("minecraft:entity.enderman.teleport", 1.0f, 1.2f),
                    SoundEntry.of("minecraft:entity.villager.no", 0.8f, 1.0f),
                    AmbientSoundSection.defaults()
            );
        }

        public static SoundsSection inherit() {
            return new SoundsSection(
                    SoundEntry.inherit(),
                    SoundEntry.inherit(),
                    SoundEntry.inherit(),
                    SoundEntry.inherit(),
                    SoundEntry.inherit(),
                    SoundEntry.inherit(),
                    SoundEntry.inherit(),
                    SoundEntry.inherit(),
                    AmbientSoundSection.inherit()
            );
        }

        public SoundsSection validatedGlobal(final Logger logger) {
            final SoundsSection def = defaults();
            return new SoundsSection(
                    place.validated("sounds.place", def.place(), logger),
                    destroy.validated("sounds.destroy", def.destroy(), logger),
                    interact.validated("sounds.interact", def.interact(), logger),
                    warpCastStart.validated("sounds.warp-cast-start", def.warpCastStart(), logger),
                    warpCastCancel.validated("sounds.warp-cast-cancel", def.warpCastCancel(), logger),
                    warpDepart.validated("sounds.warp-depart", def.warpDepart(), logger),
                    warpArrive.validated("sounds.warp-arrive", def.warpArrive(), logger),
                    error.validated("sounds.error", def.error(), logger),
                    ambient.validated("sounds.ambient", def.ambient(), logger)
            );
        }

        public SoundsSection validatedOverride(final String variationId, final Logger logger) {
            return new SoundsSection(
                    place.validated("variations." + variationId + ".sounds.place", null, logger),
                    destroy.validated("variations." + variationId + ".sounds.destroy", null, logger),
                    interact.validated("variations." + variationId + ".sounds.interact", null, logger),
                    warpCastStart.validated("variations." + variationId + ".sounds.warp-cast-start", null, logger),
                    warpCastCancel.validated("variations." + variationId + ".sounds.warp-cast-cancel", null, logger),
                    warpDepart.validated("variations." + variationId + ".sounds.warp-depart", null, logger),
                    warpArrive.validated("variations." + variationId + ".sounds.warp-arrive", null, logger),
                    error.validated("variations." + variationId + ".sounds.error", null, logger),
                    ambient.validated("variations." + variationId + ".sounds.ambient", null, logger)
            );
        }

        public ResolvedSounds resolve(final SoundsSection global) {
            return new ResolvedSounds(
                    place.resolve(global.place()),
                    destroy.resolve(global.destroy()),
                    interact.resolve(global.interact()),
                    warpCastStart.resolve(global.warpCastStart()),
                    warpCastCancel.resolve(global.warpCastCancel()),
                    warpDepart.resolve(global.warpDepart()),
                    warpArrive.resolve(global.warpArrive()),
                    error.resolve(global.error()),
                    ambient.resolve(global.ambient())
            );
        }

        public ResolvedSounds resolveGlobal() {
            return resolve(defaults());
        }
    }
}
