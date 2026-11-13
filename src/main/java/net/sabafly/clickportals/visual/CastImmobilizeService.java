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
package net.sabafly.clickportals.visual;

import org.bukkit.entity.Player;

/**
 * Service for temporarily immobilizing players while casting an Aetheryte warp (#67).
 */
public interface CastImmobilizeService {

    /**
     * Immobilizes {@code player} at their current position while allowing head rotation.
     */
    void immobilize(Player player);

    /**
     * Releases any active immobilization state for {@code player} and restores their client state.
     */
    void release(Player player);

    /**
     * Releases all currently immobilized players (e.g. on plugin reload).
     */
    void releaseAll();

    /**
     * Releases all players and unregisters any underlying packet listeners on plugin shutdown.
     */
    void destroy();
}
