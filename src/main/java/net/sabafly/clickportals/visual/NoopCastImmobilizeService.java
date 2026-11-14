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
 * No-op {@link CastImmobilizeService} implementation used in tests or fallback environments (#67).
 */
public final class NoopCastImmobilizeService implements CastImmobilizeService {

    /**
     * Shared singleton instance.
     */
    public static final NoopCastImmobilizeService INSTANCE = new NoopCastImmobilizeService();

    private NoopCastImmobilizeService() {
    }

    @Override
    public void immobilize(final Player player) {
        // No-op
    }

    @Override
    public void release(final Player player) {
        // No-op
    }

    @Override
    public void releaseAll() {
        // No-op
    }

    @Override
    public void destroy() {
        // No-op
    }
}
