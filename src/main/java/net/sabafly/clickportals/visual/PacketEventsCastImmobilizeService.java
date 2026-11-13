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

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attribute;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes.Property;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes.PropertyModifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

/**
 * PacketEvents-backed {@link CastImmobilizeService} that freezes player movement via
 * client-side attribute/ability suppression and server-side position packet clamping (#67).
 *
 * <p>Matching {@code MOVEMENT_SPEED = 1.0e-5} with {@code fovModifier = 1.0e-5f} preserves a 1.0x
 * vanilla FOV while eliminating ground movement, and keeps the player standing without action-bar
 * dismount prompts.
 */
public final class PacketEventsCastImmobilizeService implements CastImmobilizeService {

    static final double FROZEN_MOVEMENT_SPEED = 1.0e-5;
    static final float FROZEN_FOV_MODIFIER = 1.0e-5f;
    static final long EXTERNAL_DISPLACEMENT_GRACE_MS = 500L;

    record AttributeMapping(
            Function<Player, @Nullable AttributeInstance> instanceLookup,
            Attribute packetAttribute,
            double defaultValue,
            double frozenValue
    ) {
        static AttributeMapping of(
                final org.bukkit.attribute.Attribute bukkitAttribute,
                final Attribute packetAttribute,
                final double frozenValue
        ) {
            return new AttributeMapping(
                    player -> player.getAttribute(bukkitAttribute),
                    packetAttribute,
                    bukkitAttribute.getDefaultValue(),
                    frozenValue
            );
        }
    }

    private static final class DefaultAttributeMappings {
        static final List<AttributeMapping> TRACKED = List.of(
                AttributeMapping.of(org.bukkit.attribute.Attribute.MOVEMENT_SPEED, Attributes.MOVEMENT_SPEED, FROZEN_MOVEMENT_SPEED),
                AttributeMapping.of(org.bukkit.attribute.Attribute.JUMP_STRENGTH, Attributes.JUMP_STRENGTH, 0.0),
                AttributeMapping.of(org.bukkit.attribute.Attribute.SNEAKING_SPEED, Attributes.SNEAKING_SPEED, 0.0),
                AttributeMapping.of(org.bukkit.attribute.Attribute.WATER_MOVEMENT_EFFICIENCY, Attributes.WATER_MOVEMENT_EFFICIENCY, 0.0),
                AttributeMapping.of(org.bukkit.attribute.Attribute.FLYING_SPEED, Attributes.FLYING_SPEED, 0.0)
        );
    }

    record PropertySnapshot(
            Attribute attribute,
            double value,
            List<PropertyModifier> modifiers
    ) {
        Property toPacketProperty() {
            return new Property(attribute, value, List.copyOf(modifiers));
        }
    }

    record AbilitiesSnapshot(
            boolean godMode,
            boolean flying,
            boolean flightAllowed,
            boolean creativeMode,
            float flySpeed,
            float fovModifier
    ) {
    }

    static final class ImmobilizeSession {
        final UUID playerId;
        final int entityId;
        double anchorX;
        double anchorY;
        double anchorZ;
        long externalDisplacementUntilMillis;
        final Map<Attribute, PropertySnapshot> realAttributes;
        AbilitiesSnapshot realAbilities;

        ImmobilizeSession(
                final UUID playerId,
                final int entityId,
                final double anchorX,
                final double anchorY,
                final double anchorZ,
                final Map<Attribute, PropertySnapshot> realAttributes,
                final AbilitiesSnapshot realAbilities
        ) {
            this.playerId = playerId;
            this.entityId = entityId;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.realAttributes = new LinkedHashMap<>(realAttributes);
            this.realAbilities = realAbilities;
        }
    }

    private final Map<UUID, ImmobilizeSession> sessionsByPlayerId = new ConcurrentHashMap<>();
    private final Map<Integer, ImmobilizeSession> sessionsByEntityId = new ConcurrentHashMap<>();
    private final BiConsumer<Player, PacketWrapper<?>> packetSender;
    private final java.util.function.Supplier<List<AttributeMapping>> attributeMappingsSupplier;
    private volatile @Nullable Map<Attribute, Double> frozenValuesCache;
    private @Nullable PacketListenerCommon registeredListener;

