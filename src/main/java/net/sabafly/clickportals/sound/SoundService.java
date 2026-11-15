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

import io.papermc.paper.math.Position;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.sound.SoundsConfig.ResolvedAmbient;
import net.sabafly.clickportals.sound.SoundsConfig.ResolvedSound;
import net.sabafly.clickportals.sound.SoundsConfig.ResolvedSounds;
import net.sabafly.clickportals.sound.SoundsConfig.SoundsSection;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.VariationRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Folia-safe service for resolving and playing global/per-variation event sounds and
 * periodic spatial ambient sounds around placed Aetherytes (#77).
 */
public final class SoundService {

    private static final long AMBIENT_POLL_TICKS = 5L;
    private static final long AMBIENT_POLL_MILLIS = AMBIENT_POLL_TICKS * 50L;
    private static final double MAX_AMBIENT_SCAN_RANGE = SoundsConfig.AmbientSoundSection.MAX_RANGE;

    record AmbientCooldownKey(UUID playerId, UUID aetheryteId) {
    }

    private final @Nullable Plugin plugin;
    private final Supplier<SoundsSection> globalSoundsSupplier;
    private final VariationRegistry variationRegistry;
    private final AetheryteRegistry registry;
    private final Logger logger;
    private final boolean playbackEnabled;
    private final Set<String> warnedInvalidKeys = ConcurrentHashMap.newKeySet();
    private final Map<AmbientCooldownKey, Long> lastAmbientPlayTick = new ConcurrentHashMap<>();
    private final AtomicLong tickCounter = new AtomicLong(0L);
    private final AtomicLong taskGeneration = new AtomicLong(0L);
    private final AtomicReference<@Nullable ScheduledTask> ambientTaskRef = new AtomicReference<>();

    /**
     * Creates a live {@link SoundService} bound to the owning plugin.
     */
    public SoundService(
            final Plugin plugin,
            final Supplier<SoundsSection> globalSoundsSupplier,
            final VariationRegistry variationRegistry,
            final AetheryteRegistry registry
    ) {
        this(plugin, globalSoundsSupplier, variationRegistry, registry, plugin.getSLF4JLogger(), true);
    }

    SoundService(
            final @Nullable Plugin plugin,
            final Supplier<SoundsSection> globalSoundsSupplier,
            final VariationRegistry variationRegistry,
            final AetheryteRegistry registry,
            final Logger logger,
            final boolean playbackEnabled
    ) {
        this.plugin = plugin;
        this.globalSoundsSupplier = globalSoundsSupplier;
        this.variationRegistry = variationRegistry;
        this.registry = registry;
        this.logger = logger;
        this.playbackEnabled = playbackEnabled;
    }

    /**
     * Returns a non-playing {@link SoundService} suitable for headless unit tests that do not test sound playback.
     */
    public static SoundService noop() {
        return new SoundService(
                null,
                SoundsSection::defaults,
                VariationRegistry.defaults(),
                new AetheryteRegistry(),
                LoggerFactory.getLogger(SoundService.class),
                false
        );
    }

    /**
     * Resolves all event and ambient sounds for the given variation ID against the current global config.
     */
    public ResolvedSounds resolveVariationSounds(final @Nullable String variationId) {
        final SoundsSection global = globalSoundsSupplier.get();
        if (variationId == null || variationId.isBlank()) {
            return SoundsSection.inherit().resolve(global);
        }
        final AetheryteVariation variation = variationRegistry.getOrDefault(variationId);
        return variation.sounds().resolve(global);
    }

    /**
     * Resolves a single event sound for the given variation ID against the current global config.
     */
    public ResolvedSound resolveEventSound(final SoundEventType eventType, final @Nullable String variationId) {
        return resolveVariationSounds(variationId).forEvent(eventType);
    }

    /**
     * Resolves ambient sound settings for the given variation ID against the current global config.
     */
    public ResolvedAmbient resolveAmbientSound(final @Nullable String variationId) {
        return resolveVariationSounds(variationId).ambient();
    }

    /**
     * Plays an event sound directly to {@code player} if enabled and configured with a valid key.
     */
    public void playToPlayer(
            final Player player,
            final SoundEventType eventType,
            final @Nullable String variationId
    ) {
        if (!playbackEnabled || !player.isOnline()) {
            return;
        }
        final ResolvedSound resolved = resolveEventSound(eventType, variationId);
        final Sound adventureSound = toAdventureSound(resolved, "event:" + eventType.name() + ":" + variationId);
        if (adventureSound == null) {
            return;
        }
        playSafely(() -> player.playSound(adventureSound));
    }

    /**
     * Plays the {@link SoundEventType#ERROR} sound directly to {@code player}.
     */
    public void playError(final Player player, final @Nullable String variationId) {
        playToPlayer(player, SoundEventType.ERROR, variationId);
    }

    /**
     * Plays an event sound in the world at {@code location} if enabled and configured with a valid key.
     */
    public void playAtLocation(
            final Location location,
            final SoundEventType eventType,
            final @Nullable String variationId
    ) {
        if (!playbackEnabled) {
            return;
        }
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final ResolvedSound resolved = resolveEventSound(eventType, variationId);
        final Sound adventureSound = toAdventureSound(resolved, "event:" + eventType.name() + ":" + variationId);
        if (adventureSound == null) {
            return;
        }
        final Position pos = Position.fine(location.getX(), location.getY(), location.getZ());
        playSafely(() -> world.playSound(adventureSound, pos));
    }

    /**
     * Starts or restarts the periodic Folia-safe ambient sound scanner task.
     */
    public void startAmbientTask() {
        stopAmbientTask();
        if (!playbackEnabled || plugin == null) {
            return;
        }
        final long generation = taskGeneration.incrementAndGet();
        tickCounter.set(0L);
        final ScheduledTask task = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                scheduled -> tickAmbient(generation),
                AMBIENT_POLL_MILLIS,
                AMBIENT_POLL_MILLIS,
                TimeUnit.MILLISECONDS
        );
        ambientTaskRef.set(task);
    }

    /**
     * Stops the periodic ambient sound scanner task and clears ambient cooldown state.
     */
    public void stopAmbientTask() {
        taskGeneration.incrementAndGet();
        final ScheduledTask existing = ambientTaskRef.getAndSet(null);
        if (existing != null) {
            existing.cancel();
        }
        lastAmbientPlayTick.clear();
    }

    /**
     * Clears cached warning / cooldown state after configuration or variation reload.
     */
    public void reload() {
        warnedInvalidKeys.clear();
        lastAmbientPlayTick.clear();
    }

    private void tickAmbient(final long generation) {
        if (!playbackEnabled || plugin == null || taskGeneration.get() != generation) {
            return;
        }
        final long currentTick = tickCounter.addAndGet(AMBIENT_POLL_TICKS);
        final boolean shouldPrune = currentTick % 200L == 0L;
        if (registry.getAll().isEmpty()) {
            if (shouldPrune) {
                pruneAmbientCooldowns(currentTick, SoundService::isPlayerOnline);
            }
            return;
        }
        final double maxRange = maxConfiguredAmbientRange();
        if (maxRange <= 0.0) {
            if (shouldPrune) {
                pruneAmbientCooldowns(currentTick, SoundService::isPlayerOnline);
            }
            return;
        }
        final int chunkRadius = computeChunkRadius(maxRange);

        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (taskGeneration.get() != generation) {
                return;
            }
            if (!player.isOnline()) {
                continue;
            }
            player.getScheduler().run(plugin, scheduled -> playAmbientForPlayer(player, chunkRadius, currentTick, generation), null);
        }
        if (shouldPrune) {
            pruneAmbientCooldowns(currentTick, SoundService::isPlayerOnline);
        }
    }

    private void playAmbientForPlayer(
            final Player player,
            final int chunkRadius,
            final long currentTick,
            final long generation
    ) {
        if (taskGeneration.get() != generation || !player.isOnline() || !player.isValid()) {
            return;
        }
        final Location playerLoc = player.getLocation();
        if (playerLoc == null) {
            return;
        }
        final World world = playerLoc.getWorld();
        if (world == null) {
            return;
        }
        final UUID worldId = world.getUID();
        final UUID playerId = player.getUniqueId();
        final int centerChunkX = playerLoc.getBlockX() >> 4;
        final int centerChunkZ = playerLoc.getBlockZ() >> 4;

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                final List<Aetheryte> chunkEntries = registry.getByChunk(worldId, cx, cz);
                if (chunkEntries.isEmpty()) {
                    continue;
                }
                for (final Aetheryte aetheryte : chunkEntries) {
                    if (taskGeneration.get() != generation) {
                        return;
                    }
                    final ResolvedAmbient ambient = resolveAmbientSound(aetheryte.variation());
                    if (!ambient.isPlayable()) {
                        continue;
                    }
                    final double effectiveRange = Math.min(ambient.range(), MAX_AMBIENT_SCAN_RANGE);
                    final double ax = aetheryte.blockX() + 0.5;
                    final double ay = aetheryte.blockY() + 0.5;
                    final double az = aetheryte.blockZ() + 0.5;
                    final double dx = playerLoc.getX() - ax;
                    final double dy = playerLoc.getY() - ay;
                    final double dz = playerLoc.getZ() - az;
                    final double distSq = dx * dx + dy * dy + dz * dz;

                    final AmbientCooldownKey cooldownKey = new AmbientCooldownKey(playerId, aetheryte.id());
                    final Long lastTick = lastAmbientPlayTick.get(cooldownKey);
                    if (!shouldPlayAmbient(currentTick, lastTick, ambient.intervalTicks(), distSq, effectiveRange)) {
                        continue;
                    }
                    final Key key = resolvePlayableKey(ambient.sound(), "ambient:" + aetheryte.variation());
                    if (key == null) {
                        continue;
                    }
                    if (taskGeneration.get() != generation) {
                        return;
                    }
                    lastAmbientPlayTick.put(cooldownKey, currentTick);
                    final Sound sound = Sound.sound(key, Sound.Source.BLOCK, ambient.volume(), ambient.pitch());
                    playSafely(() -> player.playSound(sound, ax, ay, az));
                }
            }
        }
    }

    private static boolean isPlayerOnline(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        return player != null && player.isOnline();
    }

    void recordAmbientCooldownForTest(final UUID playerId, final UUID aetheryteId, final long tick) {
        lastAmbientPlayTick.put(new AmbientCooldownKey(playerId, aetheryteId), tick);
    }

    int ambientCooldownCacheSize() {
        return lastAmbientPlayTick.size();
    }

    boolean hasAmbientCooldownEntry(final UUID playerId, final UUID aetheryteId) {
        return lastAmbientPlayTick.containsKey(new AmbientCooldownKey(playerId, aetheryteId));
    }

    void pruneAmbientCooldowns(
            final long currentTick,
            final java.util.function.Predicate<UUID> onlineChecker
    ) {
        if (lastAmbientPlayTick.isEmpty()) {
            return;
        }
        lastAmbientPlayTick.entrySet().removeIf(entry -> {
            final AmbientCooldownKey key = entry.getKey();
            if (!onlineChecker.test(key.playerId())) {
                return true;
            }
            final var aetheryteOpt = registry.getById(key.aetheryteId());
            if (aetheryteOpt.isEmpty()) {
                return true;
            }
            final ResolvedAmbient resolved = resolveAmbientSound(aetheryteOpt.get().variation());
            if (!resolved.isPlayable()) {
                return true;
            }
            final long lastTick = entry.getValue();
            return currentTick - lastTick >= resolved.intervalTicks();
        });
    }

    double maxConfiguredAmbientRange() {
        double max = 0.0;
        final SoundsSection global = globalSoundsSupplier.get();
        final ResolvedAmbient globalResolved = SoundsSection.inherit().resolve(global).ambient();
        if (globalResolved.isPlayable()) {
            max = Math.max(max, globalResolved.range());
        }
        for (final AetheryteVariation variation : variationRegistry.all().values()) {
            final ResolvedAmbient resolved = variation.sounds().resolve(global).ambient();
            if (resolved.isPlayable()) {
                max = Math.max(max, resolved.range());
            }
        }
        return Math.min(max, MAX_AMBIENT_SCAN_RANGE);
    }

    static int computeChunkRadius(final double maxRange) {
        if (maxRange <= 0.0) {
            return 0;
        }
        return ((int) Math.ceil(maxRange) >> 4) + 1;
    }

    static boolean shouldPlayAmbient(
            final long currentTick,
            final @Nullable Long lastPlayedTick,
            final int intervalTicks,
            final double distanceSq,
            final double range
    ) {
        if (range <= 0.0 || intervalTicks <= 0 || distanceSq > range * range) {
            return false;
        }
        if (lastPlayedTick == null) {
            return true;
        }
        return currentTick - lastPlayedTick >= intervalTicks;
    }

    @Nullable
    Sound toAdventureSound(final ResolvedSound resolved, final String context) {
        if (!resolved.isEnabled()) {
            return null;
        }
        final Key key = resolvePlayableKey(resolved.sound(), context);
        if (key == null) {
            return null;
        }
        return Sound.sound(key, Sound.Source.BLOCK, resolved.volume(), resolved.pitch());
    }

    private @Nullable Key resolvePlayableKey(final String rawSound, final String context) {
        final Key key = SoundsConfig.parseSoundKey(rawSound);
        if (key == null && SoundsConfig.isMalformedKey(rawSound)) {
            if (warnedInvalidKeys.add(context + "=" + rawSound)) {
                logger.warn("Skipping invalid sound key '{}' for {}", rawSound, context);
            }
        }
        return key;
    }

    private void playSafely(final Runnable action) {
        try {
            action.run();
        } catch (final Throwable t) {
            logger.debug("Ignoring sound playback failure in headless/stubbed environment", t);
        }
    }
}
