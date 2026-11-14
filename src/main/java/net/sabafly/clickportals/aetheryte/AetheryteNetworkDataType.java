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
package net.sabafly.clickportals.aetheryte;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Paper {@link PersistentDataType} serializing {@link AetheryteNetwork} instances into world PDC (#27).
 */
public final class AetheryteNetworkDataType implements PersistentDataType<PersistentDataContainer, AetheryteNetwork> {

    private static final String NAMESPACE = "clickportals";
    private static final NamespacedKey KEY_ID = new NamespacedKey(NAMESPACE, "id");
    private static final NamespacedKey KEY_NAME = new NamespacedKey(NAMESPACE, "name");
    private static final NamespacedKey KEY_WORLD_ID = new NamespacedKey(NAMESPACE, "world_id");
    private static final NamespacedKey KEY_WORLD_NAME = new NamespacedKey(NAMESPACE, "world_name");
    private static final NamespacedKey KEY_OWNER_ID = new NamespacedKey(NAMESPACE, "owner_id");
    private static final NamespacedKey KEY_OWNER_NAME = new NamespacedKey(NAMESPACE, "owner_name");
    private static final NamespacedKey KEY_CREATED_AT = new NamespacedKey(NAMESPACE, "created_at");
    private static final NamespacedKey KEY_VISIBILITY = new NamespacedKey(NAMESPACE, "visibility");
    private static final NamespacedKey KEY_INVITEES = new NamespacedKey(NAMESPACE, "invitees");

    public static final AetheryteNetworkDataType INSTANCE = new AetheryteNetworkDataType();

    private AetheryteNetworkDataType() {
    }

    @Override
    public Class<PersistentDataContainer> getPrimitiveType() {
        return PersistentDataContainer.class;
    }

    @Override
    public Class<AetheryteNetwork> getComplexType() {
        return AetheryteNetwork.class;
    }

    @Override
    public PersistentDataContainer toPrimitive(
            final AetheryteNetwork complex,
            final PersistentDataAdapterContext context
    ) {
        final PersistentDataContainer container = context.newPersistentDataContainer();
        container.set(KEY_ID, PersistentDataType.STRING, complex.id().toString());
        container.set(KEY_NAME, PersistentDataType.STRING, complex.name());
        container.set(KEY_WORLD_ID, PersistentDataType.STRING, complex.worldId().toString());
        container.set(KEY_WORLD_NAME, PersistentDataType.STRING, complex.worldName());
        container.set(KEY_OWNER_ID, PersistentDataType.STRING, complex.ownerId().toString());
        if (complex.ownerName() != null) {
            container.set(KEY_OWNER_NAME, PersistentDataType.STRING, complex.ownerName());
        }
        container.set(KEY_CREATED_AT, PersistentDataType.LONG, complex.createdAt());
        container.set(KEY_VISIBILITY, PersistentDataType.STRING, complex.visibility().id());
        if (!complex.invitedPlayers().isEmpty()) {
            final List<String> inviteeStrings = new ArrayList<>(complex.invitedPlayers().size());
            for (final UUID inviteeId : complex.invitedPlayers()) {
                inviteeStrings.add(inviteeId.toString());
            }
            container.set(KEY_INVITEES, PersistentDataType.LIST.strings(), inviteeStrings);
        }
        return container;
    }

    @Override
    public AetheryteNetwork fromPrimitive(
            final PersistentDataContainer primitive,
            final PersistentDataAdapterContext context
    ) {
        final String idStr = Objects.requireNonNull(primitive.get(KEY_ID, PersistentDataType.STRING), "Missing network id");
        final String name = Objects.requireNonNull(primitive.get(KEY_NAME, PersistentDataType.STRING), "Missing network name");
        final String worldIdStr = Objects.requireNonNull(primitive.get(KEY_WORLD_ID, PersistentDataType.STRING), "Missing network world_id");
        final String worldName = Objects.requireNonNull(primitive.get(KEY_WORLD_NAME, PersistentDataType.STRING), "Missing network world_name");
        final String ownerIdStr = Objects.requireNonNull(primitive.get(KEY_OWNER_ID, PersistentDataType.STRING), "Missing network owner_id");
        final String ownerName = primitive.get(KEY_OWNER_NAME, PersistentDataType.STRING);
        final Long createdAt = Objects.requireNonNull(primitive.get(KEY_CREATED_AT, PersistentDataType.LONG), "Missing network created_at");
        final AetheryteVisibility visibility = AetheryteVisibility.fromString(
                primitive.get(KEY_VISIBILITY, PersistentDataType.STRING)
        );
        final List<String> rawInvitees = primitive.get(KEY_INVITEES, PersistentDataType.LIST.strings());
        final Set<UUID> invitees = new LinkedHashSet<>();
        if (rawInvitees != null) {
            for (final String rawId : rawInvitees) {
                try {
                    invitees.add(UUID.fromString(rawId));
                } catch (final IllegalArgumentException ignored) {
                    // Ignore malformed invitee UUID entries
                }
            }
        }
        return new AetheryteNetwork(
                UUID.fromString(idStr),
                name,
                UUID.fromString(worldIdStr),
                worldName,
                UUID.fromString(ownerIdStr),
                ownerName,
                createdAt,
                visibility,
                invitees
        );
    }
}
