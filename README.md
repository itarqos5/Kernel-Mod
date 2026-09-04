# Kernel Mod

Kernel is an experimental, client-side Fabric optimization mod intended to improve how smooth Minecraft feels, not merely increase the average FPS counter.

This repository is currently an initial build scaffold. The Fabric mod and JVM agent both load successfully but deliberately perform no optimization or bootstrap work yet.

## Project layout

- `mod/` — shared Fabric mod source managed across Minecraft versions by Stonecutter.
- `agent/` — version-independent JVM agent source.
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

The 1.21.x targets compile for Java 21. The 26.x targets compile for Java 25. The agent targets Java 21 so the same agent artifact can run on both Java generations.

## Building

Build and collect every Fabric variant and the agent:

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
kernel-fabric-agent-0.1.0.jar
```

`build` also runs the aggregate `buildAll` task.

## Current status

Implemented:

- Stonecutter multi-version Fabric build.
- Separate Fabric mod and JVM agent modules.
- No-op Fabric client entry point.
- No-op JVM `premain` and `agentmain` entry points.
- Versioned, collected output JARs.

Not implemented:

- Bootstrap installation or launcher-profile integration.
- First-run relaunch window.
- Early loading window or GLFW handoff.
- Startup caching or asynchronous preparation.
- Renderer, memory, chunk, world-generation, or frame-pacing optimizations.
- Mod Menu integration or configuration UI.

The proposed icon is intentionally not included yet; its revised preview must be approved first.

## Direction

Kernel is being explored as an all-in-one Fabric optimization mod. Compatibility, measurable frame-time improvements, and honest benchmarking take priority over feature claims. Reusing or forking third-party optimization code requires a separate technical and licensing decision; this scaffold contains no Sodium or other third-party mod code.

Contributor and coding-agent rules are documented in [AGENTS.md](AGENTS.md).
