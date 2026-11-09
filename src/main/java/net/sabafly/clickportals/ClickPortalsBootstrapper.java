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
