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

import com.google.gson.Gson;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jspecify.annotations.Nullable;

/**
 * Paper plugin library loader reading generated {@code /paper-libraries.json}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ClickPortalsLoader implements PluginLoader {

    private static final String LIBRARIES_RESOURCE = "/paper-libraries.json";

    @Override
    public void classloader(final PluginClasspathBuilder classpathBuilder) {
        final PluginLibraries libraries = loadLibraries();
        if (libraries.dependencies() == null || libraries.dependencies().isEmpty()) {
            return;
        }
        final MavenLibraryResolver resolver = new MavenLibraryResolver();
        libraries.asRepositories().forEach(resolver::addRepository);
        libraries.asDependencies().forEach(resolver::addDependency);
        classpathBuilder.addLibrary(resolver);
    }

    PluginLibraries loadLibraries() {
        try (final InputStream in = ClickPortalsLoader.class.getResourceAsStream(LIBRARIES_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(LIBRARIES_RESOURCE + " is missing from plugin resources");
            }
            final PluginLibraries parsed = new Gson()
                    .fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), PluginLibraries.class);
            if (parsed == null) {
                throw new IllegalStateException(LIBRARIES_RESOURCE + " is empty");
            }
            return parsed;
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to read " + LIBRARIES_RESOURCE, e);
        }
    }

    record PluginLibraries(
            @Nullable Map<String, String> repositories,
            @Nullable List<String> dependencies
    ) {
        Stream<Dependency> asDependencies() {
            final List<String> deps = dependencies == null ? List.of() : dependencies;
            return deps.stream()
                    .map(coords -> new Dependency(new DefaultArtifact(coords), "compile"));
        }

        Stream<RemoteRepository> asRepositories() {
            final Map<String, String> repos = repositories == null ? Map.of() : repositories;
            return repos.entrySet().stream()
                    .map(entry -> new RemoteRepository.Builder(entry.getKey(), "default", entry.getValue()).build());
        }
    }
}
