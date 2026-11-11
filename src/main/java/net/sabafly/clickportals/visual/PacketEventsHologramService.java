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
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCooldown;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.config.PluginConfig;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.HitboxSection;
import net.sabafly.clickportals.variation.AetheryteVariation.ModelSection;
import net.sabafly.clickportals.variation.VariationRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * PacketEvents-backed implementation of {@link HologramService} that spawns client-only
 * {@code BLOCK_DISPLAY}, {@code TEXT_DISPLAY}, and {@code INTERACTION} entities
 * for placed Aetherytes (#6, #40).
 */
public final class PacketEventsHologramService implements HologramService {

    private static final int DISPLAY_TRANSLATION_INDEX = 11;
    private static final int DISPLAY_SCALE_INDEX = 12;
    private static final int DISPLAY_BILLBOARD_INDEX = 15;
    private static final byte BILLBOARD_CENTER = 3;
    private static final int DISPLAY_VIEW_RANGE_INDEX = 17;
    private static final double BASE_VIEW_RANGE_BLOCKS = 64.0;
    private static final int BLOCK_DISPLAY_STATE_INDEX = 23;
    private static final int TEXT_DISPLAY_TEXT_INDEX = 23;
    private static final int TEXT_DISPLAY_STYLE_FLAGS_INDEX = 27;
    private static final byte STYLE_FLAG_SHADOW = 0x01;

    private static final int INTERACTION_WIDTH_INDEX = 8;
    private static final int INTERACTION_HEIGHT_INDEX = 9;
    private static final int INTERACTION_RESPONSIVE_INDEX = 10;
    private static final long INTERACT_DEBOUNCE_MS = 150L;
    private static final ResourceLocation COOLDOWN_GROUP_RESOURCE =
            new ResourceLocation("clickportals", "aetheryte_place");

    /**
     * Bundle of client-side entity IDs allocated for a single Aetheryte.
     */
    record ClientEntitySet(int bodyDisplayId, int textDisplayId, int interactionId) {
        /**
         * Returns the three allocated entity IDs as an int array.
         */
        int[] toArray() {
            return new int[]{bodyDisplayId, textDisplayId, interactionId};
        }
    }

    private final AtomicInteger entityIdGenerator = new AtomicInteger(1_500_000_000);
    private final Map<UUID, ClientEntitySet> entitiesByAetheryte = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> aetheryteByEntityId = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteractMillisByPlayer = new ConcurrentHashMap<>();
    private final Map<String, Integer> blockStateIdCache = new ConcurrentHashMap<>();
    private final VariationRegistry variationRegistry;
    private final Messages messages;
    private volatile float viewRangeMultiplier;
    private volatile @Nullable BiConsumer<Player, UUID> interactionHandler;
    private @Nullable PacketListenerCommon registeredListener;

    /**
     * Creates a new {@link PacketEventsHologramService} with default variations and messages.
     */
    public PacketEventsHologramService(final double viewDistanceBlocks) {
        this(viewDistanceBlocks, VariationRegistry.defaults(), Messages.defaults());
    }

    /**
     * Creates a new {@link PacketEventsHologramService} with the given view distance, {@link VariationRegistry},
     * and {@link Messages} service.
     */
    public PacketEventsHologramService(
            final double viewDistanceBlocks,
            final VariationRegistry variationRegistry,
            final Messages messages
    ) {
        this.variationRegistry = variationRegistry;
        this.messages = messages;
        this.viewRangeMultiplier = (float) Math.max(0.05, viewDistanceBlocks / BASE_VIEW_RANGE_BLOCKS);
        registerPacketListenerIfReady();
    }

    @Override
    public void updateSettings(final PluginConfig config) {
        this.viewRangeMultiplier = (float) Math.max(0.05, config.hologramViewDistance() / BASE_VIEW_RANGE_BLOCKS);
        this.blockStateIdCache.clear();
    }

    /**
     * Registers a PacketEvents packet listener for {@code INTERACT_ENTITY} packets if PacketEvents API is available.
     */
    private void registerPacketListenerIfReady() {
        final PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null) {
            return;
        }
        final PacketListenerAbstract listener = new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketReceive(final PacketReceiveEvent event) {
                if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
                    return;
                }
                final WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
                final UUID targetAetheryteId = aetheryteByEntityId.get(wrapper.getEntityId());
                if (targetAetheryteId == null) {
                    return;
                }
                event.setCancelled(true);
                final BiConsumer<Player, UUID> handler = interactionHandler;
                if (handler == null) {
                    return;
                }
                final WrapperPlayClientInteractEntity.InteractAction action = wrapper.getAction();
                // In Minecraft 26.1+, ServerboundInteractPacket always decodes as INTERACT_AT in PacketEvents
                if ((action == WrapperPlayClientInteractEntity.InteractAction.INTERACT
                        || action == WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT)
                        && wrapper.getHand() == InteractionHand.MAIN_HAND) {
                    final Player player = resolveBukkitPlayer(event);
                    if (player != null && shouldTriggerInteraction(player.getUniqueId())) {
                        handler.accept(player, targetAetheryteId);
                    }
                }
            }

            @Override
            public void onUserDisconnect(final UserDisconnectEvent event) {
                if (event.getUser() != null && event.getUser().getUUID() != null) {
                    lastInteractMillisByPlayer.remove(event.getUser().getUUID());
                }
            }
        };
        api.getEventManager().registerListener(listener);
        this.registeredListener = listener;
    }

    /**
     * Resolves the Bukkit {@link Player} from a PacketEvents {@link PacketReceiveEvent}.
     */
    private static @Nullable Player resolveBukkitPlayer(final PacketReceiveEvent event) {
        final Object rawPlayer = event.getPlayer();
        if (rawPlayer instanceof Player player) {
            return player;
        }
        if (event.getUser() != null && event.getUser().getUUID() != null) {
            return Bukkit.getPlayer(event.getUser().getUUID());
        }
        return null;
    }

    /**
     * Debounces duplicate {@code INTERACT_AT} + {@code INTERACT} packet pairs from a single right-click.
     */
    private boolean shouldTriggerInteraction(final UUID playerId) {
        final long now = System.currentTimeMillis();
        final boolean[] allowed = {false};
        lastInteractMillisByPlayer.compute(playerId, (id, previous) -> {
            if (previous == null || (now - previous) >= INTERACT_DEBOUNCE_MS) {
                allowed[0] = true;
                return now;
            }
            return previous;
        });
        return allowed[0];
    }

    /**
     * Returns or allocates the bundle of client-side entity IDs for the given Aetheryte UUID.
     */
    private ClientEntitySet getOrCreateEntitySet(final UUID aetheryteId) {
        return entitiesByAetheryte.computeIfAbsent(aetheryteId, id -> {
            final int bodyId = entityIdGenerator.getAndIncrement();
            final int textId = entityIdGenerator.getAndIncrement();
            final int interactionId = entityIdGenerator.getAndIncrement();
            aetheryteByEntityId.put(bodyId, id);
            aetheryteByEntityId.put(textId, id);
            aetheryteByEntityId.put(interactionId, id);
            return new ClientEntitySet(bodyId, textId, interactionId);
        });
    }

    @Override
    public Optional<UUID> resolveInteractionTarget(final int entityId) {
        return Optional.ofNullable(aetheryteByEntityId.get(entityId));
    }

    @Override
    public void setInteractionHandler(final BiConsumer<Player, UUID> handler) {
        this.interactionHandler = handler;
    }

    @Override
    public void removePlayer(final UUID playerId) {
        lastInteractMillisByPlayer.remove(playerId);
    }

    @Override
    public void showAetheryte(final Aetheryte aetheryte) {
        final PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null || !api.isInitialized()) {
            return;
        }
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getUID().equals(aetheryte.worldId())) {
                sendSpawnPackets(api, player, aetheryte);
            }
        }
    }

    @Override
    public void hideAetheryte(final UUID aetheryteId) {
        final ClientEntitySet set = entitiesByAetheryte.remove(aetheryteId);
        if (set == null) {
            return;
        }
        aetheryteByEntityId.remove(set.bodyDisplayId());
        aetheryteByEntityId.remove(set.textDisplayId());
        aetheryteByEntityId.remove(set.interactionId());

        final PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null || !api.isInitialized()) {
            return;
        }
        final WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(set.toArray());
        for (final Player player : Bukkit.getOnlinePlayers()) {
            api.getPlayerManager().sendPacket(player, destroyPacket);
        }
    }

    @Override
    public void refreshPlayer(final Player player, final Collection<Aetheryte> worldAetherytes) {
        final PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null || !api.isInitialized()) {
            return;
        }
        if (!entitiesByAetheryte.isEmpty()) {
            final int[] allIds = collectAllEntityIds();
            api.getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(allIds));
        }
        for (final Aetheryte aetheryte : worldAetherytes) {
            sendSpawnPackets(api, player, aetheryte);
        }
    }

    @Override
    public void destroyAll() {
        final PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (registeredListener != null && api != null) {
            api.getEventManager().unregisterListener(registeredListener);
            registeredListener = null;
        }
        lastInteractMillisByPlayer.clear();
        blockStateIdCache.clear();
        if (entitiesByAetheryte.isEmpty()) {
            return;
        }
        final int[] allIds = collectAllEntityIds();
        entitiesByAetheryte.clear();
        aetheryteByEntityId.clear();
        if (api == null || !api.isInitialized()) {
            return;
        }
        final WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(allIds);
        for (final Player player : Bukkit.getOnlinePlayers()) {
            api.getPlayerManager().sendPacket(player, destroyPacket);
        }
    }

    /**
     * Flattens all currently allocated client-side entity IDs into a single primitive array.
     */
    private int[] collectAllEntityIds() {
        return entitiesByAetheryte.values().stream()
                .flatMapToInt(set -> java.util.Arrays.stream(set.toArray()))
                .toArray();
    }

    /**
     * Parses a block state string once into its PacketEvents global block state ID, warning and falling back
     * to Lodestone if invalid.
     */
    private int resolveBlockStateGlobalId(final String stateString) {
        return blockStateIdCache.computeIfAbsent(stateString, raw -> {
            try {
                final WrappedBlockState parsed = WrappedBlockState.getByString(raw);
                if (parsed != null) {
                    return parsed.getGlobalId();
                }
            } catch (final Exception ignored) {
                // Fall through to default Lodestone state
            }
            Bukkit.getLogger().warning("Invalid model.block '" + raw + "'; falling back to minecraft:lodestone");
            return WrappedBlockState.getDefaultState(StateTypes.LODESTONE).getGlobalId();
        });
    }

    /**
     * Sends a client-side {@code SET_COOLDOWN} packet via PacketEvents for the Aetheryte creation item cooldown
     * group (`clickportals:aetheryte_place`) (#43).
     */
    public static void sendPlacementCooldown(final Player player, final int cooldownTicks) {
        if (cooldownTicks < 0) {
            return;
        }
        try {
            final PacketEventsAPI<?> api = PacketEvents.getAPI();
            if (api == null || !api.isInitialized()) {
                return;
            }
            final WrapperPlayServerSetCooldown packet = new WrapperPlayServerSetCooldown(
                    COOLDOWN_GROUP_RESOURCE,
                    cooldownTicks
            );
            api.getPlayerManager().sendPacket(player, packet);
        } catch (final Throwable ignored) {
            // PacketEvents is not initialized in unit test environments
        }
    }

    /**
     * Derives a deterministic child UUID from a parent Aetheryte UUID and a component role suffix.
     */
    private static UUID deriveChildUuid(final UUID baseId, final String suffix) {
        return UUID.nameUUIDFromBytes((baseId.toString() + ":" + suffix).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Converts a 3-element double list into a PacketEvents {@link Vector3f}.
     */
    private static Vector3f toVector3f(final List<Double> values, final float fallback) {
        if (values.size() != 3) {
            return new Vector3f(fallback, fallback, fallback);
        }
        return new Vector3f(
                values.get(0).floatValue(),
                values.get(1).floatValue(),
                values.get(2).floatValue()
        );
    }

    /**
     * Sends the spawn and metadata packets for `BLOCK_DISPLAY`, `TEXT_DISPLAY`, and `INTERACTION`
     * to the given player (#40).
     */
    private void sendSpawnPackets(final PacketEventsAPI<?> api, final Player player, final Aetheryte aetheryte) {
        final ClientEntitySet set = getOrCreateEntitySet(aetheryte.id());
        final AetheryteVariation variation = variationRegistry.getOrDefault(aetheryte.variation());
        final ModelSection model = variation.model();
        final HitboxSection hitbox = variation.hitbox();

        final Vector3f translation = toVector3f(model.translation(), 0.0f);
        final Vector3f scale = toVector3f(model.scale(), 1.0f);

        // 1. Visual Aetheryte body: BLOCK_DISPLAY
        final Vector3d bodyPosition = new Vector3d(aetheryte.blockX(), aetheryte.blockY(), aetheryte.blockZ());
        final int blockStateId = resolveBlockStateGlobalId(model.block());
        final List<EntityData<?>> bodyMeta = List.of(
                new EntityData<>(DISPLAY_TRANSLATION_INDEX, EntityDataTypes.VECTOR3F, translation),
                new EntityData<>(DISPLAY_SCALE_INDEX, EntityDataTypes.VECTOR3F, scale),
                new EntityData<>(DISPLAY_VIEW_RANGE_INDEX, EntityDataTypes.FLOAT, viewRangeMultiplier),
                new EntityData<>(BLOCK_DISPLAY_STATE_INDEX, EntityDataTypes.BLOCK_STATE, blockStateId)
        );

        final WrapperPlayServerSpawnEntity bodySpawn = new WrapperPlayServerSpawnEntity(
                set.bodyDisplayId(),
                Optional.of(deriveChildUuid(aetheryte.id(), "body")),
                EntityTypes.BLOCK_DISPLAY,
                bodyPosition,
                0.0f,
                0.0f,
                0.0f,
                0,
                Optional.empty()
        );

        // 2. TEXT_DISPLAY (floating name hologram above the Aetheryte)
        final Vector3d textPosition = new Vector3d(
                aetheryte.blockX() + 0.5,
                aetheryte.blockY() + model.nameHeight(),
                aetheryte.blockZ() + 0.5
        );
        final WrapperPlayServerSpawnEntity textSpawn = new WrapperPlayServerSpawnEntity(
                set.textDisplayId(),
                Optional.of(deriveChildUuid(aetheryte.id(), "text")),
                EntityTypes.TEXT_DISPLAY,
                textPosition,
                0.0f,
                0.0f,
                0.0f,
                0,
                Optional.empty()
        );
        final Component title = messages.hologramTitle(player, aetheryte.name());
        final List<EntityData<?>> textMeta = List.of(
                new EntityData<>(DISPLAY_BILLBOARD_INDEX, EntityDataTypes.BYTE, BILLBOARD_CENTER),
                new EntityData<>(DISPLAY_VIEW_RANGE_INDEX, EntityDataTypes.FLOAT, viewRangeMultiplier),
                new EntityData<>(TEXT_DISPLAY_TEXT_INDEX, EntityDataTypes.ADV_COMPONENT, title),
                new EntityData<>(TEXT_DISPLAY_STYLE_FLAGS_INDEX, EntityDataTypes.BYTE, STYLE_FLAG_SHADOW)
        );

        // 3. INTERACTION (client-side hitbox with variation-configured width and height) (#40)
        final Vector3d interactionPosition = new Vector3d(
                aetheryte.blockX() + 0.5,
                aetheryte.blockY(),
                aetheryte.blockZ() + 0.5
        );
        final WrapperPlayServerSpawnEntity interactionSpawn = new WrapperPlayServerSpawnEntity(
                set.interactionId(),
                Optional.of(deriveChildUuid(aetheryte.id(), "interaction")),
                EntityTypes.INTERACTION,
                interactionPosition,
                0.0f,
                0.0f,
                0.0f,
                0,
                Optional.empty()
        );
        final float validWidth = hitbox.width() > 0.0f ? hitbox.width() : 1.0f;
        final float validHeight = hitbox.height() > 0.0f ? hitbox.height() : 1.0f;
        final List<EntityData<?>> interactionMeta = List.of(
                new EntityData<>(INTERACTION_WIDTH_INDEX, EntityDataTypes.FLOAT, validWidth),
                new EntityData<>(INTERACTION_HEIGHT_INDEX, EntityDataTypes.FLOAT, validHeight),
                new EntityData<>(INTERACTION_RESPONSIVE_INDEX, EntityDataTypes.BOOLEAN, Boolean.TRUE)
        );

        api.getPlayerManager().sendPacket(player, bodySpawn);
        api.getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(set.bodyDisplayId(), bodyMeta));

        api.getPlayerManager().sendPacket(player, textSpawn);
        api.getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(set.textDisplayId(), textMeta));

        api.getPlayerManager().sendPacket(player, interactionSpawn);
        api.getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(set.interactionId(), interactionMeta));
    }
}
