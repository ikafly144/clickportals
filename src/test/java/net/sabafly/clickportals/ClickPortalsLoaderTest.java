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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.library.ClassPathLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("UnstableApiUsage")
@ExtendWith(MockitoExtension.class)
class ClickPortalsLoaderTest {

    @Test
    void loadLibrariesReadsGeneratedResourceWithConfigurate() {
        final ClickPortalsLoader loader = new ClickPortalsLoader();
        final ClickPortalsLoader.PluginLibraries libraries = loader.loadLibraries();

        assertThat(libraries).isNotNull();
        assertThat(libraries.repositories())
                .isNotNull()
                .containsKey("MavenCentralMirror");
        assertThat(libraries.dependencies())
                .isNotNull()
                .anyMatch(dep -> dep.startsWith("org.spongepowered:configurate-yaml:"))
                .anyMatch(dep -> dep.startsWith("org.spongepowered:configurate-core:"));
    }

    @Test
    void classloaderRegistersMavenLibraryResolver(
            @Mock final PluginClasspathBuilder classpathBuilder
    ) {
        final ClickPortalsLoader loader = new ClickPortalsLoader();
        loader.classloader(classpathBuilder);

        verify(classpathBuilder).addLibrary(any(ClassPathLibrary.class));
    }
}
