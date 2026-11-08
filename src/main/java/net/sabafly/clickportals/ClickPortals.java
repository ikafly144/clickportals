package net.sabafly.clickportals;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * ClickPortals main plugin class for Paper and Folia.
 */
public final class ClickPortals extends JavaPlugin {

    @Override
    public void onEnable() {
        getComponentLogger().info("ClickPortals enabled.");
    }

    @Override
    public void onDisable() {
        getComponentLogger().info("ClickPortals disabled.");
    }
}
