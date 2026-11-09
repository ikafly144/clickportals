package net.sabafly.clickportals.visual;

import java.util.Collection;
import java.util.UUID;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import org.bukkit.entity.Player;

/**
 * Abstraction for rendering client-side holograms above placed Aetherytes (#6).
 */
public interface HologramService {

    /**
     * Spawns or updates the hologram for a single Aetheryte to all online players in its world.
     */
    void showAetheryte(Aetheryte aetheryte);

    /**
     * Destroys the hologram for the given Aetheryte ID for all online players.
     */
    void hideAetheryte(UUID aetheryteId);

    /**
     * Refreshes all Aetheryte holograms in the player's current world for that player.
     */
    void refreshPlayer(Player player, Collection<Aetheryte> worldAetherytes);

    /**
     * Destroys all active holograms for all online players.
     */
    void destroyAll();
}
