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
package net.sabafly.clickportals.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.sabafly.clickportals.config.PluginConfig;
import net.sabafly.clickportals.message.MessageKey;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Asynchronous Modrinth API v2 update checker for ClickPortals (#76).
 */
public final class UpdateChecker {

    private static final String MODRINTH_API_BASE = "https://api.modrinth.com/v2";
    private static final String MODRINTH_WEB_BASE = "https://modrinth.com/plugin";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Metadata describing an available update on Modrinth.
     */
    public record UpdateInfo(String versionNumber, String versionType, String pageUrl) {
    }

    private final Plugin plugin;
    private final Supplier<PluginConfig.UpdateCheckerSection> configSupplier;
    private final Messages messages;
    private final HttpClient httpClient;
    private final AtomicReference<@Nullable ScheduledTask> scheduledTask = new AtomicReference<>();
    private final AtomicReference<@Nullable UpdateInfo> latestUpdate = new AtomicReference<>();
    private final AtomicReference<@Nullable String> lastLoggedVersion = new AtomicReference<>();

    public UpdateChecker(
            final Plugin plugin,
            final Supplier<PluginConfig.UpdateCheckerSection> configSupplier,
            final Messages messages
    ) {
        this(
                plugin,
                configSupplier,
                messages,
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build()
        );
    }

    UpdateChecker(
            final Plugin plugin,
            final Supplier<PluginConfig.UpdateCheckerSection> configSupplier,
            final Messages messages,
            final HttpClient httpClient
    ) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.messages = messages;
        this.httpClient = httpClient;
    }

    /**
     * Starts or restarts background update checks according to the active configuration.
     */
    public synchronized void start() {
        stop();
        final PluginConfig.UpdateCheckerSection config = configSupplier.get();
        if (!config.isEnabled()) {
            latestUpdate.set(null);
            lastLoggedVersion.set(null);
            return;
        }
        final Server server = plugin.getServer();
        if (server == null || server.getAsyncScheduler() == null || !plugin.isEnabled()) {
            return;
        }
        final int intervalMinutes = config.checkIntervalMinutesOrDefault();
        server.getAsyncScheduler().runNow(plugin, ignored -> checkNow());
        if (intervalMinutes > 0) {
            final ScheduledTask task = server.getAsyncScheduler().runAtFixedRate(
                    plugin,
                    ignored -> checkNow(),
                    intervalMinutes,
                    intervalMinutes,
                    TimeUnit.MINUTES
            );
            scheduledTask.set(task);
        }
    }

    /**
     * Reloads update checker state after config changes.
     */
    public synchronized void reload() {
        lastLoggedVersion.set(null);
        start();
    }

    /**
     * Cancels any scheduled background update check tasks.
     */
    public synchronized void stop() {
        final ScheduledTask current = scheduledTask.getAndSet(null);
        if (current != null) {
            current.cancel();
        }
    }

    /**
     * Returns the latest discovered update if newer than the running plugin version.
     */
    public Optional<UpdateInfo> latestUpdate() {
        return Optional.ofNullable(latestUpdate.get());
    }

    /**
     * Notifies the player on join if an update is available and the player holds {@link Permissions#NOTIFY_UPDATE}.
     */
    public void notifyPlayerIfAvailable(final Player player) {
        final PluginConfig.UpdateCheckerSection config = configSupplier.get();
        if (!config.isEnabled() || !config.shouldNotifyOnJoin() || !player.isOnline()) {
            return;
        }
        if (!Permissions.canNotifyUpdate(player)) {
            return;
        }
        final UpdateInfo info = latestUpdate.get();
        if (info == null) {
            return;
        }
        final String currentVersion = plugin.getPluginMeta().getVersion();
        messages.send(
                player,
                MessageKey.UPDATE_AVAILABLE_PLAYER,
                Placeholder.unparsed("current", currentVersion),
                Placeholder.unparsed("latest", info.versionNumber()),
                Placeholder.unparsed("url", info.pageUrl())
        );
    }

    /**
     * Executes a synchronous HTTP query to Modrinth API v2. Intended to be invoked only on an async scheduler.
     */
    void checkNow() {
        final PluginConfig.UpdateCheckerSection config = configSupplier.get();
        if (!config.isEnabled() || !plugin.isEnabled()) {
            latestUpdate.set(null);
            return;
        }
        final String currentRaw = plugin.getPluginMeta().getVersion();
        final Optional<SemanticVersion> currentOpt = SemanticVersion.tryParse(currentRaw);
        if (currentOpt.isEmpty()) {
            plugin.getSLF4JLogger().debug("Skipping update check because current version '{}' is not valid SemVer.", currentRaw);
            return;
        }
        final SemanticVersion currentVersion = currentOpt.get();
        final String projectId = sanitizeProjectId(config.projectIdOrDefault());
        final String encodedProject = URLEncoder.encode(projectId, StandardCharsets.UTF_8);
        final String endpoint = MODRINTH_API_BASE
                + "/project/"
                + encodedProject
                + "/version?loaders=%5B%22paper%22,%22folia%22%5D";

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "ikafly144/clickportals/" + currentRaw + " (https://github.com/ikafly144/clickportals)")
                .GET()
                .build();

        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) {
                plugin.getSLF4JLogger().debug("Modrinth project '{}' was not found (HTTP 404); skipping update notification.", projectId);
                return;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                plugin.getSLF4JLogger().warn(
                        "Failed to check for updates on Modrinth: HTTP status {}",
                        response.statusCode()
                );
                return;
            }
            final String mcVersion = plugin.getServer().getMinecraftVersion();
            final Optional<UpdateInfo> found = findLatestUpdate(
                    response.body(),
                    currentVersion,
                    config.shouldIncludePrerelease(),
                    projectId,
                    mcVersion
            );
            if (found.isEmpty()) {
                latestUpdate.set(null);
                return;
            }
            final UpdateInfo info = found.get();
            latestUpdate.set(info);
            final String previousLogged = lastLoggedVersion.getAndSet(info.versionNumber());
            if (!Objects.equals(previousLogged, info.versionNumber())) {
                plugin.getComponentLogger().info(messages.get(
                        messages.defaultLocale(),
                        MessageKey.UPDATE_AVAILABLE_CONSOLE,
                        Placeholder.unparsed("current", currentRaw),
                        Placeholder.unparsed("latest", info.versionNumber()),
                        Placeholder.unparsed("url", info.pageUrl())
                ));
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final IOException | IllegalArgumentException e) {
            plugin.getSLF4JLogger().warn("Unable to check for updates from Modrinth: {}", e.getMessage());
        }
    }

    /**
     * Parses a Modrinth {@code GET /v2/project/{id}/version} JSON array response and returns the highest
     * compatible update strictly newer than {@code currentVersion}.
     */
    static Optional<UpdateInfo> findLatestUpdate(
            final String responseJson,
            final SemanticVersion currentVersion,
            final boolean includePrerelease,
            final String projectId,
            final @Nullable String minecraftVersion
    ) {
        if (responseJson == null || responseJson.isBlank()) {
            return Optional.empty();
        }
        final JsonElement parsedRoot;
        try {
            parsedRoot = JsonParser.parseString(responseJson);
        } catch (final JsonSyntaxException e) {
            return Optional.empty();
        }
        if (!parsedRoot.isJsonArray()) {
            return Optional.empty();
        }

        final String cleanProjectId = sanitizeProjectId(projectId);
        final JsonArray array = parsedRoot.getAsJsonArray();
        SemanticVersion bestSemVer = currentVersion;
        @Nullable UpdateInfo bestInfo = null;

        for (final JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            final JsonObject obj = element.getAsJsonObject();

            final String status = readString(obj, "status");
            if (status != null && !status.equalsIgnoreCase("listed") && !status.equalsIgnoreCase("unlisted")) {
                continue;
            }

            final String versionNumber = readString(obj, "version_number");
            if (versionNumber == null) {
                continue;
            }
            final Optional<SemanticVersion> candidateOpt = SemanticVersion.tryParse(versionNumber);
            if (candidateOpt.isEmpty()) {
                continue;
            }
            final SemanticVersion candidate = candidateOpt.get();

            final String rawType = readString(obj, "version_type");
            final String versionType = rawType != null ? rawType.toLowerCase(Locale.ROOT) : "release";
            if (!includePrerelease && (!"release".equals(versionType) || candidate.isPreRelease())) {
                continue;
            }

            final List<String> loaders = readStringList(obj, "loaders");
            if (!loaders.isEmpty() && loaders.stream().noneMatch(l -> "paper".equalsIgnoreCase(l) || "folia".equalsIgnoreCase(l))) {
                continue;
            }

            final List<String> gameVersions = readStringList(obj, "game_versions");
            if (minecraftVersion != null
                    && !minecraftVersion.isBlank()
                    && !gameVersions.isEmpty()
                    && gameVersions.stream().noneMatch(v -> v.equalsIgnoreCase(minecraftVersion))) {
                continue;
            }

            if (candidate.isNewerThan(bestSemVer)) {
                bestSemVer = candidate;
                final String versionId = readString(obj, "id");
                final String versionPath = versionId != null ? versionId : versionNumber;
                final String url = MODRINTH_WEB_BASE
                        + "/"
                        + URLEncoder.encode(cleanProjectId, StandardCharsets.UTF_8)
                        + "/version/"
                        + URLEncoder.encode(versionPath, StandardCharsets.UTF_8);
                bestInfo = new UpdateInfo(versionNumber, versionType, url);
            }
        }

        return Optional.ofNullable(bestInfo);
    }

    private static @Nullable String readString(final JsonObject obj, final String key) {
        final JsonElement element = obj.get(key);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            final String value = element.getAsString().strip();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    private static List<String> readStringList(final JsonObject obj, final String key) {
        final JsonElement element = obj.get(key);
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        final JsonArray array = element.getAsJsonArray();
        final List<String> result = new ArrayList<>(array.size());
        for (final JsonElement item : array) {
            if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                final String str = item.getAsString().strip();
                if (!str.isEmpty()) {
                    result.add(str);
                }
            }
        }
        return result;
    }

    private static String sanitizeProjectId(final @Nullable String raw) {
        return (raw == null || raw.isBlank()) ? "clickportals" : raw.strip();
    }
}
