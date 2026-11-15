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
package net.sabafly.clickportals.listener;

import net.sabafly.clickportals.update.UpdateChecker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * Notifies permitted administrators shortly after joining if a plugin update is available (#76).
 */
public final class UpdateNotifyListener implements Listener {

    private static final long JOIN_NOTIFY_DELAY_TICKS = 40L;

    private final Plugin plugin;
    private final UpdateChecker updateChecker;

    public UpdateNotifyListener(final Plugin plugin, final UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.updateChecker = updateChecker;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (player == null || player.getScheduler() == null) {
            return;
        }
        player.getScheduler().runDelayed(
                plugin,
                task -> updateChecker.notifyPlayerIfAvailable(player),
                null,
                JOIN_NOTIFY_DELAY_TICKS
        );
    }
}
