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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class AetheryteDataTypeTest {

    @Test
    void roundTripSerializationPreservesAllFieldsIncludingVariation() {
        final Map<NamespacedKey, Object> backingStore = new HashMap<>();
        final PersistentDataContainer mockContainer = createInMemoryContainer(backingStore);
        final PersistentDataAdapterContext context = mock(PersistentDataAdapterContext.class);
        when(context.newPersistentDataContainer()).thenReturn(mockContainer);

        final Aetheryte original = new Aetheryte(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "拠点エーテライト",
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                "world",
                128,
                64,
                -256,
                UUID.fromString("99999999-8888-7777-6666-555555555555"),
                1_700_000_000_000L,
                "grand_crystal",
                "ikafly144"
        );

        final PersistentDataContainer serialized = AetheryteDataType.INSTANCE.toPrimitive(original, context);
        final Aetheryte restored = AetheryteDataType.INSTANCE.fromPrimitive(serialized, context);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.variation()).isEqualTo("grand_crystal");
        assertThat(restored.ownerName()).isEqualTo("ikafly144");
    }

    @Test
    void legacyV02PdcWithoutVariationKeyFallsBackToDefault() {
        final Map<NamespacedKey, Object> backingStore = new HashMap<>();
        final PersistentDataContainer mockContainer = createInMemoryContainer(backingStore);
        final PersistentDataAdapterContext context = mock(PersistentDataAdapterContext.class);
        when(context.newPersistentDataContainer()).thenReturn(mockContainer);

        final Aetheryte original = new Aetheryte(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "旧データ",
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                "world",
                10,
                64,
                20,
                UUID.fromString("99999999-8888-7777-6666-555555555555"),
                1_700_000_000_000L
        );

        final PersistentDataContainer serialized = AetheryteDataType.INSTANCE.toPrimitive(original, context);
        backingStore.remove(new NamespacedKey("clickportals", "variation"));

        final Aetheryte restored = AetheryteDataType.INSTANCE.fromPrimitive(serialized, context);
        assertThat(restored.variation()).isEqualTo(Aetheryte.DEFAULT_VARIATION);
        assertThat(restored.ownerName()).isNull();
    }

    @SuppressWarnings("unchecked")
    static PersistentDataContainer createInMemoryContainer(final Map<NamespacedKey, Object> store) {
        final PersistentDataContainer container = mock(PersistentDataContainer.class);
        doAnswer(invocation -> {
            final NamespacedKey key = invocation.getArgument(0);
            final Object value = invocation.getArgument(2);
            store.put(key, value);
            return null;
        }).when(container).set(any(NamespacedKey.class), any(PersistentDataType.class), any());

        when(container.get(any(NamespacedKey.class), any(PersistentDataType.class))).thenAnswer(invocation -> {
            final NamespacedKey key = invocation.getArgument(0);
            return store.get(key);
        });

        doAnswer(invocation -> {
            final NamespacedKey key = invocation.getArgument(0);
            store.remove(key);
            return null;
        }).when(container).remove(any(NamespacedKey.class));

        when(container.has(any(NamespacedKey.class), any(PersistentDataType.class))).thenAnswer(invocation -> {
            final NamespacedKey key = invocation.getArgument(0);
            return store.containsKey(key);
        });
        return container;
    }
}
