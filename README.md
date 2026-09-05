# Kernel Mod

Kernel is an experimental, client-side Fabric optimization mod intended to improve how smooth Minecraft feels, not merely increase the average FPS counter.

This repository is in an early implementation stage. It contains original allocation-reduction work in common vertex paths, not a complete Sodium-equivalent renderer.

## Project layout

- `mod/` — shared Fabric mod source managed across Minecraft versions by Stonecutter.
- `knot-client/` — version-independent launcher entry point that delegates to Fabric Loader.
- `mod/versions/` — generated Stonecutter workspaces. Do not treat these as independent source trees.
- `build/libs/` — collected release-shaped JARs produced by the aggregate build.

## Supported Minecraft targets

- 1.21.4
- 1.21.5
- 1.21.6
- 1.21.8
- 1.21.9
- 1.21.10
- 1.21.11
- 26.1.2
- 26.2

The 1.21.x targets compile for Java 21. The 26.x targets compile for Java 25. The Knot Client targets Java 21 so the same launcher artifact can run on both Java generations.

## Building

Build and collect every Fabric variant and the Knot Client:

```powershell
.\gradlew.bat buildAll
```

On macOS or Linux:

```bash
./gradlew buildAll
```

Artifacts are collected in `build/libs/`:

```text
kernel-fabric-0.1.0+1.21.4.jar
kernel-fabric-0.1.0+1.21.5.jar
...
kernel-fabric-0.1.0+26.2.jar
kernel-knot-client-0.1.0.jar
```

`build` also runs the aggregate `buildAll` task.

## Current status

Implemented:

- Stonecutter multi-version Fabric build.
- Separate Fabric mod and Kernel Knot Client modules.
- A Fabric startup installer for official-launcher-style Fabric version profiles.
- Strict refusal of unknown main classes and unsupported launcher layouts.
- One-time backup and atomic rewrite of a recognized version profile.
- Content-addressed Knot Client installation, allowing safe updates without overwriting a loaded JAR.
- Consistent `dev.kernel.fabric` and `dev.kernel.client` package and Gradle group namespaces.
- A minimal `dev.kernel.client.KernelKnotClient` that forwards unchanged arguments to Fabric Loader.
- Versioned, collected output JARs.
- Allocation-reduced baked-quad uploads across every supported Minecraft version, including reusable convenience-upload arrays on 1.21.x.
- Allocation-free immediate position and 2D matrix transforms, plus reusable normal-transform scratch storage, wherever those APIs exist in the supported version matrix.
- The approved Kernel lightning icon and `literal.uu` author metadata.
- A hard Fabric incompatibility with Sodium because both mods take ownership of the same renderer hot path.

Not implemented:

- First-run relaunch window.
- Automatic shutdown after first-time installation.
- Launcher-profile support outside the official-launcher-style `versions/<id>/<id>.json` layout.
- Automatic profile restoration or uninstall UI; the original JSON backup is created but not consumed yet.
- Early loading window or GLFW handoff.
- Startup caching or asynchronous preparation.
- A complete chunk renderer, GPU submission redesign, renderer settings UI, or verified Sodium feature/performance parity.
- Memory, chunk-scheduling, world-generation, or frame-pacing optimizations.
- Mod Menu integration or configuration UI.

The approved black-and-white lightning icon is included in the Fabric mod metadata.

## Direction

Kernel is being developed as an all-in-one Fabric optimization mod. Its initial renderer work reduces allocations in Minecraft's baked-quad upload and immediate vertex-transform routines. These are only a few hot paths; Kernel does not yet match Sodium's renderer breadth or demonstrated performance. Compatibility, measurable frame-time improvements, and honest benchmarking take priority over feature claims. Kernel contains no Sodium or other third-party mod code, and Fabric Loader will reject installations that also contain Sodium.

On a recognized Fabric profile, the current mod bundles and installs the Knot Client, changes that profile's launcher `mainClass`, and leaves a `.kernel-backup` copy of the original JSON. On the following launch, the Knot Client immediately delegates to Fabric's original Knot entry point. Unsupported launchers are left untouched and Minecraft continues normally.

Contributor and coding-agent rules are documented in [AGENTS.md](AGENTS.md).
