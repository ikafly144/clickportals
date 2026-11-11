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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.variation.VariationRegistry;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PacketEventsHologramServiceTest {

    @Test
    void serviceConstructsWithExternalModelPredicateAndHandlesLifecycleSafely() {
        final AtomicBoolean checkedModel = new AtomicBoolean(false);
        final UUID targetId = UUID.randomUUID();

        final PacketEventsHologramService service = new PacketEventsHologramService(
                48.0,
                VariationRegistry.defaults(),
                Messages.defaults(),
                id -> {
                    checkedModel.set(true);
                    return id.equals(targetId);
                }
        );

        final Aetheryte aetheryte = new Aetheryte(
                targetId,
                "Test Aetheryte",
                UUID.randomUUID(),
                "world",
                10,
                64,
                20,
                UUID.randomUUID(),
                System.currentTimeMillis(),
                "default"
        );

        // Safe in test environment without PacketEvents runtime
        service.showAetheryte(aetheryte);
        service.hideAetheryte(targetId);
        service.refreshPlayer(org.mockito.Mockito.mock(Player.class), List.of(aetheryte));
        service.removePlayer(UUID.randomUUID());
        service.destroyAll();

        assertThat(service.resolveInteractionTarget(9999)).isEmpty();
    }

    @Test
    void sendPlacementCooldownIsSafeInTestEnvironment() {
        final Player player = org.mockito.Mockito.mock(Player.class);
        // Does not throw even when PacketEvents API is not initialized
        PacketEventsHologramService.sendPlacementCooldown(player, 60);
        PacketEventsHologramService.sendPlacementCooldown(player, -1);
    }
}
