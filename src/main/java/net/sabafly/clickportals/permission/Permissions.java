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
package net.sabafly.clickportals.permission;

import java.util.UUID;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteNetwork;
import org.bukkit.permissions.Permissible;

/**
 * Permission node constants and authorization helpers for ClickPortals (#17).
 */
public final class Permissions {

    /**
     * Allows using Aetheryte warps (default: {@code true}).
     */
    public static final String USE = "clickportals.use";

    /**
     * Allows placing new Aetherytes (default: {@code true}).
     */
    public static final String PLACE = "clickportals.place";

    /**
     * Allows removing Aetherytes owned by other players (default: {@code op}).
     */
    public static final String REMOVE_ANY = "clickportals.remove.any";

    /**
     * Allows renaming Aetherytes owned by other players (default: {@code op}) (#57).
     */
    public static final String RENAME_ANY = "clickportals.rename.any";

    /**
     * Allows using {@code /clickportals give} (default: {@code op}).
     */
    public static final String COMMAND_GIVE = "clickportals.command.give";

    /**
     * Allows using {@code /clickportals reload} (default: {@code op}).
     */
    public static final String COMMAND_RELOAD = "clickportals.command.reload";

    /**
     * Allows using {@code /clickportals aetheryte} (default: {@code op}) (#74).
     */
    public static final String COMMAND_AETHERYTE = "clickportals.command.aetheryte";

    /**
     * Allows bypassing per-player Aetheryte placement limits and placement cooldowns (default: {@code op}).
     */
    public static final String BYPASS_LIMIT = "clickportals.bypass.limit";

    /**
     * Allows bypassing minimum distance restrictions between Aetherytes (default: {@code op}).
     */
    public static final String BYPASS_DISTANCE = "clickportals.bypass.distance";

    /**
     * Allows bypassing Aetheryte warp costs (default: {@code op}).
     */
    public static final String BYPASS_COST_WARP = "clickportals.bypass.cost.warp";

    /**
     * Allows bypassing placement costs (default: {@code op}) (#15).
     */
    public static final String BYPASS_COST_PLACE = "clickportals.bypass.cost.place";

    /**
     * Allows bypassing per-dimension Aetheryte placement and usage restrictions (default: {@code op}) (#63).
     */
    public static final String BYPASS_DIMENSION = "clickportals.bypass.dimension";

    /**
     * Allows seeing and warping to private/invite-only Aetherytes regardless of visibility restrictions (default: {@code op}) (#24).
     */
    public static final String BYPASS_VISIBILITY = "clickportals.bypass.visibility";

    /**
     * Allows receiving in-game update notifications when a new ClickPortals release is available on Modrinth (default: {@code op}) (#76).
     */
    public static final String NOTIFY_UPDATE = "clickportals.notify.update";

    /**
     * Grants all ClickPortals administrative and user permissions (default: {@code op}).
     */
    public static final String ADMIN = "clickportals.admin";

    private Permissions() {
    }

    /**
     * Checks whether the given permissible is allowed to use Aetheryte warps.
     */
    public static boolean canUse(final Permissible permissible) {
        return permissible.hasPermission(USE);
    }

    /**
     * Checks whether the given permissible is allowed to place Aetherytes.
     */
    public static boolean canPlace(final Permissible permissible) {
        return permissible.hasPermission(PLACE);
    }

    /**
     * Checks whether the given permissible with {@code playerId} is allowed to remove the specified Aetheryte.
     */
    public static boolean canRemove(final Permissible permissible, final UUID playerId, final Aetheryte aetheryte) {
        return aetheryte.ownerId().equals(playerId)
                || permissible.hasPermission(REMOVE_ANY);
    }

    /**
     * Checks whether the given permissible with {@code playerId} is allowed to rename the specified Aetheryte (#57).
     */
    public static boolean canRename(final Permissible permissible, final UUID playerId, final Aetheryte aetheryte) {
        return aetheryte.ownerId().equals(playerId)
                || permissible.hasPermission(RENAME_ANY);
    }

    /**
     * Checks whether the given permissible holds full administrative privileges (#57).
     */
    public static boolean canAdmin(final Permissible permissible) {
        return permissible.hasPermission(ADMIN);
    }

