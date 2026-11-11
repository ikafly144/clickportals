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

import java.util.Objects;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * {@link PersistentDataType} for serializing an {@link Aetheryte} into a {@link PersistentDataContainer} (#6, #21, #37).
 */
public final class AetheryteDataType implements PersistentDataType<PersistentDataContainer, Aetheryte> {

    private static final String NAMESPACE = "clickportals";
    private static final NamespacedKey KEY_ID = new NamespacedKey(NAMESPACE, "id");
    private static final NamespacedKey KEY_NAME = new NamespacedKey(NAMESPACE, "name");
    private static final NamespacedKey KEY_WORLD_ID = new NamespacedKey(NAMESPACE, "world_id");
    private static final NamespacedKey KEY_WORLD_NAME = new NamespacedKey(NAMESPACE, "world_name");
    private static final NamespacedKey KEY_X = new NamespacedKey(NAMESPACE, "x");
    private static final NamespacedKey KEY_Y = new NamespacedKey(NAMESPACE, "y");
    private static final NamespacedKey KEY_Z = new NamespacedKey(NAMESPACE, "z");
    private static final NamespacedKey KEY_OWNER_ID = new NamespacedKey(NAMESPACE, "owner_id");
    private static final NamespacedKey KEY_CREATED_AT = new NamespacedKey(NAMESPACE, "created_at");
    private static final NamespacedKey KEY_VARIATION = new NamespacedKey(NAMESPACE, "variation");

    public static final AetheryteDataType INSTANCE = new AetheryteDataType();

    private AetheryteDataType() {
    }

    @Override
    public Class<PersistentDataContainer> getPrimitiveType() {
        return PersistentDataContainer.class;
    }

    @Override
    public Class<Aetheryte> getComplexType() {
        return Aetheryte.class;
    }

    @Override
    public PersistentDataContainer toPrimitive(final Aetheryte complex, final PersistentDataAdapterContext context) {
        final PersistentDataContainer container = context.newPersistentDataContainer();
        container.set(KEY_ID, PersistentDataType.STRING, complex.id().toString());
        container.set(KEY_NAME, PersistentDataType.STRING, complex.name());
        container.set(KEY_WORLD_ID, PersistentDataType.STRING, complex.worldId().toString());
        container.set(KEY_WORLD_NAME, PersistentDataType.STRING, complex.worldName());
        container.set(KEY_X, PersistentDataType.INTEGER, complex.blockX());
        container.set(KEY_Y, PersistentDataType.INTEGER, complex.blockY());
        container.set(KEY_Z, PersistentDataType.INTEGER, complex.blockZ());
        container.set(KEY_OWNER_ID, PersistentDataType.STRING, complex.ownerId().toString());
        container.set(KEY_CREATED_AT, PersistentDataType.LONG, complex.createdAt());
        container.set(KEY_VARIATION, PersistentDataType.STRING, complex.variation());
        return container;
    }

    @Override
    public Aetheryte fromPrimitive(final PersistentDataContainer primitive, final PersistentDataAdapterContext context) {
        final String idStr = Objects.requireNonNull(primitive.get(KEY_ID, PersistentDataType.STRING), "Missing aetheryte id");
        final String name = Objects.requireNonNull(primitive.get(KEY_NAME, PersistentDataType.STRING), "Missing aetheryte name");
        final String worldIdStr = Objects.requireNonNull(primitive.get(KEY_WORLD_ID, PersistentDataType.STRING), "Missing world_id");
        final String worldName = Objects.requireNonNull(primitive.get(KEY_WORLD_NAME, PersistentDataType.STRING), "Missing world_name");
        final Integer x = Objects.requireNonNull(primitive.get(KEY_X, PersistentDataType.INTEGER), "Missing x");
        final Integer y = Objects.requireNonNull(primitive.get(KEY_Y, PersistentDataType.INTEGER), "Missing y");
        final Integer z = Objects.requireNonNull(primitive.get(KEY_Z, PersistentDataType.INTEGER), "Missing z");
        final String ownerIdStr = Objects.requireNonNull(primitive.get(KEY_OWNER_ID, PersistentDataType.STRING), "Missing owner_id");
        final Long createdAt = Objects.requireNonNull(primitive.get(KEY_CREATED_AT, PersistentDataType.LONG), "Missing created_at");
        final String variation = primitive.get(KEY_VARIATION, PersistentDataType.STRING);

        return new Aetheryte(
                UUID.fromString(idStr),
                name,
                UUID.fromString(worldIdStr),
                worldName,
                x,
                y,
                z,
                UUID.fromString(ownerIdStr),
                createdAt,
                Aetheryte.normalizeVariation(variation)
        );
    }
}
