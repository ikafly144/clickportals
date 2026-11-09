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
import java.util.UUID;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import org.bukkit.entity.Player;

/**
 * No-op fallback implementation of {@link HologramService} used when PacketEvents is absent or disabled.
 */
public final class NoopHologramService implements HologramService {

    public static final NoopHologramService INSTANCE = new NoopHologramService();

    private NoopHologramService() {
    }

    @Override
    public void showAetheryte(final Aetheryte aetheryte) {
    }

    @Override
    public void hideAetheryte(final UUID aetheryteId) {
    }

    @Override
    public void refreshPlayer(final Player player, final Collection<Aetheryte> worldAetherytes) {
    }

    @Override
    public void destroyAll() {
    }
}
