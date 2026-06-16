plugins {
    `java-library`
    id("floodgate.build-logic")
    id("io.freefair.lombok") version "6.3.0" apply false
}

allprojects {
    group = "org.geysermc.floodgate"
    version = property("version")!!
    description = "Allows Bedrock players to join Java edition servers while keeping the server in online mode"
}

val deployProjects = setOf(
    projects.api,
    // for future Floodgate integration + Fabric
    projects.core,
    projects.bungee,
    projects.spigot,
    projects.velocity
).map { it.dependencyProject }

//todo re-add checkstyle when we switch back to 2 space indention
// and take a look again at spotbugs someday

subprojects {
    apply {
        plugin("java-library")
        plugin("io.freefair.lombok")
        plugin("floodgate.build-logic")
    }

    // The bundled Lombok (1.18.22) only supports up to JDK 17, so the delombok task crashes
    // on newer JDKs (NoSuchFieldError JCImport.qualid). Pin a version that supports JDK 8-21+
    // so the build works regardless of the JDK running it (CI runner / local).
    extensions.configure<io.freefair.gradle.plugins.lombok.LombokExtension> {
        version.set("1.18.46")
    }

    val relativePath = projectDir.relativeTo(rootProject.projectDir).path

    if (relativePath.startsWith("database" + File.separator)) {
        group = rootProject.group as String + ".database"
        plugins.apply("floodgate.database-conventions")
    }

    when (this) {
        in deployProjects -> plugins.apply("floodgate.publish-conventions")
        else -> plugins.apply("floodgate.base-conventions")
    }
}