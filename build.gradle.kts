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

// Iteration counterpart to buildAll. buildAll runs check for all nine targets,
// which forks roughly a hundred JVMs for the smoke and benchmark suite; each one
// reads the Minecraft, Fabric and Mixin classpath back off disk, so on a
// mechanical disk the verification suite dominates the wall clock rather than
// compilation. assembleAll produces the same JARs and skips only verification,
// so it is for the edit/compile loop. buildAll remains the validation task that
// AGENTS.md requires before a build-system or shared-source change lands.
val assembleAll = tasks.register("assembleAll") {
    group = "build"
    description = "Builds and collects every variant's JAR without running the verification suite (use buildAll to validate)."

    dependsOn(":knot-client:assembleAndCollect")
    dependsOn(supportedMinecraftVersions.map { ":mod:$it:assembleAndCollect" })
}

tasks.named("build") {
    dependsOn(buildAll)
}
