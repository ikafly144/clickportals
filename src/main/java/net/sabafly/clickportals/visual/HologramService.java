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

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.config.PluginConfig;
import org.bukkit.entity.Player;

/**
 * Abstraction for managing client-side visual entities (`BLOCK_DISPLAY`, `TEXT_DISPLAY`, and `INTERACTION`)
 * representing placed Aetherytes (#6).
 */
public interface HologramService {

    /**
     * Updates runtime visual settings from a reloaded {@link PluginConfig}.
     */
    default void updateSettings(final PluginConfig config) {
    }

    /**
     * Spawns or updates the client-side visual and interaction entities for the given Aetheryte
     * for all players in the same world.
     */
    void showAetheryte(Aetheryte aetheryte);

    /**
     * Destroys the client-side visual and interaction entities for the given Aetheryte ID for all online players.
     */
    void hideAetheryte(UUID aetheryteId);

    /**
     * Refreshes all client-side visual and interaction entities in the player's current world for a single player.
     */
    void refreshPlayer(Player player, Collection<Aetheryte> worldAetherytes);

    /**
     * Resolves a client-side virtual entity ID to the corresponding {@link Aetheryte} UUID in O(1) time (#6, #20).
     */
    default Optional<UUID> resolveInteractionTarget(final int entityId) {
        return Optional.empty();
    }

    /**
     * Registers a callback invoked when a player right-clicks a virtual Aetheryte interaction entity.
     */
    default void setInteractionHandler(final BiConsumer<Player, UUID> handler) {
    }

    /**
     * Cleans up per-player interaction state when a player disconnects.
     */
    default void removePlayer(final UUID playerId) {
    }

    /**
     * Destroys all active client-side entities and unregisters any packet listeners when the plugin disables.
     */
    void destroyAll();
}
