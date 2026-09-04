# Kernel Agent Guide

These instructions apply to the entire repository.

## Product intent

Kernel is intended to become an all-in-one, client-side Fabric optimization mod. Its main target is perceptual smoothness: consistent frame delivery, stronger frame-time lows, and fewer visible micro-stutters. It may later address startup and world-loading costs where a normal mod or an explicitly installed bootstrap component can do so safely.

Kernel is not currently an optimization mod in functional terms. This repository is only the initial build scaffold. Do not claim that it improves performance until a feature exists and has reproducible evidence.

## Architecture

Keep the two runtime layers separate:

### Fabric mod (`mod/`)

The Fabric mod runs inside Fabric Loader and owns all Minecraft-facing behavior. Future renderer, chunk, memory, frame-time, configuration, Mod Menu, and compatibility integrations belong here.

Stonecutter owns the Minecraft-version matrix. Shared source lives under `mod/src/`; generated/preprocessed version projects live under `mod/versions/`.

### JVM agent (`agent/`)

The agent is a small, version-independent early bootstrap layer. It may eventually provide an early loading window, startup measurements, narrowly scoped instrumentation, and delegation into Fabric.

Do not move ordinary Minecraft mod behavior into the agent. Before Fabric and Minecraft initialize, game registries, Fabric APIs, renderer state, resources, and normal mod lifecycle objects are unavailable or unsafe.

The agent must remain optional, auditable, and recoverable. Do not patch or overwrite Minecraft or Fabric Loader JARs unless the user explicitly changes that architectural decision.

## Supported targets

The configured Minecraft targets are:

- `1.21.4`
- `1.21.5`
- `1.21.6`
- `1.21.8`
- `1.21.9`
- `1.21.10`
- `1.21.11`
- `26.1.2`
- `26.2`

Java 21 is used for 1.21.x and the agent. Java 25 is used for 26.x.

Do not remove, add, or broaden supported game versions without explicit user direction. Prefer shared code plus small version-specific branches/adapters over reflection-heavy universal patches.

## Versions and artifacts

Both components currently start at `0.1.0`.

- Fabric mod version: `mod_version` in `gradle.properties`
- Agent version: `agent_version` in `gradle.properties`

Expected artifacts:

- `kernel-fabric-[mod version]+[Minecraft version].jar`
- `kernel-fabric-agent-[agent version].jar`

Never bump either version automatically. Only change `mod_version` or `agent_version` when the user explicitly instructs you to bump that component. If only one component changes, do not assume the other version should change.

## Current implementation status

Implemented:

- Stonecutter 0.9.8 multi-version structure.
- Loom Back Compat configuration for official mappings across 1.21.x and 26.x.
- No-op `ClientModInitializer` in the Fabric module.
- No-op Java instrumentation `premain` and `agentmain` methods.
- Agent manifest entries required for command-line or dynamic agent loading.
- Aggregate `buildAll` task and release-shaped artifact collection.

Not implemented:

- Bootstrap installation, discovery, hashing, updates, rollback, or repair.
- Launcher detection or launcher-profile modification.
- Automatic game exit or relaunch messaging.
- Installer/helper GUI, custom title bar, taskbar integration, or approved artwork.
- Early GLFW window, progress reporting, OpenGL context transfer, or Minecraft window adoption.
- Asynchronous Mixin preparation or transformed-class caching.
- Resource-pack preparation changes or processed-resource caching.
- Startup profiler or stutter-attribution overlay.
- Frame-time governor, integrated-server coordination, input changes, chunk scheduling, renderer replacement, memory optimization, or world-generation optimization.
- Mod Menu integration and user-facing settings.
- Sodium, FerriteCore, ModernFix, Lithium, C2ME, Entity Culling, or any other third-party implementation or bundled code.

The user supplied a preliminary lightning-bolt icon, but asked to approve an upscaled/gradient preview before it is included. Do not add that icon or a replacement to the mod until approval is explicit.

## Ideas under consideration, not decisions

- A direct Sodium-class renderer competitor or a legally compliant Sodium-derived implementation.
- An all-in-one set of renderer, memory, chunk, world-generation, and smoothness improvements.
- A thin early bootstrapper installed through reversible launcher metadata rather than a loader fork.
- A NeoForge-style early window whose GLFW handle is later adopted by Minecraft.
- Parallel preparation with ordered, single-threaded application for startup work.
- Strictly keyed processed-resource and startup caches.
- Frame-time attribution and cooperative work-budget coordination.

Treat every item above as unimplemented research. Check overlap, licenses, compatibility, and measurable benefit before recommending or implementing one. Never copy or bundle Sodium or another mod merely to accelerate development; obtain explicit approval and satisfy its license first.

## Change reporting

Every agent that changes this repository must report these sections in its final handoff:

- **Fabric mod:** exact mod-side changes, or `No changes`.
- **Agent:** exact agent-side changes, or `No changes`.
- **Build/docs:** build-system and documentation changes.
- **Validation:** commands run and their results.
- **Versions:** current mod and agent versions, explicitly stating whether either changed.
- **Unimplemented:** any requested behavior deliberately left incomplete or blocked.

When functionality changes, update the implementation-status sections in both this file and `README.md` so later agents do not mistake an idea for completed work.

## Commit policy

Make atomic commits: one coherent concern per commit. Use short imperative subjects with a type prefix, for example:

- `feat: add frame-time telemetry`
- `bug: prevent stale bootstrap replacement`
- `perf: reduce chunk upload allocations`
- `chore: update Stonecutter targets`
- `build: collect agent artifact`
- `docs: record bootstrap constraints`
- `test: cover cache invalidation`
- `refactor: isolate version adapters`

Do not combine unrelated Fabric mod, agent, build, and documentation work merely to reduce the number of commits. Do not rewrite or squash user commits unless explicitly asked.

## Validation

For build-system or shared-source changes, run:

```powershell
.\gradlew.bat buildAll
```

Confirm that `build/libs/` contains one correctly named mod JAR per supported Minecraft version and exactly one correctly named agent JAR. Inspect `fabric.mod.json` and the agent manifest when metadata or packaging changes.

For a change isolated to one component, a narrower task may be used during iteration, but the final validation should be proportional to the risk and reported honestly.

## Compatibility and safety rules

- Preserve Fabric's normal mod lifecycle and allow unrelated mods to load normally.
- Prefer public Fabric APIs; isolate unavoidable Mixins and version-specific internals.
- Fail open for optional optimization behavior when safe: disabling a Kernel optimization is better than corrupting state or preventing recovery.
- Never falsify loading progress or benchmark results.
- Never silently install global JVM options or alter every Java process on the machine.
- Make bootstrap installation reversible and retain enough state to restore the original launcher metadata.
- Avoid competing ownership of systems already replaced by another renderer or optimization mod; detect conflicts and provide clear diagnostics.
- Keep third-party source, assets, and binaries out of the repository until their licenses and the integration strategy have been explicitly approved.