    /**
     * Creates a new {@link PacketEventsCastImmobilizeService} wired to the live PacketEvents runtime.
     */
    public PacketEventsCastImmobilizeService() {
        this(PacketEventsCastImmobilizeService::sendSilentPacket, () -> DefaultAttributeMappings.TRACKED);
        registerPacketListenerIfReady();
    }

    /**
     * Package-private constructor for unit tests with a custom packet sender and attribute mappings.
     */
    PacketEventsCastImmobilizeService(
            final BiConsumer<Player, PacketWrapper<?>> packetSender,
            final java.util.function.Supplier<List<AttributeMapping>> attributeMappingsSupplier
    ) {
        this.packetSender = packetSender;
        this.attributeMappingsSupplier = attributeMappingsSupplier;
    }

    private List<AttributeMapping> trackedAttributes() {
        return attributeMappingsSupplier.get();
    }

    private Map<Attribute, Double> frozenAttributeValues() {
        Map<Attribute, Double> cached = this.frozenValuesCache;
        if (cached == null) {
            final Map<Attribute, Double> map = new LinkedHashMap<>();
            for (final AttributeMapping mapping : trackedAttributes()) {
                map.put(mapping.packetAttribute(), mapping.frozenValue());
            }
            cached = Map.copyOf(map);
            this.frozenValuesCache = cached;
        }
        return cached;
    }

    private void registerPacketListenerIfReady() {
        final PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null) {
            return;
        }
        final PacketListenerAbstract listener = new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketReceive(final PacketReceiveEvent event) {
                handlePacketReceive(event);
            }

            @Override
            public void onPacketSend(final PacketSendEvent event) {
                handlePacketSend(event);
            }

