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
package net.sabafly.clickportals.sound;

/**
 * Sound event categories configurable globally and per Aetheryte variation (#77).
 */
public enum SoundEventType {
    PLACE("place"),
    DESTROY("destroy"),
    INTERACT("interact"),
    WARP_CAST_START("warp-cast-start"),
    WARP_CAST_CANCEL("warp-cast-cancel"),
    WARP_DEPART("warp-depart"),
    WARP_ARRIVE("warp-arrive"),
    ERROR("error");

    private final String configKey;

    SoundEventType(final String configKey) {
        this.configKey = configKey;
    }

    /**
     * Returns the YAML key name under {@code sounds}.
     */
    public String configKey() {
        return configKey;
    }
}
