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
package net.sabafly.clickportals.model;

import java.util.UUID;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.variation.AetheryteVariation;
import org.bukkit.World;

/**
 * No-op implementation of {@link AetheryteModelService} used when BetterModel is absent or disabled (#46).
 */
public final class NoopAetheryteModelService implements AetheryteModelService {

    public static final NoopAetheryteModelService INSTANCE = new NoopAetheryteModelService();

    @Override
    public void registerAetheryte(final Aetheryte aetheryte, final AetheryteVariation variation, final World world) {
    }

    @Override
    public void unregisterAetheryte(final UUID aetheryteId) {
    }

    @Override
    public void triggerAnimation(final UUID aetheryteId, final ModelAnimationTrigger trigger) {
    }

    @Override
    public boolean hasModel(final UUID aetheryteId) {
        return false;
    }

    @Override
    public void destroyAll() {
    }
}
