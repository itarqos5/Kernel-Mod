plugins {
    java
}

group = property("maven_group") as String
version = property("knot_client_version") as String
base.archivesName = "kernel-knot-client"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
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
                "Main-Class" to "kernel.client.KernelKnotClient",
                "Implementation-Title" to "Kernel Knot Client",
                "Implementation-Version" to project.version
            )
        }
    }

    test {
        useJUnitPlatform()
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds and copies the Kernel Knot Client JAR to the root build directory."

        dependsOn(jar, test, rootProject.tasks.named("prepareArtifacts"))
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs"))
    }
}
