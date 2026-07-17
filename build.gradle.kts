plugins {
    id("parent-logic")
    id ("architectury-plugin") version "3.4-SNAPSHOT" apply false
    id ("dev.architectury.loom") version "1.7-SNAPSHOT" apply false
    id ("maven-publish")
    // Newer 2.0.0 snapshots (.3+) are compiled for Java 24 (class major 68),
    // which Gradle 8.8 cannot load — .2.3 is the last Java 8-bytecode build
    id ("io.github.pacifistmc.forgix") version "2.0.0-SNAPSHOT.2.3"
}

defaultTasks = arrayListOf("build", "publishToMavenLocal")

val ignored = listOf("common", "platforms")

subprojects {
    if (!ignored.contains(project.name)) {
        apply(plugin = "base-conventions")
    }
}

version = project.property("otg_version").toString()
group = project.property("otg_group").toString()

// Prototype: merge the fabric + neoforge production jars into a single
// dual-loader jar (Distant Horizons-style). Run with: ./gradlew build mergeJars
forgix {
    archiveVersion.set(
        project.property("otg_version").toString() +
            project.property("otg_build").toString().let { if (it.isEmpty()) "" else "-$it" }
    )
    archiveClassifier.set("fabric-neoforge")
}

// remapJar tasks only exist after the platform projects are evaluated
gradle.projectsEvaluated {
    forgix {
        fabric {
            inputJar.set(
                project(":platforms:fabric").tasks
                    .named<org.gradle.jvm.tasks.Jar>("remapJar")
                    .flatMap { it.archiveFile }
            )
        }
        neoforge {
            inputJar.set(
                project(":platforms:neoforge").tasks
                    .named<org.gradle.jvm.tasks.Jar>("remapJar")
                    .flatMap { it.archiveFile }
            )
        }
    }
}

listOf(
    project(":platforms:fabric"),
    project(":platforms:neoforge"),
).forEach { proj ->
    proj.afterEvaluate {
        proj.tasks.withType<JavaCompile>() {
            options.compilerArgs.add("-Xmaxerrs")
            options.compilerArgs.add("5000")
        }
    }
}
