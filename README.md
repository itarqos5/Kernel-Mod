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

- [Smaller resource-reader buffers](docs/RESOURCE_READERS.md) during startup and resource reloads on every supported target. Native stream ownership, UTF-8 decoding and JDK reader behavior are retained; the initial character buffer uses 4 KiB instead of 16 KiB. The Optimizations tab provides an independent restart-only toggle. This is an allocation reduction, not a demonstrated total-launch speedup.

- Invocation-local chunk camera snapshots on 1.21.11, 26.1.2 and 26.2: unchanged native matrix copies
  are shared within one draw-preparation batch, with raw-value/property comparison and fresh copies for
  changed input. Previously uploaded snapshots are never mutated. The restart-only `chunk_uniforms`
  setting owns the hook; earlier targets keep their native paths. See [chunk camera snapshots](docs/CHUNK_UNIFORMS.md).

- Bounded preallocation for uncached local JAR class reads during bootstrap, avoiding intermediate read
  buffers for entries up to 1 MiB while preserving complete-stream reads, I/O errors and cache isolation.
  See [startup cache behavior and measurements](docs/STARTUP_CACHES.md).

- Bitset intersections for native AND queries on matching voxel grids, bounded native axis mapping for
  mismatched grids, and reusable callbacks for other block-shape queries. Native coordinate merging and custom behavior remain in control, with bounded
  reentrant storage, a restart-only setting and Lithium ownership detection. See [shape queries and validation](docs/SHAPE_QUERIES.md).

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
- A FIFO, frame-budgeted chunk GPU-upload scheduler on every supported 1.21.x target. Normal render passes execute at least one upload, then stop at an adaptive 0.25–2 ms budget or 32 tasks. Recent CPU work controls the budget, excluding upload cost and presentation/limiter waits. Renderer shutdown still drains completely and 1.21.6+ deferred mesh cleanup retains vanilla behavior. See [chunk upload scheduling](docs/CHUNK_UPLOADS.md) for controls and validation limits.
- Driver-neutral chunk uploads: Kernel schedules Minecraft's existing `VertexBuffer`/graphics-device tasks without issuing raw OpenGL calls or selecting vendor extensions. The 26.x staged uber-buffer pipeline remains native and unchanged.
- Spatially indexed pending section tasks on every target, with stationary-camera heap reuse, prompt native-task cancellation, preserved distance/recompile priorities, and a live-priority path for custom subclasses. Worker execution and mesh lifecycle remain native. See [task scheduling and validation](docs/CHUNK_TASK_SCHEDULING.md).
- Original scanline section-face connectivity on every supported target, replacing per-cell flood-fill queues with reusable per-thread storage while preserving vanilla visibility results and visited-bit semantics. Minecraft's occlusion traversal remains in use.
- Differential visibility tests against each target's actual mapped vanilla implementation, including randomized sections, walls, tunnels, enclosed cavities, repeated calls and concurrent chunk builders; standalone visibility and startup-cache microbenchmarks.
- Leaner camera visibility queries on all targets: the existing native frustum test omits unused containment
  categories when only visible/not-visible is needed. Full classifications and custom implementations
  retain their native paths. **View visibility tests** has its own restart-only switch; see
  [frustum validation and measurements](docs/FRUSTUM_TESTS.md).
- Stable radix sorting of translucent quad indices across every supported target, preserving vanilla distance evaluation, equal-key order, NaNs and signed zeros. Batches below 512 quads retain vanilla's sorter; larger batches use the radix path with an already-ordered shortcut. Temporary key/index arrays are reused with a 16,384-quad retention cap per thread, and larger inputs use temporary storage.
- Compatibility with custom distance functions, nested sorts, concurrent workers and independent returned arrays. Custom `VertexSorting` implementations remain in control; subclasses of the newer compact input format use the original sorter.
- Differential sorting tests against each target's actual vanilla implementation, real Fabric/Mixin factory smoke tests without launching the game, and an isolated sorting benchmark. Centroid generation, camera resort triggers, index uploads and blending remain vanilla.
- The approved Kernel lightning icon and `literal.uu` author metadata.
- Hard Fabric incompatibilities with Sodium and Iris because they overlap Kernel's renderer/shader ownership.
- A lightning-icon button left of Options in title/pause menus opens Kernel video settings, also available through the native video settings list. Four video/optimization tabs plus Shaders use translucent panels and white highlights. Video controls preserve native callbacks, Apply/Done/Cancel drafts and restart-only optimization switches. English fallbacks and the icon work without Fabric API. See [settings and recovery](docs/RENDERER_SETTINGS.md).