            @Override
            public void onUserDisconnect(final UserDisconnectEvent event) {
                final User user = event.getUser();
                if (user != null && user.getUUID() != null) {
                    final ImmobilizeSession removed = sessionsByPlayerId.remove(user.getUUID());
                    if (removed != null) {
                        sessionsByEntityId.remove(removed.entityId, removed);
                    }
                }
            }
        };
        api.getEventManager().registerListener(listener);
        this.registeredListener = listener;
    }

    @Override
    public void immobilize(final Player player) {
        final UUID playerId = player.getUniqueId();
        if (playerId == null || !player.isOnline()) {
            return;
        }
        final Location loc = player.getLocation();
        if (loc == null) {
            return;
        }
        final int entityId = player.getEntityId();

        final ImmobilizeSession existing = sessionsByPlayerId.get(playerId);
        final ImmobilizeSession session;
        if (existing != null) {
            synchronized (existing) {
                existing.anchorX = loc.getX();
                existing.anchorY = loc.getY();
                existing.anchorZ = loc.getZ();
                existing.externalDisplacementUntilMillis = 0L;
            }
            session = existing;
        } else {
            final Map<Attribute, PropertySnapshot> attrSnapshot = snapshotAttributes(player, trackedAttributes());
            final AbilitiesSnapshot abilitiesSnapshot = snapshotAbilities(player);
            session = new ImmobilizeSession(
                    playerId,
                    entityId,
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    attrSnapshot,
                    abilitiesSnapshot
            );
            sessionsByPlayerId.put(playerId, session);
            sessionsByEntityId.put(entityId, session);
        }

        final AbilitiesSnapshot abilities;
        synchronized (session) {
            abilities = session.realAbilities;
        }

        packetSender.accept(player, buildFrozenAttributesPacket(entityId, trackedAttributes()));
        packetSender.accept(player, new WrapperPlayServerPlayerAbilities(
                abilities.godMode(),
                abilities.flying(),
                abilities.flightAllowed(),
                abilities.creativeMode(),
                0.0f,
                FROZEN_FOV_MODIFIER
        ));
        final Vector velocity = player.getVelocity();
        final double clampedVelY = velocity != null ? Math.min(0.0, velocity.getY()) : 0.0;
        packetSender.accept(player, new WrapperPlayServerEntityVelocity(entityId, new Vector3d(0.0, clampedVelY, 0.0)));
    }

    @Override
    public void release(final Player player) {
        final UUID playerId = player.getUniqueId();
        if (playerId == null) {
            return;
        }
        final ImmobilizeSession session = sessionsByPlayerId.remove(playerId);
        if (session == null) {
            return;
        }
        sessionsByEntityId.remove(session.entityId, session);
        if (!player.isOnline()) {
            return;
        }

        final List<Property> restoreProperties;
        final AbilitiesSnapshot restoreAbilities;
        synchronized (session) {
            restoreProperties = new ArrayList<>(session.realAttributes.size());
            for (final PropertySnapshot snapshot : session.realAttributes.values()) {
                restoreProperties.add(snapshot.toPacketProperty());
            }
            restoreAbilities = session.realAbilities;
        }

        packetSender.accept(player, new WrapperPlayServerUpdateAttributes(session.entityId, restoreProperties));
        packetSender.accept(player, new WrapperPlayServerPlayerAbilities(
                restoreAbilities.godMode(),
                restoreAbilities.flying(),
                restoreAbilities.flightAllowed(),
                restoreAbilities.creativeMode(),
                restoreAbilities.flySpeed(),
                restoreAbilities.fovModifier()
        ));
    }

    @Override
    public void releaseAll() {
        for (final UUID playerId : List.copyOf(sessionsByPlayerId.keySet())) {
            final ImmobilizeSession session = sessionsByPlayerId.get(playerId);
            if (session == null) {
                continue;
            }
            final Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                release(player);
            } else {
                sessionsByPlayerId.remove(playerId, session);
                sessionsByEntityId.remove(session.entityId, session);
            }
        }
    }

    @Override
    public void destroy() {
        releaseAll();
        sessionsByPlayerId.clear();
        sessionsByEntityId.clear();
        final PacketListenerCommon listener = this.registeredListener;
        this.registeredListener = null;
        if (listener != null) {
            final PacketEventsAPI<?> api = PacketEvents.getAPI();
            if (api != null) {
                api.getEventManager().unregisterListener(listener);
            }
        }
    }

    boolean isImmobilized(final UUID playerId) {
        return sessionsByPlayerId.containsKey(playerId);
    }

    void handlePacketReceive(final PacketReceiveEvent event) {
        final User user = event.getUser();
        if (user == null || user.getUUID() == null) {
            return;
        }
        final ImmobilizeSession session = sessionsByPlayerId.get(user.getUUID());
        if (session == null) {
            return;
        }

        final var packetType = event.getPacketType();
        if (packetType == PacketType.Play.Client.PLAYER_POSITION
                || packetType == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            final WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
            if (clampClientPosition(user.getUUID(), wrapper.getLocation())) {
                event.markForReEncode(true);
            }
        } else if (packetType == PacketType.Play.Client.ENTITY_ACTION) {
            final WrapperPlayClientEntityAction actionWrapper = new WrapperPlayClientEntityAction(event);
            if (shouldCancelEntityAction(user.getUUID(), actionWrapper.getAction())) {
                event.setCancelled(true);
            }
        } else if (packetType == PacketType.Play.Client.PLAYER_ABILITIES) {
            event.setCancelled(true);
        }
    }

    boolean clampClientPosition(
            final UUID playerId,
            final com.github.retrooper.packetevents.protocol.world.Location location
    ) {
        final ImmobilizeSession session = sessionsByPlayerId.get(playerId);
        if (session == null) {
            return false;
        }
        final Vector3d pos = location.getPosition();
        final long now = System.currentTimeMillis();
        synchronized (session) {
            if (now < session.externalDisplacementUntilMillis) {
                session.anchorX = pos.getX();
                session.anchorY = pos.getY();
                session.anchorZ = pos.getZ();
                return false;
            }
            final double clampedX = session.anchorX;
            final double clampedZ = session.anchorZ;
            final double clampedY;
            if (pos.getY() > session.anchorY) {
                clampedY = session.anchorY;
            } else {
                clampedY = pos.getY();
                session.anchorY = clampedY;
            }
            final boolean modified = pos.getX() != clampedX || pos.getY() != clampedY || pos.getZ() != clampedZ;
            if (modified) {
                location.setPosition(new Vector3d(clampedX, clampedY, clampedZ));
            }
            return modified;
        }
    }

    boolean shouldCancelEntityAction(
            final UUID playerId,
            final WrapperPlayClientEntityAction.Action action
    ) {
        return sessionsByPlayerId.containsKey(playerId)
                && action == WrapperPlayClientEntityAction.Action.START_SPRINTING;
    }

    void handlePacketSend(final PacketSendEvent event) {
        final User user = event.getUser();
        if (user == null || user.getUUID() == null) {
            return;
        }
        final UUID userId = user.getUUID();
        if (!(event.getPacketType() instanceof PacketType.Play.Server serverType)) {
            return;
        }
        switch (serverType) {
            case UPDATE_ATTRIBUTES -> {
                final WrapperPlayServerUpdateAttributes wrapper = new WrapperPlayServerUpdateAttributes(event);
                if (interceptOutgoingAttributes(userId, wrapper.getEntityId(), wrapper.getProperties())) {
                    event.markForReEncode(true);
                }
            }
            case PLAYER_ABILITIES -> {
                final WrapperPlayServerPlayerAbilities wrapper = new WrapperPlayServerPlayerAbilities(event);
                if (interceptOutgoingAbilities(userId, wrapper)) {
                    event.markForReEncode(true);
                }
            }
            case PLAYER_POSITION_AND_LOOK -> markExternalDisplacement(userId);
            case ENTITY_VELOCITY -> {
                final WrapperPlayServerEntityVelocity wrapper = new WrapperPlayServerEntityVelocity(event);
                final Vector3d vel = wrapper.getVelocity();
                if (isExternalDisplacementVelocity(vel)) {
                    markExternalDisplacementForEntity(userId, wrapper.getEntityId());
                }
            }
            default -> {
            }
        }
    }

    boolean interceptOutgoingAttributes(
            final UUID userId,
            final int entityId,
            final List<Property> properties
    ) {
        final ImmobilizeSession session = sessionsByEntityId.get(entityId);
        if (session == null || !session.playerId.equals(userId)) {
            return false;
        }
        boolean modified = false;
        synchronized (session) {
            for (int i = 0; i < properties.size(); i++) {
                final Property prop = properties.get(i);
                final Double frozenVal = frozenAttributeValues().get(prop.getAttribute());
                if (frozenVal != null) {
                    session.realAttributes.put(
                            prop.getAttribute(),
                            new PropertySnapshot(prop.getAttribute(), prop.getValue(), List.copyOf(prop.getModifiers()))
                    );
                    properties.set(i, new Property(prop.getAttribute(), frozenVal, List.of()));
                    modified = true;
                }
            }
        }
        return modified;
    }

    boolean interceptOutgoingAbilities(
            final UUID userId,
            final WrapperPlayServerPlayerAbilities wrapper
    ) {
        final ImmobilizeSession session = sessionsByPlayerId.get(userId);
        if (session == null) {
            return false;
        }
        synchronized (session) {
            session.realAbilities = new AbilitiesSnapshot(
                    wrapper.isInGodMode(),
                    wrapper.isFlying(),
                    wrapper.isFlightAllowed(),
                    wrapper.isInCreativeMode(),
                    wrapper.getFlySpeed(),
                    wrapper.getFOVModifier()
            );
        }
        wrapper.setFlySpeed(0.0f);
        wrapper.setFOVModifier(FROZEN_FOV_MODIFIER);
        return true;
    }

    static boolean isExternalDisplacementVelocity(final Vector3d vel) {
        return vel.getX() != 0.0 || vel.getY() > 0.0 || vel.getZ() != 0.0;
    }

    void markExternalDisplacement(final UUID userId) {
        final ImmobilizeSession session = sessionsByPlayerId.get(userId);
        if (session != null) {
            synchronized (session) {
                session.externalDisplacementUntilMillis = System.currentTimeMillis() + EXTERNAL_DISPLACEMENT_GRACE_MS;
            }
        }
    }

    void markExternalDisplacementForEntity(final UUID userId, final int entityId) {
        final ImmobilizeSession session = sessionsByEntityId.get(entityId);
        if (session != null && session.playerId.equals(userId)) {
            synchronized (session) {
                session.externalDisplacementUntilMillis = System.currentTimeMillis() + EXTERNAL_DISPLACEMENT_GRACE_MS;
            }
        }
    }

    private static Map<Attribute, PropertySnapshot> snapshotAttributes(
            final Player player,
            final List<AttributeMapping> trackedAttributes
    ) {
        final Map<Attribute, PropertySnapshot> map = new LinkedHashMap<>();
        for (final AttributeMapping mapping : trackedAttributes) {
            final @Nullable AttributeInstance instance = mapping.instanceLookup().apply(player);
            if (instance == null) {
                map.put(mapping.packetAttribute(), new PropertySnapshot(
                        mapping.packetAttribute(),
                        mapping.defaultValue(),
                        List.of()
                ));
                continue;
            }
            final List<PropertyModifier> modifiers = new ArrayList<>();
            for (final AttributeModifier mod : instance.getModifiers()) {
                final NamespacedKey key = mod.getKey();
                final ResourceLocation resource = new ResourceLocation(key.namespace(), key.value());
                modifiers.add(new PropertyModifier(resource, mod.getAmount(), mapOperation(mod.getOperation())));
            }
            map.put(mapping.packetAttribute(), new PropertySnapshot(
                    mapping.packetAttribute(),
                    instance.getBaseValue(),
                    List.copyOf(modifiers)
            ));
        }
        return map;
    }

    private static AbilitiesSnapshot snapshotAbilities(final Player player) {
        final GameMode mode = player.getGameMode();
        final boolean godMode = player.isMarkedInvulnerable()
                || mode == GameMode.CREATIVE
                || mode == GameMode.SPECTATOR;
        return new AbilitiesSnapshot(
                godMode,
                player.isFlying(),
                player.getAllowFlight(),
                mode == GameMode.CREATIVE,
                player.getFlySpeed() / 2.0f,
                player.getWalkSpeed() / 2.0f
        );
    }

    private static WrapperPlayServerUpdateAttributes buildFrozenAttributesPacket(
            final int entityId,
            final List<AttributeMapping> trackedAttributes
    ) {
        final List<Property> properties = new ArrayList<>(trackedAttributes.size());
        for (final AttributeMapping mapping : trackedAttributes) {
            properties.add(new Property(mapping.packetAttribute(), mapping.frozenValue(), List.of()));
        }
        return new WrapperPlayServerUpdateAttributes(entityId, properties);
    }

    private static PropertyModifier.Operation mapOperation(final AttributeModifier.Operation operation) {
        return switch (operation) {
            case ADD_NUMBER -> PropertyModifier.Operation.ADDITION;
            case ADD_SCALAR -> PropertyModifier.Operation.MULTIPLY_BASE;
            case MULTIPLY_SCALAR_1 -> PropertyModifier.Operation.MULTIPLY_TOTAL;
        };
    }

    private static void sendSilentPacket(final Player player, final PacketWrapper<?> wrapper) {
        final PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null || !api.isInitialized()) {
            return;
        }
        if (api.getPlayerManager().getChannel(player) == null) {
            return;
        }
        api.getPlayerManager().sendPacketSilently(player, wrapper);
    }
}
