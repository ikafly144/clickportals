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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.plugin.configuration.PluginMeta;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.sabafly.clickportals.config.PluginConfig;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class UpdateCheckerTest {

    @Test
    void selectsHighestCompatibleStableReleaseIgnoringPrereleasesByDefault() {
        final String json = """
                [
                  {
                    "id": "verAlpha",
                    "version_number": "0.6.0-alpha.1",
                    "version_type": "alpha",
                    "game_versions": ["26.3"],
                    "loaders": ["paper", "folia"],
                    "status": "listed"
                  },
                  {
                    "id": "verOtherMc",
                    "version_number": "0.7.0",
                    "version_type": "release",
                    "game_versions": ["26.4"],
                    "loaders": ["paper"],
                    "status": "listed"
                  },
                  {
                    "id": "verStable",
                    "version_number": "0.5.0",
                    "version_type": "release",
                    "game_versions": ["26.3"],
                    "loaders": ["paper", "folia"],
                    "status": "listed"
                  },
                  {
                    "id": "verOlder",
                    "version_number": "0.3.9",
                    "version_type": "release",
                    "game_versions": ["26.3"],
                    "loaders": ["paper"],
                    "status": "listed"
                  }
                ]
                """;

        final Optional<UpdateChecker.UpdateInfo> stable = UpdateChecker.findLatestUpdate(
                json, SemanticVersion.parse("0.4.0"), false, "26.3"
        );
        assertThat(stable).isPresent();
        assertThat(stable.get().versionNumber()).isEqualTo("0.5.0");
        assertThat(stable.get().versionType()).isEqualTo("release");
        assertThat(stable.get().pageUrl()).isEqualTo("https://modrinth.com/plugin/clickportals/version/verStable");

        final Optional<UpdateChecker.UpdateInfo> withPre = UpdateChecker.findLatestUpdate(
                json, SemanticVersion.parse("0.4.0"), true, "26.3"
        );
        assertThat(withPre).isPresent();
        assertThat(withPre.get().versionNumber()).isEqualTo("0.6.0-alpha.1");
        assertThat(withPre.get().versionType()).isEqualTo("alpha");
    }

    @Test
    void returnsEmptyWhenAlreadyUpToDateOrMalformedJson() {
        final String json = """
                [
                  {
                    "id": "cur",
                    "version_number": "0.4.0",
                    "version_type": "release",
                    "game_versions": ["26.3"],
                    "loaders": ["paper", "folia"]
                  }
                ]
                """;
        assertThat(UpdateChecker.findLatestUpdate(json, SemanticVersion.parse("0.4.0"), true, "26.3"))
                .isEmpty();
        assertThat(UpdateChecker.findLatestUpdate("{not valid json", SemanticVersion.parse("0.4.0"), true, "26.3"))
                .isEmpty();
        assertThat(UpdateChecker.findLatestUpdate("", SemanticVersion.parse("0.4.0"), true, "26.3"))
                .isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void checkNowHandlesNetworkFailuresSafelyAndNotifiesPermittedPlayers() throws Exception {
        final Plugin plugin = mock(Plugin.class);
        final PluginMeta meta = mock(PluginMeta.class);
        final Server server = mock(Server.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getPluginMeta()).thenReturn(meta);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger(UpdateCheckerTest.class));
        when(plugin.getComponentLogger()).thenReturn(net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger(UpdateCheckerTest.class));
        when(meta.getVersion()).thenReturn("0.4.0");
        when(server.getMinecraftVersion()).thenReturn("26.3");

        final HttpClient failingClient = mock(HttpClient.class);
        when(failingClient.send(any(), any())).thenThrow(new IOException("offline"));

        final PluginConfig.UpdateCheckerSection cfg = new PluginConfig.UpdateCheckerSection(
                true, false, true, 0
        );
        final UpdateChecker failingChecker = new UpdateChecker(plugin, () -> cfg, Messages.defaults(), failingClient);
        failingChecker.checkNow();
        assertThat(failingChecker.latestUpdate()).isEmpty();

        final HttpClient okClient = mock(HttpClient.class);
        final HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                [{"id":"v050","version_number":"0.5.0","version_type":"release","game_versions":["26.3"],"loaders":["paper","folia"]}]
                """);
        when(okClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        final UpdateChecker okChecker = new UpdateChecker(plugin, () -> cfg, Messages.defaults(), okClient);
        okChecker.checkNow();
        assertThat(okChecker.latestUpdate()).isPresent();

        final Player nonAdmin = mock(Player.class);
        when(nonAdmin.isOnline()).thenReturn(true);
        when(nonAdmin.getUniqueId()).thenReturn(UUID.randomUUID());
        when(nonAdmin.hasPermission(Permissions.NOTIFY_UPDATE)).thenReturn(false);
        okChecker.notifyPlayerIfAvailable(nonAdmin);
        verify(nonAdmin, never()).sendMessage(any(Component.class));

        final Player admin = mock(Player.class);
        when(admin.isOnline()).thenReturn(true);
        when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
        when(admin.locale()).thenReturn(Locale.US);
        when(admin.hasPermission(Permissions.NOTIFY_UPDATE)).thenReturn(true);
        okChecker.notifyPlayerIfAvailable(admin);
        verify(admin).sendMessage(any(Component.class));
    }
}
