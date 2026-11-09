package net.sabafly.clickportals;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.command.ClickPortalsCommand;
import net.sabafly.clickportals.config.PluginConfig;
import net.sabafly.clickportals.dialog.AetheryteDialogFactory;
import net.sabafly.clickportals.item.AetheryteItemFactory;
import net.sabafly.clickportals.listener.AetheryteListener;
import net.sabafly.clickportals.service.AetheryteService;
import net.sabafly.clickportals.visual.HologramService;
import net.sabafly.clickportals.visual.NoopHologramService;
import net.sabafly.clickportals.visual.PacketEventsHologramService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * ClickPortals main plugin class for Paper and Folia.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ClickPortals extends JavaPlugin {

    private @Nullable AetheryteRegistry registry;
    private @Nullable HologramService hologramService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        final PluginConfig config = PluginConfig.fromFileConfiguration(getConfig(), getSLF4JLogger());

        final AetheryteRegistry aetheryteRegistry = new AetheryteRegistry();
        this.registry = aetheryteRegistry;

        for (final World world : Bukkit.getWorlds()) {
            aetheryteRegistry.loadWorld(world);
        }

        final HologramService activeHologramService = createHologramService(config);
        this.hologramService = activeHologramService;

        final AetheryteService service = new AetheryteService(this, aetheryteRegistry, activeHologramService);
        final AetheryteDialogFactory dialogFactory = new AetheryteDialogFactory(service);

        AetheryteItemFactory.registerRecipe(config);

        Bukkit.getPluginManager().registerEvents(
                new AetheryteListener(this, aetheryteRegistry, dialogFactory, activeHologramService),
                this
        );

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    ClickPortalsCommand.createCommand(this),
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
        getComponentLogger().info("ClickPortals disabled.");
    }

    /**
     * Creates the appropriate {@link HologramService} based on configuration and PacketEvents availability.
     */
    private HologramService createHologramService(final PluginConfig config) {
        if (!config.hologramEnabled()) {
            return NoopHologramService.INSTANCE;
        }
        if (Bukkit.getPluginManager().getPlugin("packetevents") != null) {
            getComponentLogger().info("PacketEvents detected; enabling client-side Aetheryte holograms.");
            return new PacketEventsHologramService(config.hologramViewDistance());
        }
        return NoopHologramService.INSTANCE;
    }
}
