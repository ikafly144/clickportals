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
package net.sabafly.clickportals;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.command.ClickPortalsCommand;
import net.sabafly.clickportals.config.ConfigManager;
import net.sabafly.clickportals.config.PluginConfig;
import net.sabafly.clickportals.dialog.AetheryteDialogFactory;
import net.sabafly.clickportals.item.AetheryteItemFactory;
import net.sabafly.clickportals.listener.AetheryteListener;
import net.sabafly.clickportals.message.MessageKey;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.service.AetheryteService;
import net.sabafly.clickportals.visual.HologramService;
import net.sabafly.clickportals.visual.NoopHologramService;
import net.sabafly.clickportals.visual.PacketEventsHologramService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * ClickPortals main plugin class for Paper and Folia.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ClickPortals extends JavaPlugin {

    private static final double MAX_INTERACT_DISTANCE_SQUARED = 36.0;

    private @Nullable ConfigManager configManager;
    private @Nullable Messages messages;
    private @Nullable AetheryteRegistry registry;
    private @Nullable HologramService hologramService;
    private @Nullable AetheryteService aetheryteService;
    private @Nullable AetheryteDialogFactory dialogFactory;
    private @Nullable AetheryteListener aetheryteListener;

    @Override
    public void onEnable() {
        final ConfigManager cfgManager = new ConfigManager(getDataPath().resolve("config.yml"), getSLF4JLogger());
        this.configManager = cfgManager;
        final PluginConfig config = cfgManager.load();

        final Messages msgService = Messages.load(
                getDataPath().resolve("lang"),
                config.defaultLocale(),
                config.useClientLocale(),
                getSLF4JLogger()
        );
        this.messages = msgService;

        final AetheryteRegistry aetheryteRegistry = new AetheryteRegistry();
        this.registry = aetheryteRegistry;

        for (final World world : Bukkit.getWorlds()) {
            aetheryteRegistry.loadWorld(world);
        }

        final HologramService activeHologramService = createHologramService(config, msgService);
        this.hologramService = activeHologramService;

        final AetheryteService service = new AetheryteService(this, aetheryteRegistry, activeHologramService, msgService);
        this.aetheryteService = service;
        final AetheryteDialogFactory dialogs = new AetheryteDialogFactory(service, msgService);
        this.dialogFactory = dialogs;

        bindInteractionHandler(activeHologramService, aetheryteRegistry, dialogs);

        AetheryteItemFactory.registerRecipe(config, msgService);

        final AetheryteListener listener = new AetheryteListener(
                this,
                aetheryteRegistry,
                dialogs,
                activeHologramService,
                msgService
        );
        this.aetheryteListener = listener;
        Bukkit.getPluginManager().registerEvents(listener, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    ClickPortalsCommand.createCommand(this, msgService),
                    "ClickPortals management command",
                    List.of("cp")
            );
        });

        for (final Player player : Bukkit.getOnlinePlayers()) {
            activeHologramService.refreshPlayer(player, aetheryteRegistry.getByWorld(player.getWorld().getUID()));
        }

        getComponentLogger().info("ClickPortals enabled ({} aetherytes loaded).", aetheryteRegistry.getAll().size());
    }

    @Override
    public void onDisable() {
        AetheryteItemFactory.unregisterRecipe();
        if (hologramService != null) {
            hologramService.destroyAll();
            hologramService = null;
        }
        if (registry != null) {
            registry.clear();
            registry = null;
        }
        aetheryteListener = null;
        dialogFactory = null;
        aetheryteService = null;
        messages = null;
        configManager = null;
        getComponentLogger().info("ClickPortals disabled.");
    }

    /**
     * Reloads configuration, language files, recipes, and client-side holograms on the global region scheduler (#19).
     */
    public void reloadPlugin(final CommandSender sender) {
        final ConfigManager cfgManager = this.configManager;
        final Messages msgService = this.messages;
        final AetheryteRegistry aetheryteRegistry = this.registry;
        final HologramService currentHologramService = this.hologramService;
        final AetheryteService service = this.aetheryteService;
        final AetheryteDialogFactory dialogs = this.dialogFactory;
        final AetheryteListener listener = this.aetheryteListener;
        if (cfgManager == null
                || msgService == null
                || aetheryteRegistry == null
                || currentHologramService == null
                || service == null
                || dialogs == null
                || listener == null) {
            return;
        }

        Bukkit.getGlobalRegionScheduler().execute(this, () -> {
            final PluginConfig reloadedConfig = cfgManager.load();
            msgService.reload(
                    getDataPath().resolve("lang"),
                    reloadedConfig.defaultLocale(),
                    reloadedConfig.useClientLocale(),
                    getSLF4JLogger()
            );
            AetheryteItemFactory.registerRecipe(reloadedConfig, msgService);

            final HologramService activeHologramService = resolveReloadedHologramService(
                    reloadedConfig,
                    msgService,
                    aetheryteRegistry,
                    dialogs
            );
            service.setHologramService(activeHologramService);
            listener.setHologramService(activeHologramService);

            for (final Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().run(this, task -> {
                    if (player.isOnline()) {
                        activeHologramService.refreshPlayer(
                                player,
                                aetheryteRegistry.getByWorld(player.getWorld().getUID())
                        );
                    }
                }, null);
            }
            msgService.send(sender, MessageKey.COMMAND_RELOADED);
            getComponentLogger().info("ClickPortals configuration and translations reloaded.");
        });
    }

    /**
     * Re-evaluates {@code hologram.enabled} on reload, switching between {@link PacketEventsHologramService}
     * and {@link NoopHologramService} and destroying existing displays when disabled.
     */
    private HologramService resolveReloadedHologramService(
            final PluginConfig reloadedConfig,
            final Messages msgService,
            final AetheryteRegistry aetheryteRegistry,
            final AetheryteDialogFactory dialogs
    ) {
        final HologramService current = this.hologramService != null
                ? this.hologramService
                : NoopHologramService.INSTANCE;
        if (!reloadedConfig.hologramEnabled()) {
            if (current != NoopHologramService.INSTANCE) {
                current.destroyAll();
            }
            this.hologramService = NoopHologramService.INSTANCE;
            return NoopHologramService.INSTANCE;
        }
        if (current == NoopHologramService.INSTANCE) {
            final HologramService created = createHologramService(reloadedConfig, msgService);
            bindInteractionHandler(created, aetheryteRegistry, dialogs);
            this.hologramService = created;
            return created;
        }
        current.updateSettings(reloadedConfig);
        return current;
    }

    /**
     * Registers the virtual interaction entity callback on the given {@link HologramService}.
     */
    private void bindInteractionHandler(
            final HologramService targetService,
            final AetheryteRegistry aetheryteRegistry,
            final AetheryteDialogFactory dialogs
    ) {
        targetService.setInteractionHandler((player, aetheryteId) ->
                player.getScheduler().run(this, task ->
                        aetheryteRegistry.getById(aetheryteId)
                                .filter(aetheryte -> isWithinReach(player, aetheryte))
                                .ifPresent(aetheryte -> dialogs.openWarpDialog(player, aetheryte)),
                        null)
        );
    }

    /**
     * Returns the active {@link ConfigManager}, if the plugin is enabled.
     */
    public @Nullable ConfigManager configManager() {
        return configManager;
    }

    /**
     * Returns the active {@link Messages} service, if the plugin is enabled.
     */
    public @Nullable Messages messages() {
        return messages;
    }

    /**
     * Checks that the player is in the same world and within interaction reach of the target Aetheryte.
     */
    private static boolean isWithinReach(final Player player, final Aetheryte aetheryte) {
        final Location loc = player.getLocation();
        if (loc == null) {
            return false;
        }
        final World world = loc.getWorld();
        return world != null
                && world.getUID().equals(aetheryte.worldId())
                && loc.distanceSquared(aetheryte.markerLocation(world)) <= MAX_INTERACT_DISTANCE_SQUARED;
    }

    /**
     * Creates the appropriate {@link HologramService} based on configuration and PacketEvents availability.
     */
    private HologramService createHologramService(final PluginConfig config, final Messages msgService) {
        if (!config.hologramEnabled()) {
            return NoopHologramService.INSTANCE;
        }
        if (Bukkit.getPluginManager().getPlugin("packetevents") != null) {
            getComponentLogger().info("PacketEvents detected; enabling client-side Aetheryte visuals and interaction.");
            return new PacketEventsHologramService(
                    config.hologramViewDistance(),
                    config.displayBlock(),
                    msgService
            );
        }
        return NoopHologramService.INSTANCE;
    }
}
