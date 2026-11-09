package net.sabafly.clickportals.visual;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * PacketEvents-backed implementation of {@link HologramService} that spawns client-only
 * {@code TEXT_DISPLAY} entities above placed Aetherytes (#6).
 */
public final class PacketEventsHologramService implements HologramService {

    private static final int METADATA_BILLBOARD_INDEX = 15;
    private static final byte BILLBOARD_CENTER = 3;
    private static final int METADATA_TEXT_INDEX = 23;
    private static final int METADATA_STYLE_FLAGS_INDEX = 27;
    private static final byte STYLE_FLAG_SHADOW = 0x01;

    private final AtomicInteger entityIdGenerator = new AtomicInteger(1_500_000_000);
    private final Map<UUID, Integer> entityIdsByAetheryte = new ConcurrentHashMap<>();

    private int getOrCreateEntityId(final UUID aetheryteId) {
        return entityIdsByAetheryte.computeIfAbsent(aetheryteId, ignored -> entityIdGenerator.getAndIncrement());
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
        final Integer entityId = entityIdsByAetheryte.remove(aetheryteId);
        if (entityId == null) {
            return;
        }
        final PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null || !api.isInitialized()) {
            return;
        }
        final WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
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
        if (!entityIdsByAetheryte.isEmpty()) {
            final int[] allIds = entityIdsByAetheryte.values().stream().mapToInt(Integer::intValue).toArray();
            api.getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(allIds));
        }
        for (final Aetheryte aetheryte : worldAetherytes) {
            sendSpawnPackets(api, player, aetheryte);
        }
    }

    @Override
    public void destroyAll() {
        if (entityIdsByAetheryte.isEmpty()) {
            return;
        }
        final int[] allIds = entityIdsByAetheryte.values().stream().mapToInt(Integer::intValue).toArray();
        entityIdsByAetheryte.clear();
        final PacketEventsAPI<?> api = PacketEvents.getAPI();
        if (api == null || !api.isInitialized()) {
            return;
        }
        final WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(allIds);
        for (final Player player : Bukkit.getOnlinePlayers()) {
            api.getPlayerManager().sendPacket(player, destroyPacket);
        }
    }

    private void sendSpawnPackets(final PacketEventsAPI<?> api, final Player player, final Aetheryte aetheryte) {
        final int entityId = getOrCreateEntityId(aetheryte.id());
        final Vector3d position = new Vector3d(
                aetheryte.blockX() + 0.5,
                aetheryte.blockY() + 1.35,
                aetheryte.blockZ() + 0.5
        );
        final WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(aetheryte.id()),
                EntityTypes.TEXT_DISPLAY,
                position,
                0.0f,
                0.0f,
                0.0f,
                0,
                Optional.empty()
        );
        final Component title = Messages.hologramTitle(aetheryte.name());
        final List<EntityData<?>> metadata = List.of(
                new EntityData<>(METADATA_BILLBOARD_INDEX, EntityDataTypes.BYTE, BILLBOARD_CENTER),
                new EntityData<>(METADATA_TEXT_INDEX, EntityDataTypes.ADV_COMPONENT, title),
                new EntityData<>(METADATA_STYLE_FLAGS_INDEX, EntityDataTypes.BYTE, STYLE_FLAG_SHADOW)
        );
        final WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);

        api.getPlayerManager().sendPacket(player, spawnPacket);
        api.getPlayerManager().sendPacket(player, metadataPacket);
    }
}
