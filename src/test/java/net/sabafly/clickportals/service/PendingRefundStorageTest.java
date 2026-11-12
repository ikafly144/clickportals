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
package net.sabafly.clickportals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.sabafly.clickportals.cost.CostItemQuote;
import net.sabafly.clickportals.cost.CostType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

class PendingRefundStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripSavesAndLoadsPendingRefunds() {
        final Path storageFile = tempDir.resolve("pending_refunds.yml");
        final Logger logger = mock(Logger.class);
        final PendingRefundStorage storage = new PendingRefundStorage(storageFile, logger);

        final UUID player1 = UUID.randomUUID();
        final UUID player2 = UUID.randomUUID();

        final Map<UUID, List<CostItemQuote>> original = new ConcurrentHashMap<>();
        original.put(player1, new CopyOnWriteArrayList<>(List.of(
                new CostItemQuote(CostType.VAULT, 50.0, true, true, 50.0, false),
                new CostItemQuote(CostType.EXP_LEVELS, 3.0, false, true, 3.0, false)
        )));
        original.put(player2, new CopyOnWriteArrayList<>(List.of(
                new CostItemQuote(CostType.FOOD, 4.0, true, true, 4.0, false)
        )));

        storage.save(original);
        assertThat(Files.exists(storageFile)).isTrue();

        // Fresh storage instance reading from the same file
        final PendingRefundStorage reloadedStorage = new PendingRefundStorage(storageFile, logger);
        final Map<UUID, List<CostItemQuote>> loaded = reloadedStorage.load();

        assertThat(loaded).containsOnlyKeys(player1, player2);
        final List<CostItemQuote> p1Quotes = java.util.Objects.requireNonNull(loaded.get(player1));
        assertThat(p1Quotes).hasSize(2);
        assertThat(p1Quotes.get(0).type()).isEqualTo(CostType.VAULT);
        assertThat(p1Quotes.get(0).amount()).isEqualTo(50.0);
        assertThat(p1Quotes.get(0).required()).isTrue();
        assertThat(p1Quotes.get(1).type()).isEqualTo(CostType.EXP_LEVELS);
        assertThat(p1Quotes.get(1).amount()).isEqualTo(3.0);
        assertThat(p1Quotes.get(1).required()).isFalse();

        final List<CostItemQuote> p2Quotes = java.util.Objects.requireNonNull(loaded.get(player2));
        assertThat(p2Quotes).hasSize(1);
        assertThat(p2Quotes.get(0).type()).isEqualTo(CostType.FOOD);
        assertThat(p2Quotes.get(0).amount()).isEqualTo(4.0);
    }

    @Test
    void emptyMapDeletesStorageFile() {
        final Path storageFile = tempDir.resolve("pending_refunds.yml");
        final PendingRefundStorage storage = new PendingRefundStorage(storageFile, mock(Logger.class));

        final UUID player = UUID.randomUUID();
        final Map<UUID, List<CostItemQuote>> map = new ConcurrentHashMap<>();
        map.put(player, new CopyOnWriteArrayList<>(List.of(
                new CostItemQuote(CostType.VAULT, 10.0, true, true, 10.0, false)
        )));

        storage.save(map);
        assertThat(Files.exists(storageFile)).isTrue();

        // Save empty map
        storage.save(Map.of());
        assertThat(Files.exists(storageFile)).isFalse();
    }

    @Test
    void noopStorageDoesNotCreateFilesAndReturnsEmpty() {
        final PendingRefundStorage noop = PendingRefundStorage.noop();
        assertThat(noop.load()).isEmpty();

        noop.save(Map.of(UUID.randomUUID(), List.of(new CostItemQuote(CostType.VAULT, 10.0, true, true, 10.0, false))));
        assertThat(noop.load()).isEmpty();
    }

    @Test
    void corruptedFileGracefullyReturnsEmpty() throws IOException {
        final Path storageFile = tempDir.resolve("pending_refunds.yml");
        Files.writeString(storageFile, "[ invalid yaml :::", StandardCharsets.UTF_8);

        final Logger logger = mock(Logger.class);
        final PendingRefundStorage storage = new PendingRefundStorage(storageFile, logger);
        final Map<UUID, List<CostItemQuote>> loaded = storage.load();

        assertThat(loaded).isEmpty();
        org.mockito.Mockito.verify(logger).warn(org.mockito.ArgumentMatchers.contains("Failed to load pending refunds"), eq(storageFile), any());
    }

    @Test
    void saveReturnsFalseWhenWriteFailsAndPreservesTransactionIds() throws IOException {
        final Path blockingFile = tempDir.resolve("not_a_directory");
        Files.writeString(blockingFile, "blocking", StandardCharsets.UTF_8);
        final Path invalidTarget = blockingFile.resolve("pending_refunds.yml");

        final Logger logger = mock(Logger.class);
        final PendingRefundStorage failingStorage = new PendingRefundStorage(invalidTarget, logger);
        final Map<UUID, List<CostItemQuote>> map = new ConcurrentHashMap<>();
        final UUID txId = UUID.randomUUID();
        map.put(UUID.randomUUID(), new CopyOnWriteArrayList<>(List.of(
                new CostItemQuote(CostType.VAULT, 25.0, true, true, 25.0, false, txId)
        )));

        assertThat(failingStorage.save(map)).isFalse();
        org.mockito.Mockito.verify(logger).error(org.mockito.ArgumentMatchers.contains("Failed to save pending refunds"), eq(invalidTarget), any());

        // Verify transactionId round-trip on valid storage
        final Path validTarget = tempDir.resolve("valid_refunds.yml");
        final PendingRefundStorage validStorage = new PendingRefundStorage(validTarget, logger);
        assertThat(validStorage.save(map)).isTrue();
        final Map<UUID, List<CostItemQuote>> loaded = validStorage.load();
        final List<CostItemQuote> items = loaded.values().iterator().next();
        assertThat(items.get(0).transactionId()).isEqualTo(txId);
    }
}
