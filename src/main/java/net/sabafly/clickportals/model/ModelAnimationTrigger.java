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

/**
 * Event-driven animation triggers for Aetheryte 3D models (#46).
 */
public enum ModelAnimationTrigger {

    /**
     * Continuous looping idle animation played while the Aetheryte is stationary.
     */
    IDLE,

    /**
     * One-shot animation played when a player interacts with the Aetheryte to open the teleport dialog.
     */
    ON_INTERACT,

    /**
     * One-shot animation played on the source Aetheryte when a player teleports away.
     */
    ON_WARP_DEPART,

    /**
     * One-shot animation played on the destination Aetheryte when a player arrives.
     */
    ON_WARP_ARRIVE
}