- One-time conservative video recommendations from logical CPU count, JVM heap capacity and active GPU class, with an options backup and persistent marker that preserves subsequent manual choices. No settings window is opened at launch; Recommended also stages those values on demand.

- Frame Sync with monitor-refresh pacing, preserved native FPS/VSync preferences, a GUI toggle, actual FPS and a labeled uncapped estimate that moves into F3. Nonblocking OpenGL timestamps have a CPU-only fallback. See [behavior and validation](docs/FRAME_SYNC.md).

- Bounded per-thread biome-offset reuse for nearby client and integrated-server queries, preserving native biome choices and source calls, with a restart-only GUI switch and a Lithium ownership guard. See [scope, measurements and correctness checks](docs/WORLD_OPTIMIZATIONS.md).

- An original OpenGL post-processing shader pipeline with ordered composite/final passes, native Shaders GUI, Modrinth discovery/install, ZIP drag/drop, verified downloads, persistent selection and failed-pack recovery. Conditional and guarded literal includes use the native GLSL preprocessor, with bounded expansion and source diagnostics. Up to sixteen color buffers and eight simultaneous outputs support explicit target routing and indexed graphics-state restoration. Twelve normalized/floating-point formats, per-pass color mipmaps, literal clear settings and retained auxiliary history support feedback across passes and frames, with reset on resizing or world changes. Pack-local PNG inputs support named samplers, composite/final color overrides, noise textures and literal filtering/wrapping metadata, with bounded worker decoding, shared-image memory accounting and per-pass sampler units. Unsupported terrain/shadow stages, separate opaque-depth captures and integer formats remain explicitly rejected. See [shader support and limits](docs/SHADERS.md).

- Shader world-time/day, native moon phase and interpolated rain/thunder uniforms share one snapshot
  across a frame's passes, captured only for packs that request those inputs. See [shader inputs](docs/SHADERS.md).

- Constant-shift decoding for common packed block arrays, with preserved native values and storage
  formats. The restart-only Packed block decoding option yields to Lithium and keeps native handling
  for small/custom requests. See [scope and decoder measurements](docs/PACKED_STORAGE.md).

- Native `depthtex0`/`gdepthtex` shader inputs capture depth-writing world and transparent geometry
  before late debug clears, then overlay native first-person depth while preserving the world elsewhere.
  Frame-graph dependencies retain temporary targets until capture; disabled clouds are excluded.
  Only 26.2 requires reverse-Z conversion. The owned R32F image shares the shader memory budget,
  and fresh-frame checks prevent stale sampling. `MC_HAND_DEPTH` is 1.0 for the unchanged native
  hand projection; separate opaque-depth and hand projection-matrix inputs remain incomplete.

