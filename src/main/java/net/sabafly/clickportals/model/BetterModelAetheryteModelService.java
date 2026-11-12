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
package net.sabafly.clickportals.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.DummyTracker;
import kr.toxicity.model.api.tracker.Tracker;
import kr.toxicity.model.api.tracker.TrackerModifier;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.BetterModelSection;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

/**
 * Production implementation of {@link AetheryteModelService} using the BetterModel API (#46).
 */
public final class BetterModelAetheryteModelService implements AetheryteModelService {

    private final Logger logger;
    private final Function<String, Optional<ModelRenderer>> modelSupplier;
    private final BiFunction<ModelRenderer, Location, Optional<ModelTracker>> trackerFactory;
    private final BooleanSupplier readySupplier;
    private final Map<UUID, ActiveModel> activeModels = new ConcurrentHashMap<>();

    private record ActiveModel(ModelTracker tracker, BetterModelSection section, UUID worldId) {}

    /**
     * Default {@link ModelTracker} adapter wrapping a concrete BetterModel {@link Tracker}.
     *
     * @param tracker the underlying BetterModel tracker instance
     */
    public record BetterModelTrackerHandle(Tracker tracker) implements ModelTracker {

        @Override
        public boolean animate(final String animationName) {
            return tracker.animate(animationName);
        }

        @Override
        public boolean animate(final String animationName, final AnimationModifier modifier) {
            return tracker.animate(animationName, modifier);
        }

        @Override
        public boolean animate(
                final String animationName,
                final AnimationModifier modifier,
                final Runnable onEnd
        ) {
            return tracker.animate(animationName, modifier, onEnd);
        }

        @Override
        public boolean isClosed() {
            return tracker.isClosed();
        }

        @Override
        public boolean spawn(final Player player) {
            if (tracker instanceof DummyTracker dummy && !dummy.isClosed()) {
                dummy.spawn(BukkitAdapter.adapt(player));
                return true;
            }
            return false;
        }

        @Override
        public boolean remove(final Player player) {
            return tracker.remove(BukkitAdapter.adapt(player));
        }

        @Override
        public boolean isSpawned(final Player player) {
            return tracker.isSpawned(player.getUniqueId());
        }

        @Override
        public void close() {
            tracker.close();
        }
    }

    /**
     * Creates a new {@link BetterModelAetheryteModelService} using the live BetterModel runtime API.
     *
     * @param logger the plugin logger for reporting warnings and debug information
     */
    public BetterModelAetheryteModelService(final Logger logger) {
        this(
                logger,
                modelId -> {
                    try {
                        return BetterModel.model(modelId);
                    } catch (final Throwable t) {
                        return Optional.empty();
                    }
                },
                (renderer, loc) -> {
                    try {
                        return Optional.of(new BetterModelTrackerHandle(
                                renderer.create(BukkitAdapter.adapt(loc), TrackerModifier.DEFAULT)));
                    } catch (final Throwable t) {
                        return Optional.empty();
                    }
                },
                () -> {
                    try {
                        return !BetterModel.models().isEmpty();
                    } catch (final Throwable t) {
                        return false;
                    }
                }
        );
    }

    /**
     * Creates a new {@link BetterModelAetheryteModelService} with injected model supplier and tracker factory for testing.
     *
     * @param logger the logger instance
     * @param modelSupplier function to look up model renderers by ID
     * @param trackerFactory factory function to spawn model trackers at locations
     */
    public BetterModelAetheryteModelService(
            final Logger logger,
            final Function<String, Optional<ModelRenderer>> modelSupplier,
            final BiFunction<ModelRenderer, Location, Optional<ModelTracker>> trackerFactory
    ) {
        this(logger, modelSupplier, trackerFactory, () -> true);
    }

    /**
     * Creates a new {@link BetterModelAetheryteModelService} with injected ready check for testing.
     *
     * @param logger the logger instance
     * @param modelSupplier function to look up model renderers by ID
     * @param trackerFactory factory function to spawn model trackers at locations
     * @param readySupplier supplier indicating whether BetterModel has finished loading its models
     */
    public BetterModelAetheryteModelService(
            final Logger logger,
            final Function<String, Optional<ModelRenderer>> modelSupplier,
            final BiFunction<ModelRenderer, Location, Optional<ModelTracker>> trackerFactory,
            final BooleanSupplier readySupplier
    ) {
        this.logger = logger;
        this.modelSupplier = modelSupplier;
        this.trackerFactory = trackerFactory;
        this.readySupplier = readySupplier;
    }

