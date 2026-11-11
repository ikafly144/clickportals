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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.Tracker;
import kr.toxicity.model.api.tracker.TrackerModifier;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.BetterModelSection;
import org.bukkit.Location;
import org.bukkit.World;
import org.slf4j.Logger;

/**
 * Production implementation of {@link AetheryteModelService} using the BetterModel API (#46).
 */
public final class BetterModelAetheryteModelService implements AetheryteModelService {

    private final Logger logger;
    private final Function<String, Optional<ModelRenderer>> modelSupplier;
    private final BiFunction<ModelRenderer, Location, Optional<ModelTracker>> trackerFactory;
    private final Map<UUID, ActiveModel> activeModels = new ConcurrentHashMap<>();

    private record ActiveModel(ModelTracker tracker, BetterModelSection section) {}

    /**
     * Default {@link ModelTracker} adapter wrapping a concrete BetterModel {@link Tracker}.
     */
    public record BetterModelTrackerHandle(Tracker tracker) implements ModelTracker {

        @Override
        public boolean animate(final String animationName) {
            return tracker.animate(animationName);
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
        public void close() {
            tracker.close();
        }
    }

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
                }
        );
    }

    public BetterModelAetheryteModelService(
            final Logger logger,
            final Function<String, Optional<ModelRenderer>> modelSupplier,
            final BiFunction<ModelRenderer, Location, Optional<ModelTracker>> trackerFactory
    ) {
        this.logger = logger;
        this.modelSupplier = modelSupplier;
        this.trackerFactory = trackerFactory;
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
            logger.warn("BetterModel model '{}' not found for Aetheryte variation '{}'; falling back to BLOCK_DISPLAY",
                    modelId, aetheryte.variation());
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

        activeModels.put(aetheryte.id(), new ActiveModel(tracker, section));
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
            if (trigger == ModelAnimationTrigger.IDLE) {
                active.tracker.animate(targetAnim);
            } else {
                active.tracker.animate(
                        targetAnim,
                        AnimationModifier.DEFAULT_WITH_PLAY_ONCE,
                        () -> {
                            if (!idleAnim.isBlank() && !active.tracker.isClosed()) {
                                try {
                                    active.tracker.animate(idleAnim);
                                } catch (final Throwable t) {
                                    logger.debug("Failed to resume idle animation on Aetheryte {}", aetheryteId, t);
                                }
                            }
                        }
                );
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
