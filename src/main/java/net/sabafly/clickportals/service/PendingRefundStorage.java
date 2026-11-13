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

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.sabafly.clickportals.cost.CostItemQuote;
import net.sabafly.clickportals.cost.CostType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Persists unrefunded player cost quotes to disk across server restarts (#15).
 */
public class PendingRefundStorage {

    private final @Nullable Path storagePath;
    private final @Nullable Logger logger;

    /**
     * Creates a new {@link PendingRefundStorage} persisting to {@code storagePath}.
     *
     * @param storagePath destination file path (e.g. plugins/ClickPortals/pending_refunds.yml), or {@code null} for no-op
     * @param logger SLF4J logger for error reporting, or {@code null}
     */
    public PendingRefundStorage(final @Nullable Path storagePath, final @Nullable Logger logger) {
        this.storagePath = storagePath;
        this.logger = logger;
    }

    /**
     * Returns an in-memory no-op {@link PendingRefundStorage} that does not touch disk.
     */
    public static PendingRefundStorage noop() {
        return new PendingRefundStorage(null, null);
    }

    /**
     * Loads pending refunds from disk. Returns an empty concurrent map if the file does not exist or storage is no-op.
     */
    public Map<UUID, List<CostItemQuote>> load() {
        if (storagePath == null || !Files.exists(storagePath)) {
            return new ConcurrentHashMap<>();
        }
        final Map<UUID, List<CostItemQuote>> result = new ConcurrentHashMap<>();
        try {
            final YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(storagePath)
                    .build();
            final ConfigurationNode root = loader.load();
            final ConfigurationNode refundsNode = root.node("refunds");
            if (refundsNode.isMap()) {
                for (final Map.Entry<Object, ? extends ConfigurationNode> entry : refundsNode.childrenMap().entrySet()) {
                    try {
                        final UUID playerId = UUID.fromString(entry.getKey().toString());
                        final List<CostItemQuote> items = new CopyOnWriteArrayList<>();
                        for (final ConfigurationNode itemNode : entry.getValue().childrenList()) {
                            final String rawType = itemNode.node("type").getString();
                            final CostType type = CostType.fromString(rawType);
                            final double amount = itemNode.node("amount").getDouble(0.0);
                            final boolean required = itemNode.node("required").getBoolean(true);
                            final String rawId = itemNode.node("id").getString();
                            UUID txId = null;
                            if (rawId != null && !rawId.isBlank()) {
                                try {
                                    txId = UUID.fromString(rawId);
                                } catch (final IllegalArgumentException ignored) {
                                    txId = UUID.randomUUID();
                                }
                            } else {
                                txId = UUID.randomUUID();
                            }
                            if (type != null && amount > 0.0 && Double.isFinite(amount)) {
                                items.add(new CostItemQuote(type, amount, required, true, amount, false, txId));
                            }
                        }
                        if (!items.isEmpty()) {
                            result.put(playerId, items);
                        }
                    } catch (final IllegalArgumentException ignored) {
                        // skip invalid UUID
                    }
                }
            }
        } catch (final Exception e) {
            if (logger != null) {
                logger.warn("Failed to load pending refunds from {}: {}", storagePath, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Persists the given pending refunds map to disk atomically. If the map is empty, deletes the storage file.
     *
     * @return {@code true} if the persistence operation succeeded, {@code false} if an error occurred
     */
    public synchronized boolean save(final Map<UUID, List<CostItemQuote>> pendingRefunds) {
        if (storagePath == null) {
            return true;
        }
        Path tempFile = null;
        try {
            boolean hasEntries = false;
            for (final List<CostItemQuote> list : pendingRefunds.values()) {
                if (list != null && !list.isEmpty()) {
                    hasEntries = true;
                    break;
                }
            }
            if (!hasEntries) {
                if (Files.exists(storagePath)) {
                    Files.deleteIfExists(storagePath);
                }
                return true;
            }
            final Path parent = storagePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            tempFile = storagePath.resolveSibling(storagePath.getFileName() + ".tmp");
            final YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(tempFile)
                    .indent(2)
                    .nodeStyle(NodeStyle.BLOCK)
                    .build();
            final BasicConfigurationNode root = BasicConfigurationNode.root();
            final ConfigurationNode refundsNode = root.node("refunds");
            for (final Map.Entry<UUID, List<CostItemQuote>> entry : pendingRefunds.entrySet()) {
                final List<CostItemQuote> list = entry.getValue();
                if (list != null && !list.isEmpty()) {
                    final ConfigurationNode playerNode = refundsNode.node(entry.getKey().toString());
                    for (final CostItemQuote quote : list) {
                        if (quote.type() != null && quote.effectiveDeduction() > 0.0) {
                            final ConfigurationNode itemNode = playerNode.appendListNode();
                            if (quote.transactionId() != null) {
                                itemNode.node("id").set(quote.transactionId().toString());
                            }
                            itemNode.node("type").set(quote.type().name().toLowerCase(Locale.ROOT));
                            itemNode.node("amount").set(quote.effectiveDeduction());
                            itemNode.node("required").set(quote.required());
                        }
                    }
                }
            }
            loader.save(root);
            try {
                Files.move(tempFile, storagePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (final Exception e) {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (final Exception ignored) {
                    // ignore cleanup failure
                }
            }
            if (logger != null) {
                logger.error("Failed to save pending refunds to {}: {}", storagePath, e.getMessage());
            }
            return false;
        }
    }
}
