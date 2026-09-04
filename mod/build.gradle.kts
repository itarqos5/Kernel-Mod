plugins {
    id("dev.kikugie.loom-back-compat")
}

group = property("fabric_maven_group") as String
version = "${property("mod_version")}+${sc.current.version}"
base.archivesName = "kernel-fabric"

val knotClientJar = project(":knot-client").tasks.named<Jar>("jar").flatMap { it.archiveFile }

val requiredJava = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
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
            "java" to requiredJava.majorVersion,
            "knot_client_version" to project.property("knot_client_version")
        )

        inputs.properties(properties)
        filesMatching(listOf("fabric.mod.json", "kernel-bootstrap.properties")) {
            expand(properties)
        }

        dependsOn(knotClientJar)
        from(knotClientJar) {
            into("kernel/bootstrap")
            rename { "kernel-knot-client.jar" }
        }
    }

    withType<JavaCompile>().configureEach {
        options.release = requiredJava.majorVersion.toInt()
    }

    test {
        useJUnitPlatform()
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds and copies this Minecraft version's remapped mod JAR to the root build directory."

        dependsOn(loomx.modJar, test, rootProject.tasks.named("prepareArtifacts"))
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs"))
    }
}
