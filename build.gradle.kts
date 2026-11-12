import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import groovy.json.JsonOutput
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import xyz.jpenilla.resourcefactory.bukkit.Permission
import xyz.jpenilla.resourcefactory.paper.PaperPluginYaml
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    java
    `jvm-test-suite`
    alias(libs.plugins.run.paper)
    alias(libs.plugins.resource.factory.paper)
    alias(libs.plugins.shadow)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

val projectGroup = providers.gradleProperty("projectGroup").get()
val pluginVersion = providers.gradleProperty("pluginVersion").get()
val targetJavaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
val targetMcVersion = providers.gradleProperty("minecraftVersion").get()

group = projectGroup
version = pluginVersion

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://repo.codemc.io/repository/maven-releases/") {
        name = "codemc-releases"
    }
    maven("https://jitpack.io") {
        name = "jitpack"
    }
}

val paperLibrary: Configuration = configurations.create("paperLibrary") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

configurations.named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) {
    extendsFrom(paperLibrary)
}
configurations.named(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME) {
    extendsFrom(paperLibrary)
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.packetevents.spigot)
    compileOnly(libs.vault.api) {
        exclude(group = "org.bukkit")
    }
    compileOnly(libs.jspecify)
    testCompileOnly(libs.jspecify)

    paperLibrary(libs.configurate.core)
    paperLibrary(libs.configurate.yaml)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = targetJavaVersion
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        option("NullAway:AnnotatedPackages", projectGroup)
        option("NullAway:JSpecifyMode", "true")
        error("NullAway")
    }
}

@CacheableTask
abstract class GeneratePaperLibrariesTask : DefaultTask() {
    @get:Input
    abstract val repositories: MapProperty<String, String>

    @get:Input
    abstract val dependencies: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.mkdirs()
        val file = out.resolve("paper-libraries.json")
        val payload = mapOf(
            "repositories" to repositories.get(),
            "dependencies" to dependencies.get(),
        )
        file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(payload)) + "\n", Charsets.UTF_8)
    }
}

val defaultCentralMirror = "https://maven-central.storage-download.googleapis.com/maven2"
val declaredMavenRepos = repositories.withType<MavenArtifactRepository>()
    .matching { it.name != ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME }
    .associate { repo -> repo.name to repo.url.toString() }
    .toMutableMap()
    .apply { put("MavenCentralMirror", defaultCentralMirror) }

val paperLibraryCoordinates = providers.provider {
    paperLibrary.allDependencies
        .withType<ExternalModuleDependency>()
        .map { dep -> "${dep.group}:${dep.name}:${dep.version}" }
        .sorted()
}

val generatePaperLibraries = tasks.register<GeneratePaperLibrariesTask>("generatePaperLibraries") {
    repositories.set(declaredMavenRepos)
    dependencies.set(paperLibraryCoordinates)
    outputDir.set(layout.buildDirectory.dir("generated/paper-libraries"))
}

sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME) {
    resources.srcDir(generatePaperLibraries.flatMap { it.outputDir })
}

paperPluginYaml {
    name = rootProject.name
    version = project.version.toString()
    main = "$projectGroup.ClickPortals"
    bootstrapper = "$projectGroup.ClickPortalsBootstrapper"
    loader = "$projectGroup.ClickPortalsLoader"
    apiVersion = targetMcVersion
    foliaSupported = true
    authors.add("ikafly144")
    website = "https://github.com/ikafly144/clickportals"
    dependencies {
        server("packetevents", PaperPluginYaml.Load.BEFORE, false)
        server("Vault", PaperPluginYaml.Load.BEFORE, false)
    }
    permissions {
        register("clickportals.use") {
            description = "Use Aetheryte warps"
            default = Permission.Default.TRUE
        }
        register("clickportals.place") {
            description = "Place Aetherytes"
            default = Permission.Default.TRUE
        }
        register("clickportals.remove.any") {
            description = "Remove Aetherytes owned by others"
            default = Permission.Default.OP
        }
        register("clickportals.command.give") {
            description = "Give Aetheryte creation items via command"
            default = Permission.Default.OP
        }
        register("clickportals.command.reload") {
            description = "Reload ClickPortals configuration and translations"
            default = Permission.Default.OP
        }
        register("clickportals.bypass.limit") {
            description = "Bypass per-player Aetheryte placement limits and placement cooldowns"
            default = Permission.Default.OP
        }
        register("clickportals.bypass.cost.warp") {
            description = "Bypass Aetheryte warp costs"
            default = Permission.Default.OP
        }
        register("clickportals.bypass.cost.place") {
            description = "Bypass Aetheryte placement costs"
            default = Permission.Default.OP
        }
        register("clickportals.admin") {
            description = "Full administrative access to ClickPortals"
            default = Permission.Default.OP
            children(
                "clickportals.use",
                "clickportals.place",
                "clickportals.remove.any",
                "clickportals.command.give",
                "clickportals.command.reload",
                "clickportals.bypass.limit",
                "clickportals.bypass.cost.warp",
                "clickportals.bypass.cost.place",
            )
        }
    }
}

tasks.named<Jar>("jar") {
    archiveClassifier = "dev"
}

val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName = rootProject.name
    archiveClassifier = ""
    archiveVersion = project.version.toString()
    minimize()
}

tasks.named("assemble") {
    dependsOn(shadowJarTask)
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
            dependencies {
                implementation(platform(libs.junit.bom))
                implementation(platform(libs.mockito.bom))
                implementation(libs.mockito.core)
                implementation(libs.mockito.junit.jupiter)
                implementation(libs.assertj.core)
                implementation(libs.paper.api)
                implementation(libs.packetevents.spigot)
                implementation(libs.vault.api) {
                    exclude(group = "org.bukkit")
                }
            }
            targets {
                all {
                    testTask.configure {
                        jvmArgs("-XX:+EnableDynamicAgentLoading")
                        testLogging {
                            events("passed", "skipped", "failed")
                        }
                    }
                }
            }
        }
    }
}

spotless {
    java {
        target("src/**/*.java")
        licenseHeader(
            """
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
            """.trimIndent(),
            "^(package|import|@NullMarked)"
        )
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        leadingTabsToSpaces(4)
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
        leadingTabsToSpaces(4)
    }
}

tasks.named<RunServer>("runServer") {
    minecraftVersion(targetMcVersion)
    runDirectory = layout.projectDirectory.dir("run/paper")

    downloadPlugins {
        modrinth("packetevents", "${libs.versions.packetevents.get()}+spigot")
    }
}

runPaper.folia.registerTask {
    minecraftVersion(targetMcVersion)
    runDirectory = layout.projectDirectory.dir("run/folia")

    downloadPlugins {
        modrinth("packetevents", "${libs.versions.packetevents.get()}+spigot")
    }
}
