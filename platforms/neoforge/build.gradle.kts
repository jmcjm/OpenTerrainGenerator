plugins {
    id("platform-conventions")
    id("com.gradleup.shadow")
    id("architectury-plugin")
    id("dev.architectury.loom")
}

// platforms/shared and common project source sets are referenced in loom.mods below
// (dev runs) — make sure those projects are configured first.
evaluationDependsOn(":platforms:shared")
evaluationDependsOn(":common:common-annotation")
evaluationDependsOn(":common:common-util")
evaluationDependsOn(":common:common-customobject")
evaluationDependsOn(":common:common-generator")
evaluationDependsOn(":common:common-core")

repositories {
    maven {
        name = "NeoForged"
        url = uri("https://maven.neoforged.net/releases")
    }
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

val otg: Configuration by configurations.creating
configurations {
    implementation {
        extendsFrom(otg)
    }
}

dependencies {
    "neoForge"("net.neoforged:neoforge:${project.property("neo_version")}")

    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    otg(project(":common:common-core"))
    otg(project(":platforms:shared"))

    // High-performance cache - bundled in JAR
    otg("com.github.ben-manes.caffeine:caffeine:3.1.8")
    // Dev runs: libraries bundled into the production shadowJar must be put on the game
    // module path explicitly (FML module isolation does not see plain classpath entries)
    "forgeRuntimeLibrary"("com.github.ben-manes.caffeine:caffeine:3.1.8")
    "forgeRuntimeLibrary"("com.fasterxml.jackson.core:jackson-annotations:2.17.1")
    "forgeRuntimeLibrary"("com.fasterxml.jackson.core:jackson-core:2.17.1")
    "forgeRuntimeLibrary"("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    "forgeRuntimeLibrary"("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.1")
    // jackson-module-jsonSchema deliberately NOT included: its module descriptor requires
    // validation.api (module name that no modern javax/jakarta artifact provides), which
    // breaks the boot module graph. Nothing on the server runtime path uses it.
    "forgeRuntimeLibrary"("org.yaml:snakeyaml:2.2")

    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
}

loom {
    accessWidenerPath = file("src/main/resources/META-INF/otg.accesswidener")
    // NeoForge does NOT support splitEnvironmentSourceSets() — Architectury Loom 1.7
    // throws UnsupportedOperationException("Using Forge with split jars is not supported!")
    // in CompileConfiguration.java when extension.isForgeLike().
    // Client code from platforms/shared reaches NeoForge via transformProductionNeoForge.

    // Dev runs only: group platforms/shared classes+resources into mod `otg` so FML can
    // load the shared mixins (otg-shared.mixins.json) in runServer/runClient. Production
    // jars are unaffected (shared is merged via transformProductionNeoForge in shadowJar).
    mods {
        create("otg") {
            sourceSet(sourceSets.getByName("main"))
            val sharedSourceSets = project(":platforms:shared").extensions.getByType(SourceSetContainer::class.java)
            sourceSet(sharedSourceSets.getByName("main"))
            sourceSet(sharedSourceSets.getByName("client"))
            listOf(
                ":common:common-annotation",
                ":common:common-util",
                ":common:common-customobject",
                ":common:common-generator",
                ":common:common-core"
            ).forEach { path ->
                val ss = project(path).extensions.getByType(SourceSetContainer::class.java)
                sourceSet(ss.getByName("main"))
            }
        }
    }
}

tasks {
    processResources {
        inputs.property("version", project.property("otg_version"))
        inputs.property("minecraft_version", project.property("minecraft_version"))
        inputs.property("neo_version", project.property("neo_version"))

        filesMatching("META-INF/neoforge.mods.toml") {
            val map = mapOf(
                "version" to inputs.properties["version"].toString(),
                "minecraft_version" to inputs.properties["minecraft_version"].toString(),
                "neo_version" to inputs.properties["neo_version"].toString(),
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
            // shared excluded from dep filter — included via NeoForge-transformed jar below
            include(dependency("com.github.ben-manes.caffeine:caffeine"))
        }
        dependsOn(":platforms:shared:transformProductionNeoForge")
        from(zipTree(project(":platforms:shared").layout.buildDirectory.file("libs/shared-${project.property("otg_version")}-SNAPSHOT-transformProductionNeoForge.jar")))
        relocate("com.github.benmanes.caffeine", "com.pg85.otg.dependency.caffeine")
        exclude("architectury.common.json")
        configurations = listOf(otg)
        archiveClassifier.set("deobf-all")
    }

    remapJar {
        injectAccessWidener = true
        dependsOn(shadowJar)
        inputFile.set(shadowJar.get().archiveFile)
        archiveVersion = project.property("otg_version").toString()
    }
}

otgPlatform {
    productionJar.set(tasks.remapJar.flatMap { it.archiveFile })
}
