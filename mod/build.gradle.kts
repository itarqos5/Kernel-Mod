plugins {
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod_version")}+${sc.current.version}"
base.archivesName = "kernel-fabric"

val requiredJava = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
}

loom {
    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run/${sc.current.version}")
    }
}

java {
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava

    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        val properties = mapOf(
            "id" to project.property("mod_id"),
            "name" to project.property("mod_name"),
            "version" to project.version,
            "minecraft" to sc.current.version,
            "loader" to project.property("fabric_loader_version"),
            "java" to requiredJava.majorVersion
        )

        inputs.properties(properties)
        filesMatching("fabric.mod.json") {
            expand(properties)
        }
    }

    withType<JavaCompile>().configureEach {
        options.release = requiredJava.majorVersion.toInt()
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds and copies this Minecraft version's remapped mod JAR to the root build directory."

        dependsOn(loomx.modJar)
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs"))
    }
}
