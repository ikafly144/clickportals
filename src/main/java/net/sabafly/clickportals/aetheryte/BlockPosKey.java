package net.sabafly.clickportals.aetheryte;

import java.util.UUID;

/**
 * Immutable block position key for O(1) lookup of Aetherytes by coordinates.
 */
public record BlockPosKey(
        UUID worldId,
        int x,
        int y,
        int z
) {
}
