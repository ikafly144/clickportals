package net.sabafly.clickportals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("UnstableApiUsage")
@ExtendWith(MockitoExtension.class)
class ClickPortalsLoaderTest {

    @Test
    void loadLibrariesReadsGeneratedResource() {
        final ClickPortalsLoader loader = new ClickPortalsLoader();
        final ClickPortalsLoader.PluginLibraries libraries = loader.loadLibraries();

        assertThat(libraries).isNotNull();
        assertThat(libraries.repositories())
                .isNotNull()
                .containsKey("MavenCentralMirror");
        assertThat(libraries.dependencies())
                .isNotNull();
    }

    @Test
    void classloaderSkipsResolverWhenNoDependenciesConfigured(
            @Mock final PluginClasspathBuilder classpathBuilder
    ) {
        final ClickPortalsLoader loader = new ClickPortalsLoader();
        loader.classloader(classpathBuilder);

        verifyNoInteractions(classpathBuilder);
    }
}