    @Override
    public void registerAetheryte(final Aetheryte aetheryte, final AetheryteVariation variation, final World world) {
        final BetterModelSection section = variation.bettermodel();
        if (!section.enabled() || section.modelId().isBlank()) {
            unregisterAetheryte(aetheryte.id());
            return;
        }

        final String modelId = section.modelId();
        final Optional<ModelRenderer> rendererOpt;
        try {
            rendererOpt = modelSupplier.apply(modelId);
        } catch (final Throwable t) {
            logger.warn("Failed to query BetterModel model '{}': {}", modelId, t.getMessage());
            unregisterAetheryte(aetheryte.id());
            return;
        }

        if (rendererOpt.isEmpty()) {
            if (!readySupplier.getAsBoolean()) {
                logger.debug(
                        "BetterModel model '{}' not loaded yet for Aetheryte variation '{}'; will sync when BetterModel completes loading",
                        modelId, aetheryte.variation());
            } else {
                logger.warn("BetterModel model '{}' not found for Aetheryte variation '{}'; falling back to BLOCK_DISPLAY",
                        modelId, aetheryte.variation());
            }
            unregisterAetheryte(aetheryte.id());
            return;
        }

        // Close any previously registered model for this Aetheryte
        unregisterAetheryte(aetheryte.id());

        final Location loc = aetheryte.markerLocation(world);
        final Optional<ModelTracker> trackerOpt;
        try {
            trackerOpt = trackerFactory.apply(rendererOpt.get(), loc);
        } catch (final Throwable t) {
            logger.warn("Failed to create BetterModel tracker for Aetheryte {}: {}", aetheryte.id(), t.getMessage());
            return;
        }

        if (trackerOpt.isEmpty()) {
            logger.warn("BetterModel tracker creation returned empty for Aetheryte {}; falling back to BLOCK_DISPLAY",
                    aetheryte.id());
            return;
        }

        final ModelTracker tracker = trackerOpt.get();
        final String idleAnim = section.animations().idle();
        if (!idleAnim.isBlank()) {
            try {
                tracker.animate(idleAnim);
            } catch (final Throwable t) {
                logger.warn("Failed to start BetterModel idle animation '{}' on Aetheryte {}: {}",
                        idleAnim, aetheryte.id(), t.getMessage());
            }
        }

        activeModels.put(aetheryte.id(), new ActiveModel(tracker, section, world.getUID()));

        // DummyTracker is not auto-tracked by BetterModel; spawn explicitly for players already in the world.
        for (final Player player : world.getPlayers()) {
            spawnSafely(tracker, player, aetheryte.id());
        }
    }

    @Override
    public void refreshPlayer(final Player player, final Collection<Aetheryte> worldAetherytes) {
        final UUID worldId = player.getWorld().getUID();
        final Set<UUID> inWorld = new HashSet<>();
        for (final Aetheryte aetheryte : worldAetherytes) {
            inWorld.add(aetheryte.id());
        }
        for (final Map.Entry<UUID, ActiveModel> entry : activeModels.entrySet()) {
            final ActiveModel active = entry.getValue();
            if (active.tracker.isClosed()) {
                continue;
            }
            final boolean visible = active.worldId.equals(worldId) && inWorld.contains(entry.getKey());
            try {
                if (visible) {
                    if (active.tracker.isSpawned(player)) {
                        active.tracker.remove(player);
                    }
                    spawnSafely(active.tracker, player, entry.getKey());
                } else if (active.tracker.isSpawned(player)) {
                    active.tracker.remove(player);
                }
            } catch (final Throwable t) {
                logger.debug("Failed to refresh BetterModel visibility for Aetheryte {}", entry.getKey(), t);
            }
        }
    }

