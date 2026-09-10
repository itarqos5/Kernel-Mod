plugins {
    java
}

val kernelJavaToolchains = extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>()
val fallbackSmoke = sourceSets.create("fallbackSmoke") {
    compileClasspath += sourceSets.main.get().output
}

val startupSmokeTasks = listOf(21, 25).flatMap { javaVersion ->
    listOf(false, true).map { fallback ->
        tasks.register<JavaExec>("startupAgent${if (fallback) "Fallback" else ""}Smoke$javaVersion") {
            group = "verification"
            description = "Tests the packaged startup agent on Java $javaVersion${if (fallback) " without optional dependencies" else " against Fabric"}."
            val agentJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
            dependsOn(agentJar, tasks.named(if (fallback) fallbackSmoke.classesTaskName else "testClasses"))
            javaLauncher = kernelJavaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(javaVersion) }
            classpath = files(agentJar) + (if (fallback) fallbackSmoke.output else sourceSets.test.get().output)
            if (!fallback) classpath += configurations.testRuntimeClasspath.get()
            mainClass = if (fallback) "dev.kernel.client.AgentFallbackSmoke" else "dev.kernel.client.startup.StartupAgentSmoke"
            jvmArgs("-javaagent:${agentJar.get().asFile.absolutePath}")
        }
    }
}

tasks.named("check") { dependsOn(startupSmokeTasks) }

group = property("knot_client_maven_group") as String
version = property("knot_client_version") as String
base.archivesName = "kernel-knot-client"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // Minecraft supplies LWJGL and its natives; no third-party binaries are bundled.
    compileOnly("org.lwjgl:lwjgl:3.3.3")
    compileOnly("org.lwjgl:lwjgl-glfw:3.3.3")
    // Supplied by Fabric's launcher classpath, never bundled into Kernel's artifacts.
    compileOnly("org.ow2.asm:asm-tree:9.9.1")
    testImplementation("org.ow2.asm:asm-tree:9.9.1")
    testImplementation("org.ow2.asm:asm-util:9.9.1")
    testImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    testImplementation("net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 21
    }

    jar {
        manifest {
            attributes(
                "Main-Class" to "dev.kernel.client.KernelKnotClient",
                "Premain-Class" to "dev.kernel.client.KernelAgent",
                "Implementation-Title" to "Kernel Knot Client",
                "Implementation-Version" to project.version
            )
        }
    }

    test {
        useJUnitPlatform()
        dependsOn(fallbackSmoke.classesTaskName)
        systemProperty("kernel.fallbackSmokeClasses", fallbackSmoke.output.classesDirs.singleFile.absolutePath)
    }

    for (javaVersion in listOf(21, 25)) {
        register<JavaExec>("startupCacheBenchmark$javaVersion") {
            group = "verification"
            description = "Measures isolated repeated class reads and target parsing on Java $javaVersion."
            dependsOn(testClasses)
            classpath = sourceSets.test.get().runtimeClasspath
            mainClass = "dev.kernel.client.startup.StartupCacheBenchmark"
            javaLauncher = kernelJavaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(javaVersion) }
            jvmArgs("-Xms512m", "-Xmx512m")
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds and copies the Kernel Knot Client JAR to the root build directory."

        dependsOn(jar, test, startupSmokeTasks, rootProject.tasks.named("prepareArtifacts"))
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs"))
    }
}
