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
import java.util.UUID;
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
import net.sabafly.clickportals.model.AetheryteModelService;
import net.sabafly.clickportals.model.BetterModelAetheryteModelService;
import net.sabafly.clickportals.model.ModelAnimationTrigger;
import net.sabafly.clickportals.model.NoopAetheryteModelService;
import net.sabafly.clickportals.service.AetheryteService;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.VariationRegistry;
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

    private static final double BASE_INTERACT_REACH = 6.0;

    private @Nullable ConfigManager configManager;
    private @Nullable VariationRegistry variationRegistry;
    private @Nullable Messages messages;
    private @Nullable AetheryteRegistry registry;
    private @Nullable HologramService hologramService;
    private @Nullable AetheryteModelService modelService;
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

        final VariationRegistry variations = VariationRegistry.load(
                getDataPath().resolve("aetheryte"),
                getSLF4JLogger()
        );
        this.variationRegistry = variations;

        final AetheryteRegistry aetheryteRegistry = new AetheryteRegistry();
        this.registry = aetheryteRegistry;

        final AetheryteModelService modelSvc = createModelService();
        this.modelService = modelSvc;

        for (final World world : Bukkit.getWorlds()) {
            final List<Aetheryte> loaded = aetheryteRegistry.loadWorld(world);
            for (final Aetheryte aetheryte : loaded) {
                final AetheryteVariation var = variations.getOrDefault(aetheryte.variation());
                modelSvc.registerAetheryte(aetheryte, var, world);
            }
        }

        final HologramService activeHologramService = createHologramService(
                config,
                variations,
                msgService,
                this::hasExternalModel
        );
        this.hologramService = activeHologramService;

        final AetheryteService service = new AetheryteService(
                this,
                aetheryteRegistry,
                variations,
                activeHologramService,
                modelSvc,
                msgService,
                () -> cfgManager.config().maxAetherytesPerPlayer(),
                () -> cfgManager.config().placementCooldownTicks(),
                AetheryteService::resolveCurrentTick,
                PacketEventsHologramService::sendPlacementCooldown,
                (player, source, destination) -> AetheryteService.WarpEvaluation.permitted(msgService.freeCostLabel(player))
        );
        this.aetheryteService = service;
        final AetheryteDialogFactory dialogs = new AetheryteDialogFactory(service, msgService);
        this.dialogFactory = dialogs;

        bindInteractionHandler(activeHologramService, aetheryteRegistry, variations, dialogs);

        AetheryteItemFactory.registerRecipes(variations, msgService);

        final AetheryteListener listener = new AetheryteListener(
                this,
                aetheryteRegistry,
                service,
                dialogs,
                activeHologramService,
                msgService
        );
        this.aetheryteListener = listener;
        Bukkit.getPluginManager().registerEvents(listener, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    ClickPortalsCommand.createCommand(this, variations, msgService),
                    "ClickPortals management command",
                    List.of("cp")
            );
        });

        for (final Player player : Bukkit.getOnlinePlayers()) {
            activeHologramService.refreshPlayer(player, aetheryteRegistry.getByWorld(player.getWorld().getUID()));
        }

        getComponentLogger().info(
                "ClickPortals enabled ({} aetherytes, {} variations loaded).",
                aetheryteRegistry.getAll().size(),
                variations.ids().size()
        );
    }

    @Override
    public void onDisable() {
        AetheryteItemFactory.unregisterRecipes();
        if (modelService != null) {
            modelService.destroyAll();
            modelService = null;
        }
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
        variationRegistry = null;
        messages = null;
        configManager = null;
        getComponentLogger().info("ClickPortals disabled.");
    }

    /**
     * Reloads configuration, language files, variation files, crafting recipes, and client-side holograms
     * on the global region scheduler (#19, #37, #38).
     */
    public void reloadPlugin(final CommandSender sender) {
        final ConfigManager cfgManager = this.configManager;
        final VariationRegistry variations = this.variationRegistry;
        final Messages msgService = this.messages;
        final AetheryteRegistry aetheryteRegistry = this.registry;
        final HologramService currentHologramService = this.hologramService;
        final AetheryteService service = this.aetheryteService;
        final AetheryteDialogFactory dialogs = this.dialogFactory;
        final AetheryteListener listener = this.aetheryteListener;
        if (cfgManager == null
                || variations == null
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
            variations.reload(getDataPath().resolve("aetheryte"), getSLF4JLogger());
            try {
                AetheryteItemFactory.registerRecipes(variations, msgService);
            } catch (final Throwable t) {
                getSLF4JLogger().warn("Failed to refresh crafting recipes on reload: {}", t.getMessage());
            }

            if (this.modelService != null) {
                this.modelService.destroyAll();
            }
            final AetheryteModelService reloadedModelService = createModelService();
            this.modelService = reloadedModelService;
            service.setModelService(reloadedModelService);
            for (final World world : Bukkit.getWorlds()) {
                for (final Aetheryte aetheryte : aetheryteRegistry.getByWorld(world.getUID())) {
                    final AetheryteVariation var = variations.getOrDefault(aetheryte.variation());
                    reloadedModelService.registerAetheryte(aetheryte, var, world);
                }
            }

            final HologramService activeHologramService = resolveReloadedHologramService(
                    reloadedConfig,
                    variations,
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
            getComponentLogger().info(
                    "ClickPortals configuration, translations, and {} variations reloaded.",
                    variations.ids().size()
            );
        });
    }

    /**
     * Re-evaluates {@code hologram.enabled} on reload, switching between {@link PacketEventsHologramService}
     * and {@link NoopHologramService} and destroying existing displays when disabled.
     */
    private HologramService resolveReloadedHologramService(
            final PluginConfig reloadedConfig,
            final VariationRegistry variations,
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
            final HologramService created = createHologramService(
                    reloadedConfig,
                    variations,
                    msgService,
                    this::hasExternalModel
            );
            bindInteractionHandler(created, aetheryteRegistry, variations, dialogs);
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
            final VariationRegistry variations,
            final AetheryteDialogFactory dialogs
    ) {
        targetService.setInteractionHandler((player, aetheryteId) ->
                player.getScheduler().run(this, task ->
                        aetheryteRegistry.getById(aetheryteId)
                                .filter(aetheryte -> isWithinReach(player, aetheryte, variations))
                                .ifPresent(aetheryte -> {
                                    final AetheryteModelService currentModels = this.modelService;
                                    if (currentModels != null) {
                                        currentModels.triggerAnimation(aetheryte.id(), ModelAnimationTrigger.ON_INTERACT);
                                    }
                                    dialogs.openWarpDialog(player, aetheryte);
                                }),
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
     * Returns the active {@link VariationRegistry}, if the plugin is enabled.
     */
    public @Nullable VariationRegistry variationRegistry() {
        return variationRegistry;
    }

    /**
     * Returns the active {@link Messages} service, if the plugin is enabled.
     */
    public @Nullable Messages messages() {
        return messages;
    }

    /**
     * Returns the active {@link AetheryteModelService}, if the plugin is enabled (#46).
     */
    public @Nullable AetheryteModelService modelService() {
        return modelService;
    }

    /**
     * Checks that the player is in the same world and within interaction reach of the target Aetheryte,
     * scaling the reach allowance by the variation's hitbox size (#40).
     */
    private static boolean isWithinReach(
            final Player player,
            final Aetheryte aetheryte,
            final VariationRegistry variations
    ) {
        final Location loc = player.getLocation();
        if (loc == null) {
            return false;
        }
        final World world = loc.getWorld();
        if (world == null || !world.getUID().equals(aetheryte.worldId())) {
            return false;
        }
        final AetheryteVariation variation = variations.getOrDefault(aetheryte.variation());
        final double hitboxAllowance = Math.max(variation.hitbox().width(), variation.hitbox().height());
        final double maxReach = BASE_INTERACT_REACH + hitboxAllowance;
        return loc.distanceSquared(aetheryte.markerLocation(world)) <= maxReach * maxReach;
    }

    /**
     * Creates the appropriate {@link HologramService} based on configuration and PacketEvents availability.
     */
    private HologramService createHologramService(
            final PluginConfig config,
            final VariationRegistry variations,
            final Messages msgService,
            final java.util.function.Predicate<java.util.UUID> externalModelPredicate
    ) {
        if (!config.hologramEnabled()) {
            return NoopHologramService.INSTANCE;
        }
        if (Bukkit.getPluginManager().getPlugin("packetevents") != null) {
            getComponentLogger().info("PacketEvents detected; enabling client-side Aetheryte visuals and interaction.");
            return new PacketEventsHologramService(
                    config.hologramViewDistance(),
                    variations,
                    msgService,
                    externalModelPredicate
            );
        }
        return NoopHologramService.INSTANCE;
    }

    /**
     * Creates the appropriate {@link AetheryteModelService} based on BetterModel availability (#46).
     */
    private AetheryteModelService createModelService() {
        if (Bukkit.getPluginManager().isPluginEnabled("BetterModel")) {
            getComponentLogger().info("BetterModel detected; enabling BetterModel 3D animation engine.");
            return new BetterModelAetheryteModelService(getSLF4JLogger());
        }
        return NoopAetheryteModelService.INSTANCE;
    }

    /**
     * Checks whether an external 3D model (e.g. BetterModel) is currently active for the given Aetheryte (#46).
     *
     * @param aetheryteId the Aetheryte UUID to check
     * @return {@code true} if an external model is currently active, {@code false} otherwise
     */
    private boolean hasExternalModel(final UUID aetheryteId) {
        final AetheryteModelService current = this.modelService;
        return current != null && current.hasModel(aetheryteId);
    }
}
