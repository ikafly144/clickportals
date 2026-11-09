package net.sabafly.clickportals.aetheryte;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Immutable domain model representing a placed Aetheryte (Lodestone warp point).
 */
public record Aetheryte(
        UUID id,
        String name,
        UUID worldId,
        String worldName,
        int blockX,
        int blockY,
        int blockZ,
        UUID ownerId,
        long createdAt
) {

    /**
     * Returns the {@link BlockPosKey} corresponding to this Aetheryte's block coordinates.
     */
    public BlockPosKey posKey() {
        return new BlockPosKey(worldId, blockX, blockY, blockZ);
    }

    /**
     * Returns a Bukkit {@link Location} pointing to the Aetheryte's Lodestone block.
     */
    public Location blockLocation(final World world) {
        return new Location(world, blockX, blockY, blockZ);
    }

    /**
     * Returns the safe landing {@link Location} centered on top of the Aetheryte block.
     */
    public Location teleportDestination(final World world, final float yaw, final float pitch) {
        return new Location(world, blockX + 0.5, blockY + 1.0, blockZ + 0.5, yaw, pitch);
    }
}
