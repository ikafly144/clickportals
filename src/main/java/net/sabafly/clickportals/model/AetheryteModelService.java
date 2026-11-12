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
import java.util.UUID;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.variation.AetheryteVariation;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Service managing external 3D animated models (e.g. BetterModel) attached to Aetherytes (#46).
 */
public interface AetheryteModelService {

    /**
     * Initializes and registers an external 3D model for the given Aetheryte if configured in its variation.
     */
    void registerAetheryte(Aetheryte aetheryte, AetheryteVariation variation, World world);

    /**
     * Unregisters and despawns any external 3D model associated with the given Aetheryte ID.
     */
    void unregisterAetheryte(UUID aetheryteId);

    /**
     * Triggers an event-driven animation on the Aetheryte's model tracker, resuming idle on completion.
     */
    void triggerAnimation(UUID aetheryteId, ModelAnimationTrigger trigger);

    /**
     * Returns whether an external 3D model is active for the given Aetheryte ID.
     */
    boolean hasModel(UUID aetheryteId);

    /**
     * Synchronizes model visibility for a single player: spawns models of Aetherytes in the player's current
     * world and removes models from other worlds.
     *
     * @param player the target player
     * @param worldAetherytes the Aetherytes registered in the player's current world
     */
    default void refreshPlayer(final Player player, final Collection<Aetheryte> worldAetherytes) {
    }

    /**
     * Removes the given player from all active model trackers (e.g. on quit).
     *
     * @param player the player to remove
     */
    default void removePlayer(final Player player) {
    }

    /**
     * Destroys and cleans up all active model trackers across all worlds.
     */
    void destroyAll();
}