- Current, inverse and previous world projection matrices for post-processing shaders, captured from
  Minecraft's actual upload after view effects. Depth-range conversion follows the native backend;
  fresh-frame checks and resize/world resets prevent stale matrix history. Native hand projection
  remains separate. See [shader matrix inputs](docs/SHADERS.md#world-projection-inputs).

- Current/inverse/previous world view matrices and native camera positions for post-processing.
  Rebased positions preserve small movements far from spawn; integer/fractional inputs retain world
  coordinates. Large teleports reset temporal history for packs using these inputs. Native rendering
  keeps its original camera and matrices. See [camera inputs](docs/SHADERS.md#world-view-and-camera-inputs).

- Single-allocation noise interpolation slices on all nine targets: each zero-filled row is allocated once instead of creating and immediately discarding an identical row. Independent restart-only GUI/config control, Lithium ownership guard, native-method allocation checks and world-output verification preserve generation semantics.

- Bounded reuse of identical native End island heights across repeated terrain samples. The cache preserves exact float results, uses weak noise-source identities, leaves custom noise subclasses uncached and has an independent restart-only setting. See [world optimizations](docs/WORLD_OPTIMIZATIONS.md) for scope and validation.

- Deferred first GPU allocation for native section-layer buffers on 1.21.4, with stable worker-visible objects, render-thread materialization and native upload/release ownership. Other targets retain their existing upload-time allocation. The independent restart-only control and validation are described in [deferred chunk buffers](docs/SECTION_BUFFERS.md).

- Reusable traversal state for materialized voxel-shape joins on all nine targets, preserving independent native outputs, exact empty bounds, native coordinate/operation order and custom callback ownership. A separate restart-only shape construction setting yields to Lithium; see [shape construction](docs/SHAPE_CONSTRUCTION.md).

Not implemented:

- First-run relaunch window.
- Automatic shutdown after first-time installation.
- Launcher-profile support outside the official-launcher-style `versions/<id>/<id>.json` layout.
- Automatic profile restoration or uninstall UI; the original JSON backup is created but not consumed yet.
- Early-window adoption for Vulkan or other graphics backends, and a complete OS/driver, fullscreen, DPI and accessibility validation matrix.
- Persistent startup caches, transformed-class caches, asynchronous Mixin preparation or processed-resource caching.
- A complete chunk mesh compiler, mesh-storage/draw-command replacement, persistent-mapped or multi-draw GPU submission system, occlusion traversal replacement, or verified Sodium feature/performance parity.
- Full Lithium-style game-logic coverage or verified Lithium feature/performance parity.
- Reproducible end-to-end launch-time improvements; isolated repeated-operation microbenchmarks do not establish total startup gains.
- Physical AMD, Intel, NVIDIA, Apple, and software-driver compatibility/performance testing; the current upload scheduler is vendor-neutral by construction but has not been validated on that hardware matrix.
- Broader memory, server chunk-scheduling, world-generation, or adaptive frame-time scheduling optimizations.
- Mod Menu integration, speech-engine/controller validation and complete renderer-option parity.
- Full-world shader-pack support, including terrain/geometry replacement, shadows, separate opaque-depth stages, hand projection inputs, integer/other unsupported color formats, non-PNG/resource-pack textures and other shader properties/options. Popular full-world packs are not compatible with the limited color post-processing renderer yet.

The approved black-and-white lightning icon has a transparent background and is included in the Fabric mod metadata. Its original bolt shape is preserved.

## Direction

Kernel is being developed as an all-in-one Fabric optimization mod. Its initial renderer work optimizes translucent quad sorting and section-face connectivity, time-slices legacy chunk GPU uploads and reduces allocations in Minecraft's pose-stack, block-model tessellation, standalone model random selection, fluid-height calculation, baked-quad upload, immediate vertex-transform, entity/model-part rendering, and block-face visibility routines. These are only a few hot paths; Kernel does not yet match Sodium's renderer breadth or demonstrated performance. Compatibility, measurable frame-time improvements, and honest benchmarking take priority over feature claims. Kernel contains no Sodium or other third-party mod code, and Fabric Loader will reject installations that also contain Sodium or Iris. See [quad sorting behavior and measurement](docs/TRANSLUCENT_SORTING.md) for its scope and validation.

On a recognized Fabric profile, the mod bundles and installs the Knot Client, changes that profile's launcher `mainClass`, adds its profile-local Java agent argument, and leaves a `.kernel-backup` copy of the original JSON. On the following launch, the agent enables its audited startup-cache hooks and the Knot Client delegates to Fabric's original Knot entry point. Unsupported launchers are left untouched and Minecraft continues normally. No game or loader JAR is patched on disk, and no third-party libraries are bundled into the Knot Client; its optional ASM dependency is supplied by Fabric's launcher classpath.

The game-profile JVM option `-Dkernel.startupCache=false` disables startup caching. Cache contents live only in the current process and are released when initial loading completes, with a three-minute fallback expiry. See [startup cache behavior and measurement](docs/STARTUP_CACHES.md) for limits, recovery and benchmark commands.

The requested sequence and pinned comparison versions are tracked in [the parity plan](docs/PARITY_PLAN.md). The subsequent loading-window request moved that work forward; same-window OpenGL startup is now implemented. Both full renderer and game-logic parity remain incomplete. Frame Sync, biome-offset reuse, noise-slice allocation reduction and End island height reuse are implemented; limited color post-processing and shader installation are implemented, while full-world shader compatibility and broader world-generation work remain outstanding.

Contributor and coding-agent rules are documented in [AGENTS.md](AGENTS.md).