    /**
     * Checks whether the given permissible is allowed to execute {@code /clickportals give}.
     */
    public static boolean canGive(final Permissible permissible) {
        return permissible.hasPermission(COMMAND_GIVE);
    }

    /**
     * Checks whether the given permissible is allowed to execute {@code /clickportals reload}.
     */
    public static boolean canReload(final Permissible permissible) {
        return permissible.hasPermission(COMMAND_RELOAD);
    }

    /**
     * Checks whether the given permissible is allowed to execute {@code /clickportals aetheryte} (#74).
     */
    public static boolean canManageAetherytes(final Permissible permissible) {
        return permissible.hasPermission(COMMAND_AETHERYTE);
    }

    /**
     * Checks whether the given permissible has access to any {@code /clickportals} subcommand.
     */
    public static boolean canUseCommand(final Permissible permissible) {
        return canGive(permissible) || canReload(permissible) || canManageAetherytes(permissible);
    }

    /**
     * Checks whether the given permissible bypasses per-player placement limits.
     */
    public static boolean canBypassLimit(final Permissible permissible) {
        return permissible.hasPermission(BYPASS_LIMIT);
    }

    /**
     * Checks whether the given permissible bypasses minimum placement distance restrictions.
     */
    public static boolean canBypassPlacementDistance(final Permissible permissible) {
        return permissible.hasPermission(BYPASS_LIMIT) || permissible.hasPermission(BYPASS_DISTANCE);
    }

    /**
     * Checks whether the given permissible bypasses warp costs.
     */
    public static boolean canBypassWarpCost(final Permissible permissible) {
        return permissible.hasPermission(BYPASS_COST_WARP);
    }

    /**
     * Checks whether the given permissible bypasses placement costs.
     */
    public static boolean canBypassPlaceCost(final Permissible permissible) {
        return permissible.hasPermission(BYPASS_COST_PLACE);
    }

    /**
     * Checks whether the given permissible bypasses per-dimension placement and usage restrictions (#63).
     */
    public static boolean canBypassDimension(final Permissible permissible) {
        return permissible.hasPermission(BYPASS_DIMENSION);
    }

    /**
     * Checks whether the given permissible should receive update notifications (#76).
     */
    public static boolean canNotifyUpdate(final Permissible permissible) {
        return permissible.hasPermission(NOTIFY_UPDATE);
    }

    /**
     * Checks whether the given permissible bypasses Aetheryte visibility restrictions (#24).
     */
    public static boolean canBypassVisibility(final Permissible permissible) {
        return permissible.hasPermission(BYPASS_VISIBILITY);
    }

    /**
     * Checks whether the given permissible with {@code playerId} can view and warp to {@code aetheryte} (#24).
     */
    public static boolean canAccessVisibility(final Permissible permissible, final UUID playerId, final Aetheryte aetheryte) {
        return aetheryte.isAccessibleTo(playerId) || canBypassVisibility(permissible);
    }

    /**
     * Checks whether the given permissible with {@code playerId} can view and warp to {@code network} (#27).
     */
    public static boolean canAccessVisibility(final Permissible permissible, final UUID playerId, final AetheryteNetwork network) {
        return network.isAccessibleTo(playerId, canBypassVisibility(permissible));
    }

    /**
     * Checks whether the given permissible with {@code playerId} can manage visibility and invitees for {@code aetheryte} (#24).
     */
    public static boolean canManageVisibility(final Permissible permissible, final UUID playerId, final Aetheryte aetheryte) {
        return aetheryte.ownerId().equals(playerId) || canAdmin(permissible);
    }

    /**
     * Checks whether the given permissible with {@code playerId} can manage {@code network} (#27).
     */
    public static boolean canManageNetwork(final Permissible permissible, final UUID playerId, final AetheryteNetwork network) {
        return network.ownerId().equals(playerId) || canAdmin(permissible);
    }

    /**
     * Checks whether the given permissible with {@code playerId} can create/manage hosted networks on {@code hub} (#27).
     */
    public static boolean canManageHub(final Permissible permissible, final UUID playerId, final Aetheryte hub) {
        return hub.ownerId().equals(playerId) || canAdmin(permissible);
    }
}
