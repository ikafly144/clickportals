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
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
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
import net.sabafly.clickportals.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * PacketEvents-backed implementation of {@link HologramService} that spawns client-only
 * {@code BLOCK_DISPLAY}, {@code TEXT_DISPLAY}, and {@code INTERACTION} entities for placed Aetherytes (#6).
 */
public final class PacketEventsHologramService implements HologramService {

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
    private static final float INTERACTION_BOX_SIZE = 1.0f;

    /**
     * Bundle of client-side entity IDs allocated for a single Aetheryte.
     */
    record ClientEntitySet(int blockDisplayId, int textDisplayId, int interactionId) {
        int[] toArray() {
            return new int[]{blockDisplayId, textDisplayId, interactionId};
        }
    }

    private final AtomicInteger entityIdGenerator = new AtomicInteger(1_500_000_000);
    private final Map<UUID, ClientEntitySet> entitiesByAetheryte = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> aetheryteByEntityId = new ConcurrentHashMap<>();
    private final float viewRangeMultiplier;
    private final String displayBlockStateString;
    private volatile @Nullable BiConsumer<Player, UUID> interactionHandler;
    private @Nullable PacketListenerCommon registeredListener;

    /**
     * Creates a new {@link PacketEventsHologramService} with the default Lodestone display block.
     */
    public PacketEventsHologramService(final double viewDistanceBlocks) {
        this(viewDistanceBlocks, "minecraft:lodestone");
    }

    /**
     * Creates a new {@link PacketEventsHologramService} with the given view distance and display block state string.
     */
    public PacketEventsHologramService(final double viewDistanceBlocks, final String displayBlockStateString) {
        this.viewRangeMultiplier = (float) Math.max(0.05, viewDistanceBlocks / BASE_VIEW_RANGE_BLOCKS);
        this.displayBlockStateString = displayBlockStateString;
        registerPacketListenerIfReady();
    }

    private void registerPacketListenerIfReady() {
        final PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null || !api.isInitialized()) {
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
                if (action == WrapperPlayClientInteractEntity.InteractAction.INTERACT
                        && wrapper.getHand() == InteractionHand.MAIN_HAND) {
                    final Object rawPlayer = event.getPlayer();
                    if (rawPlayer instanceof Player player) {
                        handler.accept(player, targetAetheryteId);
                    }
                }
            }
        };
        api.getEventManager().registerListener(listener);
        this.registeredListener = listener;
    }

    private ClientEntitySet getOrCreateEntitySet(final UUID aetheryteId) {
        return entitiesByAetheryte.computeIfAbsent(aetheryteId, id -> {
            final int blockId = entityIdGenerator.getAndIncrement();
            final int textId = entityIdGenerator.getAndIncrement();
            final int interactionId = entityIdGenerator.getAndIncrement();
            aetheryteByEntityId.put(blockId, id);
            aetheryteByEntityId.put(textId, id);
            aetheryteByEntityId.put(interactionId, id);
            return new ClientEntitySet(blockId, textId, interactionId);
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
        aetheryteByEntityId.remove(set.blockDisplayId());
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

    private int[] collectAllEntityIds() {
        return entitiesByAetheryte.values().stream()
                .flatMapToInt(set -> java.util.Arrays.stream(set.toArray()))
                .toArray();
    }

    private int resolveBlockStateGlobalId() {
        try {
            final WrappedBlockState parsed = WrappedBlockState.getByString(displayBlockStateString);
            if (parsed != null) {
                return parsed.getGlobalId();
            }
        } catch (final Exception ignored) {
            // Fall through to default Lodestone state
        }
        return WrappedBlockState.getDefaultState(StateTypes.LODESTONE).getGlobalId();
    }

    private static UUID deriveChildUuid(final UUID baseId, final String suffix) {
        return UUID.nameUUIDFromBytes((baseId.toString() + ":" + suffix).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sends the spawn and metadata packets for `BLOCK_DISPLAY`, `TEXT_DISPLAY`, and `INTERACTION`
     * to the given player.
     */
    private void sendSpawnPackets(final PacketEventsAPI<?> api, final Player player, final Aetheryte aetheryte) {
        final ClientEntitySet set = getOrCreateEntitySet(aetheryte.id());

        // 1. BLOCK_DISPLAY (visual Aetheryte body aligned to block origin)
        final Vector3d blockOrigin = new Vector3d(aetheryte.blockX(), aetheryte.blockY(), aetheryte.blockZ());
        final WrapperPlayServerSpawnEntity blockSpawn = new WrapperPlayServerSpawnEntity(
                set.blockDisplayId(),
                Optional.of(deriveChildUuid(aetheryte.id(), "block")),
                EntityTypes.BLOCK_DISPLAY,
                blockOrigin,
                0.0f,
                0.0f,
                0.0f,
                0,
                Optional.empty()
        );
        final List<EntityData<?>> blockMeta = List.of(
                new EntityData<>(DISPLAY_VIEW_RANGE_INDEX, EntityDataTypes.FLOAT, viewRangeMultiplier),
                new EntityData<>(BLOCK_DISPLAY_STATE_INDEX, EntityDataTypes.BLOCK_STATE, resolveBlockStateGlobalId())
        );

        // 2. TEXT_DISPLAY (floating name hologram above the Aetheryte)
        final Vector3d textPosition = new Vector3d(
                aetheryte.blockX() + 0.5,
                aetheryte.blockY() + 1.35,
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
        final Component title = Messages.hologramTitle(aetheryte.name());
        final List<EntityData<?>> textMeta = List.of(
                new EntityData<>(DISPLAY_BILLBOARD_INDEX, EntityDataTypes.BYTE, BILLBOARD_CENTER),
                new EntityData<>(DISPLAY_VIEW_RANGE_INDEX, EntityDataTypes.FLOAT, viewRangeMultiplier),
                new EntityData<>(TEXT_DISPLAY_TEXT_INDEX, EntityDataTypes.ADV_COMPONENT, title),
                new EntityData<>(TEXT_DISPLAY_STYLE_FLAGS_INDEX, EntityDataTypes.BYTE, STYLE_FLAG_SHADOW)
        );

        // 3. INTERACTION (client-side 1x1x1 hitbox for right-click detection)
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
        final List<EntityData<?>> interactionMeta = List.of(
                new EntityData<>(INTERACTION_WIDTH_INDEX, EntityDataTypes.FLOAT, INTERACTION_BOX_SIZE),
                new EntityData<>(INTERACTION_HEIGHT_INDEX, EntityDataTypes.FLOAT, INTERACTION_BOX_SIZE),
                new EntityData<>(INTERACTION_RESPONSIVE_INDEX, EntityDataTypes.BOOLEAN, Boolean.TRUE)
        );

        api.getPlayerManager().sendPacket(player, blockSpawn);
        api.getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(set.blockDisplayId(), blockMeta));

        api.getPlayerManager().sendPacket(player, textSpawn);
        api.getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(set.textDisplayId(), textMeta));

        api.getPlayerManager().sendPacket(player, interactionSpawn);
        api.getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(set.interactionId(), interactionMeta));
    }
}
