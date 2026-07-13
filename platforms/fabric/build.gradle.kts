import java.nio.file.FileSystems
import java.nio.file.Files

plugins {
    id("platform-conventions")
    id("com.gradleup.shadow")
    id("architectury-plugin")
    id("dev.architectury.loom")
}

repositories {
    maven {
        name = "Ladysnake Mods"
        url = uri("https://maven.ladysnake.org/releases")
    }
}

architectury {
    platformSetupLoomIde()
    fabric()
}

val otg: Configuration by configurations.creating
configurations {
    implementation {
        extendsFrom(otg)
    }
}

dependencies {
    modImplementation("net.fabricmc:fabric-loader:${project.property("fabric_loader_version")}")
    modApi("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    otg(project(":common:common-core"))
    otg(project(path = ":platforms:shared", configuration = "namedElements")) { isTransitive = false }

    // High-performance cache - bundled in JAR
    otg("com.github.ben-manes.caffeine:caffeine:3.1.8")

    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")

    // Cardinal Components API for player portal state tracking
    modImplementation("org.ladysnake.cardinal-components-api:cardinal-components-base:${project.property("cardinal_components_version")}")
    modImplementation("org.ladysnake.cardinal-components-api:cardinal-components-entity:${project.property("cardinal_components_version")}")
}

loom {
    accessWidenerPath = file("src/main/resources/META-INF/otg.accesswidener")
    splitEnvironmentSourceSets()
}

tasks {
    processResources {
        inputs.property("version", project.property("otg_version"))
        inputs.property("minecraft_version", project.property("minecraft_version"))
        inputs.property("fabric_loader_version", project.property("fabric_loader_version"))

        filesMatching("fabric.mod.json") {
            val map = mapOf(
                "version" to inputs.properties["version"].toString(),
                "minecraft_version" to inputs.properties["minecraft_version"].toString(),
                "fabric_loader_version" to inputs.properties["fabric_loader_version"].toString(),
            )
            expand(map)
        }
    }

    shadowJar {
        dependencyFilter.apply {
            include(project(":common:common-annotation"))
            include(project(":common:common-util"))
            include(project(":common:common-customobject"))
            include(project(":common:common-generator"))
            include(project(":common:common-core"))
            include(project(":platforms:shared"))
            include(dependency("com.github.ben-manes.caffeine:caffeine"))
        }
        // Relocate Caffeine to avoid conflicts with other mods
        relocate("com.github.benmanes.caffeine", "com.pg85.otg.dependency.caffeine")
        exclude("architectury.common.json")
        configurations = listOf(otg)
        archiveClassifier.set("deobf-all")

        // Loom's remapJar injects the refmap key only into this module's own mixin
        // config; the shared module's configs pass through without one, so in the
        // production jar (intermediary runtime) mixin resolves members by their
        // named (mojmap) names and crashes on the first accessor. The refmap files
        // themselves are already in the jar — reference them explicitly here.
        // Fabric-only: on NeoForge the runtime is mojmap, named resolution is
        // correct there and these intermediary refmaps must NOT be referenced.
        doLast {
            val sharedRefmaps = mapOf(
                "otg-shared.mixins.json" to "shared-platforms_shared-refmap.json",
                "otg-shared-client.mixins.json" to "client-shared-platforms_shared-refmap.json",
            )
            FileSystems.newFileSystem(archiveFile.get().asFile.toPath()).use { fs ->
                sharedRefmaps.forEach { (config, refmap) ->
                    val path = fs.getPath(config)
                    if (Files.exists(path)) {
                        val json = Files.readString(path)
                        if (!json.contains("\"refmap\"")) {
                            Files.writeString(
                                path,
                                json.replaceFirst("{", "{\n  \"refmap\": \"$refmap\","),
                            )
                        }
                    }
                }
            }
        }
    }

    remapJar {
        injectAccessWidener = true
        dependsOn(shadowJar)
        inputFile.set(shadowJar.get().archiveFile)

        archiveVersion = project.property("otg_version").toString()
    }

    sourcesJar {
        //val commonSources = project(":platforms:shared").tasks.sourcesJar
        //def sharedSources = project(":platforms:shared").sourcesJar
        //dependsOn commonSources, sharedSources
        //from(commonSources.get().archiveFile.map { zipTree(it) })
        //from sharedSources.archiveFile.map { zipTree(it) }
    }
}

otgPlatform {
    productionJar.set(tasks.remapJar.flatMap { it.archiveFile })
}