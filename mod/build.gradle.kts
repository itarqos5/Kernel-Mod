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

    runConfigs.create("guiSmoke") {
        client()
        generateRunConfig = false
        runDirectory = layout.buildDirectory.dir("gui-smoke-game")
        programArguments.addAll("--width", "960", "--height", "540", "--username", "KernelProbe")
    }
    runConfigs.create("guiPreview") {
        client()
        generateRunConfig = false
        runDirectory = layout.buildDirectory.dir("gui-smoke-game")
        jvmArguments.add("-Dkernel.guiProbe.preview=true")
        programArguments.addAll("--width", "1280", "--height", "720", "--username", "KernelPreview")
    }
    runConfigs.create("bootstrapSmoke") {
        client()
        mainClass.set("dev.kernel.client.KernelKnotClient")
        generateRunConfig = false
        runDirectory = layout.buildDirectory.dir("bootstrap-smoke-game")
        jvmArguments.add("-javaagent:" + knotClientJar.get().asFile.absolutePath)
        jvmArguments.add("-Dkernel.guiProbe.bootstrap=true")
        programArguments.addAll("--width", "960", "--height", "540", "--username", "KernelBootstrap")
    }
    runConfigs.create("frameSyncSmoke") {
        client()
        generateRunConfig = false
        runDirectory = layout.buildDirectory.dir("frame-sync-smoke-game")
        jvmArguments.add("-Dkernel.guiProbe.frameSync=true")
        programArguments.addAll("--width", "960", "--height", "540", "--username", "KernelFrameProbe")
    }
    runConfigs.create("shaderSmoke") {
        client()
        generateRunConfig = false
        runDirectory = layout.buildDirectory.dir("shader-smoke-game")
        jvmArguments.add("-Dkernel.guiProbe.shaders=true")
        programArguments.addAll("--width", "960", "--height", "540", "--username", "KernelShader")
    }
    for (mode in listOf("Baseline", "Optimized")) {
        runConfigs.create("worldGeneration$mode") {
            client()
            generateRunConfig = false
            runDirectory = layout.buildDirectory.dir("world-generation-${mode.lowercase()}-game")
            jvmArguments.add("-Dkernel.guiProbe.worldGeneration=true")
            // Test-local scheduling control: keep overlapping feature writes reproducible.
            jvmArguments.add("-Dmax.bg.threads=1")
            programArguments.addAll("--width", "960", "--height", "540", "--username", "KernelWorldProbe")
        }
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

        dependsOn(loomx.modJar, check, rootProject.tasks.named("prepareArtifacts"))
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

    register<JavaExec>("biomeJitterBenchmark") {
        group = "verification"
        description = "Measures seed-dependent biome offset reuse against this target's native calculation."
        dependsOn(testClasses)
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass = "dev.kernel.fabric.world.BiomeJitterBenchmark"
        javaLauncher = kernelJavaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion) }
        jvmArgs("-Xms512m", "-Xmx512m")
    }

    for (mode in listOf("Enabled", "Disabled", "Conflict")) {
        val worldSmoke = register<JavaExec>("worldOptimization${mode}Smoke") {
            group = "verification"
            description = "Checks biome selection through real Fabric/Mixin with $mode optimization ownership."
            dependsOn(testClasses)
            classpath = sourceSets.test.get().runtimeClasspath.filter { it.exists() }
            mainClass = "dev.kernel.fabric.world.WorldOptimizationSmoke"
            javaLauncher = kernelJavaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion) }
            systemProperty("fabric.development", "true")
            systemProperty("fabric.gameVersion", sc.current.version)
            systemProperty("fabric.gameMappingNamespace", if (sc.current.parsed >= "26.1") "official" else "named")
            systemProperty("kernel.worldProbe.expectedEnabled", mode == "Enabled")
            workingDir(layout.buildDirectory.dir("world-${mode.lowercase()}-smoke-game").get().asFile)
            args("--gameDir", workingDir.absolutePath)
            val conflict = layout.buildDirectory.dir("world-conflict-test-mod").get().asFile
            if (mode == "Conflict") classpath += files(conflict)
            doFirst {
                val config = workingDir.resolve("config/kernel-world.properties")
                config.parentFile.mkdirs()
                config.writeText("biome_offsets=${mode != "Disabled"}\n")
                if (mode == "Conflict") {
                    conflict.mkdirs()
                    conflict.resolve("fabric.mod.json").writeText("""{"schemaVersion":1,"id":"lithium","version":"0.0.0","name":"Kernel ownership test marker"}""")
                }
            }
        }
        check { dependsOn(worldSmoke) }
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

    val prepareGuiProbe = register<Sync>("prepareGuiProbe") {
        group = "verification"
        dependsOn(testClasses)
        from(sourceSets.test.get().output.classesDirs) { include("dev/kernel/fabric/verification/**") }
        from(rootProject.file("mod/src/test/resources/gui-probe"))
        into(layout.buildDirectory.dir("gui-probe-mod"))
    }

    named<JavaExec>("runGuiSmoke") {
        group = "verification"
        description = "Opens an isolated game, verifies renderer settings, captures three frames and exits."
        dependsOn(prepareGuiProbe)
        classpath += files(layout.buildDirectory.dir("gui-probe-mod"))
        val completion = layout.buildDirectory.file("gui-smoke-game/probe-complete.json")
        doFirst {
            val marker = completion.get().asFile
            check(!marker.exists() || marker.delete()) { "Cannot remove previous GUI probe completion marker." }
            val settings = marker.parentFile.resolve("config/kernel-renderer.properties")
            check(!settings.exists() || settings.delete()) { "Cannot reset isolated GUI probe settings." }
            val options = marker.parentFile.resolve("options.txt")
            options.parentFile.mkdirs()
            val lines = if (options.exists()) options.readLines().filterNot { it.startsWith("onboardAccessibility:") } else emptyList()
            options.writeText((lines + "onboardAccessibility:false").joinToString("\n", postfix = "\n"))
        }
        doLast { check(completion.get().asFile.isFile) { "Kernel GUI probe did not complete; inspect the game log." } }
    }
    named<JavaExec>("runGuiPreview") {
        dependsOn(prepareGuiProbe)
        classpath += files(layout.buildDirectory.dir("gui-probe-mod"))
    }
    named<JavaExec>("runBootstrapSmoke") {
        dependsOn(prepareGuiProbe, knotClientJar)
        classpath += files(knotClientJar, layout.buildDirectory.dir("gui-probe-mod"))
        val game = layout.buildDirectory.dir("bootstrap-smoke-game")
        doFirst {
            val directory = game.get().asFile
            directory.mkdirs()
            directory.resolve("options.txt").writeText("onboardAccessibility:false\nguiScale:2\n")
            val marker = directory.resolve("probe-complete.json")
            check(!marker.exists() || marker.delete())
            val settings = directory.resolve("config/kernel-renderer.properties")
            check(!settings.exists() || settings.delete())
        }
        doLast { check(game.get().file("probe-complete.json").asFile.isFile) { "Bootstrap probe did not complete." } }
    }
    named<JavaExec>("runFrameSyncSmoke") {
        dependsOn(prepareGuiProbe)
        classpath += files(layout.buildDirectory.dir("gui-probe-mod"))
        val game = layout.buildDirectory.dir("frame-sync-smoke-game")
        doFirst {
            val directory = game.get().asFile
            directory.mkdirs()
            directory.resolve("options.txt").writeText("onboardAccessibility:false\nguiScale:2\nrenderDistance:6\nsimulationDistance:5\n")
            val marker = directory.resolve("frame-sync-complete.json")
            check(!marker.exists() || marker.delete())
        }
        doLast { check(game.get().file("frame-sync-complete.json").asFile.isFile) { "Frame Sync probe did not complete." } }
    }
    named<JavaExec>("runShaderSmoke") {
        dependsOn(prepareGuiProbe)
        classpath += files(layout.buildDirectory.dir("gui-probe-mod"))
        if (providers.gradleProperty("kernelShaderLive").orNull == "true") systemProperty("kernel.guiProbe.liveModrinth", "true")
        val game = layout.buildDirectory.dir("shader-smoke-game")
        doFirst {
            val directory = game.get().asFile
            directory.mkdirs()
            directory.resolve("options.txt").writeText("onboardAccessibility:false\nguiScale:2\nrenderDistance:4\nsimulationDistance:5\npauseOnLostFocus:false\n")
            directory.resolve("config").mkdirs()
            directory.resolve("config/kernel-shaders.properties").writeText("selected=\n")
            val marker = directory.resolve("shader-probe-complete.json")
            check(!marker.exists() || marker.delete())
        }
        doLast { check(game.get().file("shader-probe-complete.json").asFile.isFile) { "Shader probe did not complete." } }
    }
    for (mode in listOf("Baseline", "Optimized")) {
        named<JavaExec>("runWorldGeneration$mode") {
            dependsOn(prepareGuiProbe)
            classpath += files(layout.buildDirectory.dir("gui-probe-mod"))
            val game = layout.buildDirectory.dir("world-generation-${mode.lowercase()}-game")
            doFirst {
                val directory = game.get().asFile
                directory.mkdirs()
                directory.resolve("options.txt").writeText("onboardAccessibility:false\nguiScale:2\nrenderDistance:4\nsimulationDistance:5\n")
                directory.resolve("config").mkdirs()
                directory.resolve("config/kernel-world.properties").writeText("biome_offsets=${mode == "Optimized"}\n")
                val marker = directory.resolve("world-generation-sha256.txt")
                check(!marker.exists() || marker.delete())
            }
            doLast { check(game.get().file("world-generation-sha256.txt").asFile.isFile) { "World generation probe did not complete." } }
        }
    }
    register("worldGenerationComparison") {
        group = "verification"
        description = "Creates two isolated worlds and compares nine full remote chunks with biome reuse off/on."
        dependsOn("runWorldGenerationBaseline", "runWorldGenerationOptimized")
        doLast {
            val baseline = layout.buildDirectory.file("world-generation-baseline-game/world-generation-sha256.txt").get().asFile.readText()
            val optimized = layout.buildDirectory.file("world-generation-optimized-game/world-generation-sha256.txt").get().asFile.readText()
            check(baseline == optimized) { "Generated block/biome output differs with biome reuse enabled." }
            logger.lifecycle("Kernel world generation comparison: nine chunk block/biome fingerprints match.")
        }
    }
    named("runWorldGenerationOptimized") { mustRunAfter("runWorldGenerationBaseline") }
}
