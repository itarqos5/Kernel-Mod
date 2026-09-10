plugins {
    id("dev.kikugie.loom-back-compat")
}

group = property("fabric_maven_group") as String
version = "${property("mod_version")}+${sc.current.version}"
base.archivesName = "kernel-fabric"

val knotClientJar = project(":knot-client").tasks.named<Jar>("jar").flatMap { it.archiveFile }
val kernelJavaToolchains = extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>()

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

        dependsOn(loomx.modJar, test, "vertexSortingSmoke", "chunkTaskQueueSmoke", "rendererSettingsSmoke", rootProject.tasks.named("prepareArtifacts"))
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs"))
    }

    register<JavaExec>("visibilityBenchmark") {
        group = "verification"
        description = "Compares Kernel's visibility solver with this Minecraft version's vanilla solver."
        dependsOn(testClasses)
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass = "dev.kernel.fabric.render.SectionVisibilityBenchmark"
        javaLauncher = kernelJavaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion) }
        jvmArgs("-Xms512m", "-Xmx512m")
    }

    register<JavaExec>("vertexSortingBenchmark") {
        group = "verification"
        description = "Compares Kernel's stable quad sorter with this Minecraft version's vanilla sorter."
        dependsOn(testClasses)
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass = "dev.kernel.fabric.render.VertexSortingBenchmark"
        javaLauncher = kernelJavaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion) }
        jvmArgs("-Xms512m", "-Xmx512m")
    }

    register<JavaExec>("vertexSortingSmoke") {
        group = "verification"
        description = "Checks the sorting factory through the real Fabric and Mixin runtime without starting the game."
        dependsOn(testClasses)
        classpath = sourceSets.test.get().runtimeClasspath.filter { it.exists() }
        mainClass = "dev.kernel.fabric.render.VertexSortingSmoke"
        javaLauncher = kernelJavaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion) }
        systemProperty("fabric.development", "true")
        systemProperty("fabric.gameVersion", sc.current.version)
        systemProperty("fabric.gameMappingNamespace", if (sc.current.parsed >= "26.1") "official" else "named")
        workingDir(layout.buildDirectory.dir("sorting-smoke-game").get().asFile)
        args("--gameDir", workingDir.absolutePath)
        doFirst { workingDir.mkdirs() }
    }

    check { dependsOn("vertexSortingSmoke") }

    register<JavaExec>("chunkTaskQueueSmoke") {
        group = "verification"
        description = "Checks native queued-task cancellation and selection through Fabric and Mixin."
        dependsOn(testClasses)
        classpath = sourceSets.test.get().runtimeClasspath.filter { it.exists() }
        mainClass = "dev.kernel.fabric.render.ChunkTaskQueueSmoke"
        javaLauncher = kernelJavaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion) }
        systemProperty("fabric.development", "true")
        systemProperty("fabric.gameVersion", sc.current.version)
        systemProperty("fabric.gameMappingNamespace", if (sc.current.parsed >= "26.1") "official" else "named")
        workingDir(layout.buildDirectory.dir("queue-smoke-game").get().asFile)
        args("--gameDir", workingDir.absolutePath)
        doFirst { workingDir.mkdirs() }
    }

    check { dependsOn("chunkTaskQueueSmoke") }

    register<JavaExec>("chunkTaskQueueBenchmark") {
        group = "verification"
        description = "Measures isolated queued-task workloads against the vanilla linear selection rules."
        dependsOn(testClasses)
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass = "dev.kernel.fabric.render.ChunkTaskQueueBenchmark"
        javaLauncher = kernelJavaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion) }
        jvmArgs("-Xms512m", "-Xmx512m")
    }

    register<JavaExec>("rendererSettingsSmoke") {
        group = "verification"
        description = "Checks persisted feature disabling and the settings screen through real Fabric and Mixin."
        dependsOn(testClasses)
        classpath = sourceSets.test.get().runtimeClasspath.filter { it.exists() }
        mainClass = "dev.kernel.fabric.config.RendererSettingsSmoke"
        javaLauncher = kernelJavaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion) }
        systemProperty("fabric.development", "true")
        systemProperty("fabric.gameVersion", sc.current.version)
        systemProperty("fabric.gameMappingNamespace", if (sc.current.parsed >= "26.1") "official" else "named")
        workingDir(layout.buildDirectory.dir("settings-disabled-smoke-game").get().asFile)
        args("--gameDir", workingDir.absolutePath)
        doFirst {
            val config = workingDir.resolve("config/kernel-renderer.properties")
            config.parentFile.mkdirs()
            rootProject.file("mod/src/test/resources/settings-disabled.properties").copyTo(config, overwrite = true)
        }
    }

    check { dependsOn("rendererSettingsSmoke") }
}
