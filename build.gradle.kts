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
    compileOnly(libs.jspecify)
    testCompileOnly(libs.jspecify)

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
}

runPaper.folia.registerTask {
    minecraftVersion(targetMcVersion)
    runDirectory = layout.projectDirectory.dir("run/folia")
}
