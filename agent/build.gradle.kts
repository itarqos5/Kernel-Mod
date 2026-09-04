plugins {
    java
}

group = property("maven_group") as String
version = property("agent_version") as String
base.archivesName = "kernel-fabric-agent"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 21
    }

    jar {
        manifest {
            attributes(
                "Premain-Class" to "io.github.itarqos5.kernel.agent.KernelAgent",
                "Agent-Class" to "io.github.itarqos5.kernel.agent.KernelAgent",
                "Can-Redefine-Classes" to "false",
                "Can-Retransform-Classes" to "false",
                "Implementation-Title" to "Kernel Fabric Agent",
                "Implementation-Version" to project.version
            )
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds and copies the Kernel agent JAR to the root build directory."

        dependsOn(jar)
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs"))
    }
}
