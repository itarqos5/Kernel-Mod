# Kernel Mod

Kernel is an experimental, client-side Fabric optimization mod intended to improve how smooth Minecraft feels, not merely increase the average FPS counter.

This repository is in an early implementation stage. It contains original optimizations for vertex allocation, section connectivity, quad sorting and pending section-task selection. A complete Sodium-equivalent renderer remains a future milestone.

## Project layout

- `mod/` — shared Fabric mod source managed across Minecraft versions by Stonecutter.
- `knot-client/` — version-independent early loading window, launcher entry point and optional startup-cache Java agent.
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

- A Kernel GLFW loading window before Fabric's Knot client initializes, followed by Minecraft adopting the same OpenGL window. Early class/mod/Mixin activity uses an indeterminate bar; native resource loading supplies actual resource progress and sampled asset lookups. Minecraft's completion and error lifecycle remains intact. See [loading window and recovery](docs/BOOTSTRAP_WINDOW.md).
- Real startup, window-adoption, settings-interaction and clean-exit probes on every supported game target on the available Windows/AMD machine. Other operating systems, graphics backends, drivers and display configurations still require testing.

- Stonecutter multi-version Fabric build.
- Separate Fabric mod and Kernel Knot Client modules.
- A Fabric startup installer for official-launcher-style Fabric version profiles.
- Strict refusal of unknown main classes and unsupported launcher layouts.
- One-time backup and atomic rewrite of a recognized version profile.
- Content-addressed Knot Client installation, allowing safe updates without overwriting a loaded JAR.
- Consistent `dev.kernel.fabric` and `dev.kernel.client` package and Gradle group namespaces.
- A minimal `dev.kernel.client.KernelKnotClient` that forwards unchanged arguments to Fabric Loader.
- Profile-local installation of the optional startup-cache agent from the same content-addressed Knot Client JAR, preserving unrelated JVM/game arguments and the original profile backup.
- Bounded, per-launch reuse of raw JAR class entries and byte-identical Mixin target readers. Fabric still resolves resources, enforces classloader isolation, runs its bytecode provider and transformations, and produces fresh mutable target trees.
- Startup-cache hooks audited against exact Fabric Loader 0.19.3 class bytes, with normal-loading fallback for different bytecode or missing optional dependencies, and cache release at game-load completion or after three minutes.
- Packaged-agent process tests on Java 21 and Java 25, plus cache mutation/isolation, eviction, shutdown and launcher-update regression tests.
- Versioned, collected output JARs.
- Allocation-reduced baked-quad uploads across every supported Minecraft version, including reusable convenience-upload arrays for exact native BufferBuilder consumers on 1.21.x. Custom consumers keep independently owned arrays and reentrant normal values.
- Allocation-free immediate position and 2D matrix transforms, plus reusable normal-transform scratch storage, wherever those APIs exist in the supported version matrix.
- Allocation-reduced entity/model-part transforms and cube emission using reusable rotation and normal scratch values plus scalar position transforms.
- Reusable pose-stack entries on 1.21.4, matching the pooling strategy introduced by newer Minecraft versions, plus reusable normal-matrix scratch storage on every supported target.
- Allocation-free cached block-face visibility lookups using a bounded per-thread identity cache while retaining Minecraft's original occlusion rules.
- Reentrant per-thread lighting-array scratch for the legacy 1.21.4 block tessellator, eliminating two temporary arrays per emitted block quad.
- Scalar weighted fluid-corner height accumulation across the supported 1.21.x targets, eliminating a temporary two-float array per calculation.
- Reentrant per-thread fixed-seed model random sources across the supported 1.21.x targets, eliminating a temporary random-source allocation per standalone model render; 26.x already keeps equivalent renderer-owned state.
- A FIFO, frame-budgeted chunk GPU-upload scheduler on every supported 1.21.x target. Normal render passes execute at least one upload but stop after 2 ms or 32 tasks, while renderer shutdown still drains completely and 1.21.6+ deferred mesh cleanup retains vanilla behavior.
- Driver-neutral chunk uploads: Kernel schedules Minecraft's existing `VertexBuffer`/graphics-device tasks without issuing raw OpenGL calls or selecting vendor extensions. The 26.x staged uber-buffer pipeline remains native and unchanged.
- Spatially indexed pending section tasks on every target, with stationary-camera heap reuse, prompt native-task cancellation, preserved distance/recompile priorities, and a live-priority path for custom subclasses. Worker execution and mesh lifecycle remain native. See [task scheduling and validation](docs/CHUNK_TASK_SCHEDULING.md).
- Original scanline section-face connectivity on every supported target, replacing per-cell flood-fill queues with reusable per-thread storage while preserving vanilla visibility results and visited-bit semantics. Minecraft's occlusion traversal remains in use.
- Differential visibility tests against each target's actual mapped vanilla implementation, including randomized sections, walls, tunnels, enclosed cavities, repeated calls and concurrent chunk builders; standalone visibility and startup-cache microbenchmarks.
- Stable radix sorting of translucent quad indices across every supported target, preserving vanilla distance evaluation, equal-key order, NaNs and signed zeros. Batches below 512 quads retain vanilla's sorter; larger batches use the radix path with an already-ordered shortcut. Temporary key/index arrays are reused with a 16,384-quad retention cap per thread, and larger inputs use temporary storage.
- Compatibility with custom distance functions, nested sorts, concurrent workers and independent returned arrays. Custom `VertexSorting` implementations remain in control; subclasses of the newer compact input format use the original sorter.
- Differential sorting tests against each target's actual vanilla implementation, real Fabric/Mixin factory smoke tests without launching the game, and an isolated sorting benchmark. Centroid generation, camera resort triggers, index uploads and blending remain vanilla.
- The approved Kernel lightning icon and `literal.uu` author metadata.
- A hard Fabric incompatibility with Sodium because both mods take ownership of the same renderer hot path.
- A lightning-icon button left of Options in title/pause menus opens Kernel video settings, also available through the native video settings list. Four translucent tabs use white highlights, native video-option callbacks, Apply/Done/Cancel drafts and restart-only optimization switches. English fallbacks and the icon work without Fabric API. See [settings and recovery](docs/RENDERER_SETTINGS.md).

