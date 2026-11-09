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

    public BlockPosKey posKey() {
        return new BlockPosKey(worldId, blockX, blockY, blockZ);
    }

    public Location blockLocation(final World world) {
        return new Location(world, blockX, blockY, blockZ);
    }

    public Location teleportDestination(final World world, final float yaw, final float pitch) {
        return new Location(world, blockX + 0.5, blockY + 1.0, blockZ + 0.5, yaw, pitch);
    }
}
