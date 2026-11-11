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
package net.sabafly.clickportals.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class PluginYamlPermissionsTest {

    @Test
    void paperPluginYamlDeclaresAllPermissionNodesWithDefaultsAndAdminChildren() throws IOException {
        try (final InputStream in = PluginYamlPermissionsTest.class.getResourceAsStream("/paper-plugin.yml")) {
            assertThat(in).as("/paper-plugin.yml resource").isNotNull();
            final CommentedConfigurationNode root = YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .build()
                    .load();

            final ConfigurationNode perms = root.node("permissions");
            assertThat(perms.virtual()).isFalse();

            assertThat(perms.node(Permissions.USE, "default").getString()).isEqualTo("true");
            assertThat(perms.node(Permissions.PLACE, "default").getString()).isEqualTo("true");
            assertThat(perms.node(Permissions.REMOVE_ANY, "default").getString()).isEqualTo("op");
            assertThat(perms.node(Permissions.COMMAND_GIVE, "default").getString()).isEqualTo("op");
            assertThat(perms.node(Permissions.COMMAND_RELOAD, "default").getString()).isEqualTo("op");
            assertThat(perms.node(Permissions.BYPASS_LIMIT, "default").getString()).isEqualTo("op");
            assertThat(perms.node(Permissions.BYPASS_COST_WARP, "default").getString()).isEqualTo("op");
            assertThat(perms.node(Permissions.BYPASS_COST_PLACE, "default").getString()).isEqualTo("op");
            assertThat(perms.node(Permissions.ADMIN, "default").getString()).isEqualTo("op");

            final ConfigurationNode children = perms.node(Permissions.ADMIN, "children");
            assertThat(children.node(Permissions.USE).getBoolean()).isTrue();
            assertThat(children.node(Permissions.PLACE).getBoolean()).isTrue();
            assertThat(children.node(Permissions.REMOVE_ANY).getBoolean()).isTrue();
            assertThat(children.node(Permissions.COMMAND_GIVE).getBoolean()).isTrue();
            assertThat(children.node(Permissions.COMMAND_RELOAD).getBoolean()).isTrue();
            assertThat(children.node(Permissions.BYPASS_LIMIT).getBoolean()).isTrue();
            assertThat(children.node(Permissions.BYPASS_COST_WARP).getBoolean()).isTrue();
            assertThat(children.node(Permissions.BYPASS_COST_PLACE).getBoolean()).isTrue();
        }
    }

    @Test
    void permissionHelpersEvaluateOwnerAndNodesCorrectly() {
        final UUID ownerId = UUID.randomUUID();
        final UUID otherId = UUID.randomUUID();
        final Aetheryte aetheryte = new Aetheryte(
                UUID.randomUUID(),
                "Test",
                UUID.randomUUID(),
                "world",
                0,
                64,
                0,
                ownerId,
                1000L
        );

        final Permissible regularUser = mock(Permissible.class);
        when(regularUser.hasPermission(Permissions.USE)).thenReturn(true);
        when(regularUser.hasPermission(Permissions.PLACE)).thenReturn(true);

        assertThat(Permissions.canUse(regularUser)).isTrue();
        assertThat(Permissions.canPlace(regularUser)).isTrue();
        assertThat(Permissions.canRemove(regularUser, ownerId, aetheryte)).isTrue();
        assertThat(Permissions.canRemove(regularUser, otherId, aetheryte)).isFalse();
        assertThat(Permissions.canUseCommand(regularUser)).isFalse();

        final Permissible moderator = mock(Permissible.class);
        when(moderator.hasPermission(Permissions.REMOVE_ANY)).thenReturn(true);
        when(moderator.hasPermission(Permissions.COMMAND_RELOAD)).thenReturn(true);

        assertThat(Permissions.canRemove(moderator, otherId, aetheryte)).isTrue();
        assertThat(Permissions.canUseCommand(moderator)).isTrue();
        assertThat(Permissions.canReload(moderator)).isTrue();
        assertThat(Permissions.canGive(moderator)).isFalse();
    }
}
