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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.util.LogManager;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes.Property;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes.PropertyModifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PacketEventsCastImmobilizeServiceTest {

    private @Nullable PacketEventsAPI<?> previousApi;
    private List<PacketWrapper<?>> sentPackets;
    private Map<com.github.retrooper.packetevents.protocol.attribute.Attribute, AttributeInstance> mockedAttributes;
    private PacketEventsCastImmobilizeService service;
    private Player player;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        previousApi = PacketEvents.getAPI();
        final PacketEventsAPI<?> mockApi = mock(PacketEventsAPI.class);
        final ServerManager mockServerManager = mock(ServerManager.class);
        when(mockServerManager.getVersion()).thenReturn(ServerVersion.getLatest());
        when(mockApi.getSettings()).thenReturn(new PacketEventsSettings());
        when(mockApi.getServerManager()).thenReturn(mockServerManager);
        when(mockApi.getLogManager()).thenReturn(mock(LogManager.class));
        PacketEvents.setAPI(mockApi);

        mockedAttributes = new HashMap<>();
        final List<PacketEventsCastImmobilizeService.AttributeMapping> testMappings = List.of(
                new PacketEventsCastImmobilizeService.AttributeMapping(
                        p -> mockedAttributes.get(Attributes.MOVEMENT_SPEED),
                        Attributes.MOVEMENT_SPEED,
                        0.1,
                        PacketEventsCastImmobilizeService.FROZEN_MOVEMENT_SPEED
                ),
                new PacketEventsCastImmobilizeService.AttributeMapping(
                        p -> mockedAttributes.get(Attributes.JUMP_STRENGTH),
                        Attributes.JUMP_STRENGTH,
                        0.42,
                        0.0
                ),
                new PacketEventsCastImmobilizeService.AttributeMapping(
                        p -> mockedAttributes.get(Attributes.SNEAKING_SPEED),
                        Attributes.SNEAKING_SPEED,
                        0.3,
                        0.0
                ),
                new PacketEventsCastImmobilizeService.AttributeMapping(
                        p -> mockedAttributes.get(Attributes.WATER_MOVEMENT_EFFICIENCY),
                        Attributes.WATER_MOVEMENT_EFFICIENCY,
                        0.0,
                        0.0
                ),
                new PacketEventsCastImmobilizeService.AttributeMapping(
                        p -> mockedAttributes.get(Attributes.FLYING_SPEED),
                        Attributes.FLYING_SPEED,
                        0.4,
                        0.0
                )
        );

        sentPackets = new ArrayList<>();
        service = new PacketEventsCastImmobilizeService((p, wrapper) -> sentPackets.add(wrapper), () -> testMappings);

        playerId = UUID.randomUUID();
        player = mock(Player.class);
        final World world = mock(World.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getEntityId()).thenReturn(42);
        when(player.getLocation()).thenReturn(new org.bukkit.Location(world, 10.5, 64.0, -20.25, 90.0f, 15.0f));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.isMarkedInvulnerable()).thenReturn(false);
        when(player.isFlying()).thenReturn(false);
        when(player.getAllowFlight()).thenReturn(false);
        when(player.getFlySpeed()).thenReturn(0.1f);
        when(player.getWalkSpeed()).thenReturn(0.2f);
        when(player.getVelocity()).thenReturn(new Vector(0.25, 0.42, -0.1));

        mockAttribute(Attributes.MOVEMENT_SPEED, 0.1, List.of(
                new AttributeModifier(
                        new NamespacedKey("test", "swiftness"),
                        0.2,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1
                )
        ));
        mockAttribute(Attributes.JUMP_STRENGTH, 0.42, List.of());
        mockAttribute(Attributes.SNEAKING_SPEED, 0.3, List.of());
        mockAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY, 0.0, List.of());
        mockAttribute(Attributes.FLYING_SPEED, 0.4, List.of());
    }

    @AfterEach
    void tearDown() {
        PacketEvents.setAPI(previousApi);
    }

    private void mockAttribute(
            final com.github.retrooper.packetevents.protocol.attribute.Attribute attribute,
            final double baseValue,
            final List<AttributeModifier> modifiers
    ) {
        final AttributeInstance instance = mock(AttributeInstance.class);
        when(instance.getBaseValue()).thenReturn(baseValue);
        when(instance.getModifiers()).thenReturn(modifiers);
        mockedAttributes.put(attribute, instance);
    }

    @Test
    void immobilizeFreezesMovementAttributesWithoutZoomAndReleaseRestoresSnapshot() {
        service.immobilize(player);

        assertThat(service.isImmobilized(playerId)).isTrue();
        assertThat(sentPackets).hasSize(3);

        final WrapperPlayServerUpdateAttributes frozenAttrs = (WrapperPlayServerUpdateAttributes) sentPackets.get(0);
        assertThat(frozenAttrs.getEntityId()).isEqualTo(42);
        assertThat(frozenAttrs.getProperties())
                .anySatisfy(p -> {
                    assertThat(p.getAttribute()).isEqualTo(Attributes.MOVEMENT_SPEED);
                    assertThat(p.getValue()).isEqualTo(PacketEventsCastImmobilizeService.FROZEN_MOVEMENT_SPEED);
                    assertThat(p.getModifiers()).isEmpty();
                })
                .anySatisfy(p -> {
                    assertThat(p.getAttribute()).isEqualTo(Attributes.JUMP_STRENGTH);
                    assertThat(p.getValue()).isZero();
                });

        final WrapperPlayServerPlayerAbilities frozenAbilities = (WrapperPlayServerPlayerAbilities) sentPackets.get(1);
        assertThat(frozenAbilities.getFlySpeed()).isZero();
        assertThat(frozenAbilities.getFOVModifier()).isEqualTo(PacketEventsCastImmobilizeService.FROZEN_FOV_MODIFIER);

        final WrapperPlayServerEntityVelocity zeroVel = (WrapperPlayServerEntityVelocity) sentPackets.get(2);
        assertThat(zeroVel.getVelocity().getX()).isZero();
        assertThat(zeroVel.getVelocity().getY()).isZero();
        assertThat(zeroVel.getVelocity().getZ()).isZero();

        sentPackets.clear();
        service.release(player);

        assertThat(service.isImmobilized(playerId)).isFalse();
        assertThat(sentPackets).hasSize(2);

        final WrapperPlayServerUpdateAttributes restoredAttrs = (WrapperPlayServerUpdateAttributes) sentPackets.get(0);
        assertThat(restoredAttrs.getProperties())
                .anySatisfy(p -> {
                    assertThat(p.getAttribute()).isEqualTo(Attributes.MOVEMENT_SPEED);
                    assertThat(p.getValue()).isEqualTo(0.1);
                    assertThat(p.getModifiers()).hasSize(1);
                    assertThat(p.getModifiers().getFirst().getName()).isEqualTo(new ResourceLocation("test", "swiftness"));
                    assertThat(p.getModifiers().getFirst().getAmount()).isEqualTo(0.2);
                })
                .anySatisfy(p -> {
                    assertThat(p.getAttribute()).isEqualTo(Attributes.JUMP_STRENGTH);
                    assertThat(p.getValue()).isEqualTo(0.42);
                });

        final WrapperPlayServerPlayerAbilities restoredAbilities = (WrapperPlayServerPlayerAbilities) sentPackets.get(1);
        assertThat(restoredAbilities.getFlySpeed()).isEqualTo(0.05f);
        assertThat(restoredAbilities.getFOVModifier()).isEqualTo(0.1f);
    }

    @Test
    void clampClientPositionBlocksHorizontalAndUpwardMovementWhileAllowingGravityAndRotation() {
        service.immobilize(player);

        // Attempt horizontal + jump move while rotating head
        final Location moveUp = new Location(new Vector3d(11.2, 64.42, -19.8), 120.0f, -30.0f);
        assertThat(service.clampClientPosition(playerId, moveUp)).isTrue();
        assertThat(moveUp.getPosition()).isEqualTo(new Vector3d(10.5, 64.0, -20.25));
        assertThat(moveUp.getYaw()).isEqualTo(120.0f);
        assertThat(moveUp.getPitch()).isEqualTo(-30.0f);

        // Gravity downward is permitted and updates anchorY
        final Location fallDown = new Location(new Vector3d(10.7, 63.5, -20.0), 120.0f, -30.0f);
        assertThat(service.clampClientPosition(playerId, fallDown)).isTrue();
        assertThat(fallDown.getPosition()).isEqualTo(new Vector3d(10.5, 63.5, -20.25));

        // Pure rotation at updated anchor is untouched
        final Location stationary = new Location(new Vector3d(10.5, 63.5, -20.25), 140.0f, 10.0f);
        assertThat(service.clampClientPosition(playerId, stationary)).isFalse();
    }

    @Test
    void externalDisplacementGraceWindowPassesThroughKnockbackAndUpdatesAnchor() {
        service.immobilize(player);
        service.markExternalDisplacement(playerId);

        final Location knockedBack = new Location(new Vector3d(12.0, 64.3, -18.5), 0.0f, 0.0f);
        assertThat(service.clampClientPosition(playerId, knockedBack)).isFalse();
        assertThat(knockedBack.getPosition()).isEqualTo(new Vector3d(12.0, 64.3, -18.5));
    }

    @Test
    void interceptOutgoingPacketsRefreezesMidCastUpdatesAndRestoresLatestOnRelease() {
        service.immobilize(player);
        sentPackets.clear();

        final List<Property> serverUpdate = new ArrayList<>();
        serverUpdate.add(new Property(
                Attributes.MOVEMENT_SPEED,
                0.15,
                List.of(new PropertyModifier(new ResourceLocation("test", "boots"), 0.05, PropertyModifier.Operation.ADDITION))
        ));

        assertThat(service.interceptOutgoingAttributes(playerId, 42, serverUpdate)).isTrue();
        assertThat(serverUpdate.getFirst().getValue()).isEqualTo(PacketEventsCastImmobilizeService.FROZEN_MOVEMENT_SPEED);
        assertThat(serverUpdate.getFirst().getModifiers()).isEmpty();

        // Attributes for other entities or other recipients must not be intercepted
        final List<Property> otherEntityUpdate = new ArrayList<>(List.of(new Property(Attributes.MOVEMENT_SPEED, 0.25, List.of())));
        assertThat(service.interceptOutgoingAttributes(playerId, 999, otherEntityUpdate)).isFalse();
        assertThat(otherEntityUpdate.getFirst().getValue()).isEqualTo(0.25);

        final WrapperPlayServerPlayerAbilities abilitiesUpdate = new WrapperPlayServerPlayerAbilities(
                false, true, true, false, 0.08f, 0.12f
        );
        assertThat(service.interceptOutgoingAbilities(playerId, abilitiesUpdate)).isTrue();
        assertThat(abilitiesUpdate.getFlySpeed()).isZero();
        assertThat(abilitiesUpdate.getFOVModifier()).isEqualTo(PacketEventsCastImmobilizeService.FROZEN_FOV_MODIFIER);

        service.release(player);
        final WrapperPlayServerUpdateAttributes restoredAttrs = (WrapperPlayServerUpdateAttributes) sentPackets.get(0);
        assertThat(restoredAttrs.getProperties())
                .anySatisfy(p -> {
                    assertThat(p.getAttribute()).isEqualTo(Attributes.MOVEMENT_SPEED);
                    assertThat(p.getValue()).isEqualTo(0.15);
                    assertThat(p.getModifiers()).hasSize(1);
                });
        final WrapperPlayServerPlayerAbilities restoredAbilities = (WrapperPlayServerPlayerAbilities) sentPackets.get(1);
        assertThat(restoredAbilities.isFlying()).isTrue();
        assertThat(restoredAbilities.getFlySpeed()).isEqualTo(0.08f);
        assertThat(restoredAbilities.getFOVModifier()).isEqualTo(0.12f);
    }

    @Test
    void shouldCancelSprintActionOnlyWhileImmobilized() {
        assertThat(service.shouldCancelEntityAction(playerId, WrapperPlayClientEntityAction.Action.START_SPRINTING)).isFalse();

        service.immobilize(player);
        assertThat(service.shouldCancelEntityAction(playerId, WrapperPlayClientEntityAction.Action.START_SPRINTING)).isTrue();
        assertThat(service.shouldCancelEntityAction(playerId, WrapperPlayClientEntityAction.Action.START_SNEAKING)).isFalse();
    }

    @Test
    void detectsExternalDisplacementVelocityIncludingPureUpwardKnockback() {
        assertThat(PacketEventsCastImmobilizeService.isExternalDisplacementVelocity(new Vector3d(0.0, 0.0, 0.0))).isFalse();
        assertThat(PacketEventsCastImmobilizeService.isExternalDisplacementVelocity(new Vector3d(0.0, -0.4, 0.0))).isFalse();
        assertThat(PacketEventsCastImmobilizeService.isExternalDisplacementVelocity(new Vector3d(0.0, 0.35, 0.0))).isTrue();
        assertThat(PacketEventsCastImmobilizeService.isExternalDisplacementVelocity(new Vector3d(0.2, 0.0, 0.0))).isTrue();
        assertThat(PacketEventsCastImmobilizeService.isExternalDisplacementVelocity(new Vector3d(0.0, 0.0, -0.2))).isTrue();
    }
}