- One-time conservative video recommendations from logical CPU count, JVM heap capacity and active GPU class, with an options backup and persistent marker that preserves subsequent manual choices. No settings window is opened at launch; Recommended also stages those values on demand.

Not implemented:

- First-run relaunch window.
- Automatic shutdown after first-time installation.
- Launcher-profile support outside the official-launcher-style `versions/<id>/<id>.json` layout.
- Automatic profile restoration or uninstall UI; the original JSON backup is created but not consumed yet.
- Early-window adoption for Vulkan or other graphics backends, and a complete OS/driver, fullscreen, DPI and accessibility validation matrix.
- Persistent startup caches, transformed-class caches, asynchronous Mixin preparation or processed-resource caching.
- A complete chunk mesh compiler, mesh-storage/draw-command replacement, persistent-mapped or multi-draw GPU submission system, occlusion traversal replacement, or verified Sodium feature/performance parity.
- Lithium-style game-logic optimizations or verified Lithium feature/performance parity.
- Reproducible end-to-end launch-time improvements; isolated repeated-operation microbenchmarks do not establish total startup gains.
- Physical AMD, Intel, NVIDIA, Apple, and software-driver compatibility/performance testing; the current upload scheduler is vendor-neutral by construction but has not been validated on that hardware matrix.
- Broader memory, server chunk-scheduling, world-generation, or frame-pacing optimizations.
- Mod Menu integration, speech-engine/controller validation and complete renderer-option parity.

The approved black-and-white lightning icon has a transparent background and is included in the Fabric mod metadata. Its original bolt shape is preserved.

## Direction

Kernel is being developed as an all-in-one Fabric optimization mod. Its initial renderer work optimizes translucent quad sorting and section-face connectivity, time-slices legacy chunk GPU uploads and reduces allocations in Minecraft's pose-stack, block-model tessellation, standalone model random selection, fluid-height calculation, baked-quad upload, immediate vertex-transform, entity/model-part rendering, and block-face visibility routines. These are only a few hot paths; Kernel does not yet match Sodium's renderer breadth or demonstrated performance. Compatibility, measurable frame-time improvements, and honest benchmarking take priority over feature claims. Kernel contains no Sodium or other third-party mod code, and Fabric Loader will reject installations that also contain Sodium. See [quad sorting behavior and measurement](docs/TRANSLUCENT_SORTING.md) for its scope and validation.

On a recognized Fabric profile, the mod bundles and installs the Knot Client, changes that profile's launcher `mainClass`, adds its profile-local Java agent argument, and leaves a `.kernel-backup` copy of the original JSON. On the following launch, the agent enables its audited startup-cache hooks and the Knot Client delegates to Fabric's original Knot entry point. Unsupported launchers are left untouched and Minecraft continues normally. No game or loader JAR is patched on disk, and no third-party libraries are bundled into the Knot Client; its optional ASM dependency is supplied by Fabric's launcher classpath.

The game-profile JVM option `-Dkernel.startupCache=false` disables startup caching. Cache contents live only in the current process and are released when initial loading completes, with a three-minute fallback expiry. See [startup cache behavior and measurement](docs/STARTUP_CACHES.md) for limits, recovery and benchmark commands.

The requested sequence and pinned comparison versions are tracked in [the parity plan](docs/PARITY_PLAN.md). The subsequent loading-window request moved that work forward; same-window OpenGL startup is now implemented. Both full renderer and game-logic parity remain incomplete. Frame Sync, a shader-pack rendering pipeline and broader world-generation work are still outstanding.

Contributor and coding-agent rules are documented in [AGENTS.md](AGENTS.md).