    @Override
    public void removePlayer(final Player player) {
        for (final Map.Entry<UUID, ActiveModel> entry : activeModels.entrySet()) {
            final ModelTracker tracker = entry.getValue().tracker;
            try {
                if (!tracker.isClosed() && tracker.isSpawned(player)) {
                    tracker.remove(player);
                }
            } catch (final Throwable t) {
                logger.debug("Failed to remove player from BetterModel tracker for Aetheryte {}", entry.getKey(), t);
            }
        }
    }

    @Override
    public void showAetheryteToPlayer(final Player player, final Aetheryte aetheryte) {
        final ActiveModel active = activeModels.get(aetheryte.id());
        if (active == null || active.tracker.isClosed()) {
            return;
        }
        if (player.getWorld().getUID().equals(active.worldId)) {
            try {
                if (active.tracker.isSpawned(player)) {
                    active.tracker.remove(player);
                }
                spawnSafely(active.tracker, player, aetheryte.id());
            } catch (final Throwable t) {
                logger.debug("Failed to spawn BetterModel for Aetheryte {}", aetheryte.id(), t);
            }
        }
    }

    @Override
    public void hideAetheryteFromPlayer(final Player player, final Aetheryte aetheryte) {
        final ActiveModel active = activeModels.get(aetheryte.id());
        if (active == null || active.tracker.isClosed()) {
            return;
        }
        try {
            if (active.tracker.isSpawned(player)) {
                active.tracker.remove(player);
            }
        } catch (final Throwable t) {
            logger.debug("Failed to hide BetterModel for Aetheryte {}", aetheryte.id(), t);
        }
    }

    /**
     * Spawns the model for a player, logging instead of propagating failures.
     */
    private void spawnSafely(final ModelTracker tracker, final Player player, final UUID aetheryteId) {
        try {
            tracker.spawn(player);
        } catch (final Throwable t) {
            logger.debug("Failed to spawn BetterModel model of Aetheryte {} for {}", aetheryteId, player.getName(), t);
        }
    }

    @Override
    public void unregisterAetheryte(final UUID aetheryteId) {
        final ActiveModel removed = activeModels.remove(aetheryteId);
        if (removed != null) {
            try {
                if (!removed.tracker.isClosed()) {
                    removed.tracker.close();
                }
            } catch (final Throwable t) {
                logger.warn("Failed to cleanly close BetterModel tracker for Aetheryte {}: {}",
                        aetheryteId, t.getMessage());
            }
        }
    }

    @Override
    public void triggerAnimation(final UUID aetheryteId, final ModelAnimationTrigger trigger) {
        final ActiveModel active = activeModels.get(aetheryteId);
        if (active == null || active.tracker.isClosed()) {
            return;
        }

        final String targetAnim = switch (trigger) {
            case IDLE -> active.section.animations().idle();
            case ON_INTERACT -> active.section.animations().onInteract();
            case ON_WARP_DEPART -> active.section.animations().onWarpDepart();
            case ON_WARP_ARRIVE -> active.section.animations().onWarpArrive();
        };

        if (targetAnim.isBlank()) {
            return;
        }

        final String idleAnim = active.section.animations().idle();
        try {
            if (trigger == ModelAnimationTrigger.IDLE || targetAnim.equals(idleAnim)) {
                active.tracker.animate(targetAnim);
            } else {
                final AnimationModifier modifier = AnimationModifier.builder()
                        .type(AnimationIterator.Type.PLAY_ONCE)
                        .priority(1)
                        .build();
                active.tracker.animate(targetAnim, modifier);
            }
        } catch (final Throwable t) {
            logger.warn("Failed to play BetterModel animation '{}' ({}) on Aetheryte {}: {}",
                    targetAnim, trigger, aetheryteId, t.getMessage());
        }
    }

    @Override
    public boolean hasModel(final UUID aetheryteId) {
        final ActiveModel active = activeModels.get(aetheryteId);
        return active != null && !active.tracker.isClosed();
    }

    @Override
    public void destroyAll() {
        for (final ActiveModel model : activeModels.values()) {
            try {
                if (!model.tracker.isClosed()) {
                    model.tracker.close();
                }
            } catch (final Throwable t) {
                logger.debug("Error while closing tracker during destroyAll", t);
            }
        }
        activeModels.clear();
    }
}
