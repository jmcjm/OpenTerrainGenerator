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
    implementation(project(":platforms:shared"))

    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")

    // Cardinal Components API for player portal state tracking
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-base:${project.property("cardinal_components_version")}")
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-entity:${project.property("cardinal_components_version")}")
}

loom {
    //accessWidenerPath = file("src/main/resources/META-INF/otg.accesswidener")
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
        exclude("architectury.common.json")
        configurations = listOf(otg)
        archiveClassifier.set("deobf-all")
    }

    remapJar {
        //injectAccessWidener = true
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