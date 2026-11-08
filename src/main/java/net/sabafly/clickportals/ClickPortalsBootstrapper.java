package net.sabafly.clickportals;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper plugin bootstrapper for ClickPortals.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ClickPortalsBootstrapper implements PluginBootstrap {

    @Override
    public void bootstrap(final BootstrapContext context) {
        context.getLogger().info("Bootstrapping ClickPortals...");
    }

    @Override
    public JavaPlugin createPlugin(final PluginProviderContext context) {
        return new ClickPortals();
    }
}
