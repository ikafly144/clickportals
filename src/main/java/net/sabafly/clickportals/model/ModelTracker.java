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

import kr.toxicity.model.api.animation.AnimationModifier;

/**
 * Handle wrapping an underlying 3D model tracker (e.g. BetterModel {@code Tracker}) (#46).
 */
public interface ModelTracker extends AutoCloseable {

    /**
     * Plays the specified animation on the tracker.
     */
    boolean animate(String animationName);

    /**
     * Plays a one-shot animation with modifier, invoking the callback when finished.
     */
    boolean animate(String animationName, AnimationModifier modifier, Runnable onEnd);

    /**
     * Returns whether this tracker has been closed or despawned.
     */
    boolean isClosed();

    /**
     * Closes and despawns the model tracker.
     */
    @Override
    void close();
}
