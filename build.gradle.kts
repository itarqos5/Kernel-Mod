plugins {
    base
}

val supportedMinecraftVersions = listOf(
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

val prepareArtifacts = tasks.register<Delete>("prepareArtifacts") {
    group = "build"
    description = "Removes previously collected artifacts so renamed or removed JARs cannot remain in build/libs."
    outputs.upToDateWhen { false }
    delete(layout.buildDirectory.dir("libs"))
}

val buildAll = tasks.register("buildAll") {
    group = "build"
    description = "Builds and collects every Kernel Fabric variant and the Kernel Knot Client."

    dependsOn(":knot-client:buildAndCollect")
    dependsOn(supportedMinecraftVersions.map { ":mod:$it:buildAndCollect" })
}

tasks.named("build") {
    dependsOn(buildAll)
}
