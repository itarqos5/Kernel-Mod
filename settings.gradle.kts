pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Kernel-Mod"

include("knot-client")

stonecutter {
    create(":mod") {
        versions(
            "1.21.4",
            "1.21.5",
            "1.21.6",
            "1.21.8",
            "1.21.9",
            "1.21.10",
            "1.21.11",
            "26.1.2",
            "26.2"
        )
        vcsVersion = "26.2"
    }
}
