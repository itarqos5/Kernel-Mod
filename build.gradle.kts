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

val buildAll = tasks.register("buildAll") {
    group = "build"
    description = "Builds and collects every Kernel Fabric variant and the Kernel agent."

    dependsOn(":agent:buildAndCollect")
    dependsOn(supportedMinecraftVersions.map { ":mod:$it:buildAndCollect" })
}

tasks.named("build") {
    dependsOn(buildAll)
}
