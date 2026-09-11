# Renderer, game logic, and startup milestones

The requested order is full renderer feature parity, then full Lithium-style game-logic parity, then the
Kernel loading window with a same-window transition into the game. The subsequent request also authorizes
launch-time optimizations while the renderer work proceeds. The later request to repair pre-Fabric startup
moved the loading window forward. Neither parity milestone is complete.

Implementations must be original. No upstream mod source, binary, or asset is included. The renderer and
game logic stay inside the Fabric mod; launcher-only work stays in the Knot Client. All nine existing game
targets remain required, with Java 21 for 1.21.x and the launcher, and Java 25 for 26.x.

## Comparison versions

These are reference releases for the acceptance work, not Kernel dependencies. They were selected from
the projects' published Fabric version metadata on 2026-09-10. Pinning them prevents the acceptance target
from silently moving with upstream updates. No reference JARs were downloaded into this repository.

| Minecraft | Sodium reference | Lithium reference |
| --- | --- | --- |
| 1.21.4 | [0.6.13](https://modrinth.com/mod/sodium/version/c3YkZvne) | [0.15.3](https://modrinth.com/mod/lithium/version/u8pHPXJl) |
| 1.21.5 | [0.6.13, beta](https://modrinth.com/mod/sodium/version/DA250htH) | [0.16.3](https://modrinth.com/mod/lithium/version/xcELvp6R) |
| 1.21.6 | [0.7.3, tagged for 1.21.8 and listed for 1.21.6](https://modrinth.com/mod/sodium/version/7pwil2dy) | [0.17.0](https://modrinth.com/mod/lithium/version/XWGBHYcB) |
| 1.21.8 | [0.7.3](https://modrinth.com/mod/sodium/version/7pwil2dy) | [0.18.1](https://modrinth.com/mod/lithium/version/qxIL7Kb8) |
| 1.21.9 | [0.7.3, tagged for 1.21.10 and listed for 1.21.9](https://modrinth.com/mod/sodium/version/sFfidWgd) | [0.19.2](https://modrinth.com/mod/lithium/version/L1sSIxFm) |
| 1.21.10 | [0.7.3](https://modrinth.com/mod/sodium/version/sFfidWgd) | [0.20.1](https://modrinth.com/mod/lithium/version/NsswKiwi) |
| 1.21.11 | [0.8.14](https://modrinth.com/mod/sodium/version/rkdTcxoT) | [0.21.4](https://modrinth.com/mod/lithium/version/Ow7wA0kG) |
| 26.1.2 | [0.9.1](https://modrinth.com/mod/sodium/version/vf7UgZpC) | [0.24.7](https://modrinth.com/mod/lithium/version/Oqq8TOAV) |
| 26.2 | [0.9.1](https://modrinth.com/mod/sodium/version/2Yom1N68) | [0.25.3](https://modrinth.com/mod/lithium/version/f7vZ0VWU) |

No stable Sodium release is listed for 1.21.5 in that metadata, so its newest listed beta is the reference;
this does not remove or reclassify Kernel's 1.21.5 target. The comparison inventory must be refined against
each pinned release's actual behavior before a family can be marked complete. The families below are a
work breakdown, not an assertion that all upstream options have already been audited.

## Renderer acceptance

| Capability | Current implementation | Remaining acceptance work |
| --- | --- | --- |
| Section mesh compilation | Vanilla compiler with Kernel hot-path optimizations | Own compiler, worker snapshots, block/fluid geometry, AO, lighting, tint, model hooks, resource-pack reloads |
| Build and upload scheduling | Indexed pending-task priorities and cancellation on all targets; adaptive FIFO upload budget on 1.21.x; native 26.x staging | Broader obsolete-result policy, bounded backlogs, reload gameplay validation, measured stutter reduction and worker scheduling |
| Mesh storage | Native storage with deferred first allocation for 1.21.4 section layers; newer allocation paths unchanged | Region allocation, compact vertices with demonstrated precision, index storage, lifetime accounting and reclamation |
| Draw submission | Vanilla graphics-device paths; shared unchanged camera snapshots within native chunk batches on 1.21.11/26.x | Region batching, solid/cutout/translucent passes, capabilities and fallback paths for every target's graphics backend |
| Visibility | Kernel scanline section-connectivity solver and boolean native frustum queries; vanilla traversal | Occlusion traversal, incremental invalidation, camera transitions, caves, boundary cases and broader visibility integration |
| Translucency | Kernel stable radix index sorting with vanilla centroids, resort triggers and index uploads | Geometry-aware sorting for intersecting surfaces, rebuild/resort policy, visual reference tests and pinned-release behavior coverage |
| Entities and block entities | Allocation-reduced model emission | Visibility decisions, oversized bounds, off-screen render contracts, special effects and mod compatibility |
| Particles and animated textures | Vanilla paths | Visibility-aware work scheduling without stale animations or altered particle simulation |
| Configuration | Persistent per-feature switches, immutable startup snapshots, themed native video settings, one-time hardware recommendations and actual screen checks on all nine targets | Mod Menu integration, speech-engine/controller checks, broader renderer options and pinned-release configuration coverage |
| Mod and pack compatibility | Sodium and Iris conflicts explicitly declared; limited original color post-processing with native world/weather inputs | Fabric rendering integration, full-world shader stages, custom model/quad formats, pack reloads, mod hooks and a maintained test matrix |
| Performance and platforms | Local isolated benchmarks only | Reproducible gameplay traces, render correctness captures, frame-time distributions, memory, upload stalls and physical GPU/OS testing |

The connectivity solver replaces one part of occlusion preparation. It does not replace the visibility
graph traversal, chunk compiler, storage allocator or draw system. Allocation reductions alone do not
close any of those broader parity requirements.

The [quad sorter](TRANSLUCENT_SORTING.md) preserves vanilla's exact distance-key order while reducing sort
cost and temporary allocations. It does not correct centroid-order artifacts for intersecting surfaces
or replace the translucency scheduling and rendering pipeline. Differential tests and factory smoke tests
are required on all nine targets; GPU render captures and full translucency parity remain outstanding.

## Game-logic acceptance

Lithium's [published configuration inventory](https://github.com/CaffeineMC/lithium/blob/develop/lithium-fabric-mixin-config.md)
is a discovery aid; acceptance must use the pinned releases above, including their defaults, dependencies
and documented exceptions. The families below remain incomplete in Kernel:

- Collision queries and movement: [matching/mapped-grid bitset intersections and reusable shape-overlap callbacks](SHAPE_QUERIES.md) are implemented;
  [materialized shape joins](SHAPE_CONSTRUCTION.md) also reuse their temporary traversal state.
  [Cube-shape coordinates](SHAPE_COORDINATES.md) share bounded native immutable lists.
  Broader voxel-shape algorithms, entity lookup, supporting blocks and movement work remain incomplete.
- Entity lifecycle and ticking: tracking, equipment changes, passenger traversal and client-only work avoidance.
- AI and navigation: task scheduling, sensors, path searches, points of interest and invalidation.
- Inventories and block entities: hopper transfer/lookup, change notification, sleeping and wake-up rules.
- Chunk data: [constant-shift native packed-array decoding](PACKED_STORAGE.md) is implemented;
  broader palette, block access, serialization, ticket and entity-collection work remains incomplete.
- Block and fluid work: neighbor updates, redstone, fluid flow, moving block shapes and scheduled/random ticks.
- World and generation work: [biome-offset reuse, noise-slice allocation reduction and End island height reuse](WORLD_OPTIMIZATIONS.md) are implemented; broader allocation reductions,
  caches, random-number sequencing and deterministic-output validation remain incomplete.
- Configuration and compatibility: selective disabling, dependency-aware activation, conflict detection and mod hooks.

Kernel's client-only scope includes its integrated server. Dedicated-server distribution is not currently
supported. Each algorithm needs differential tests against vanilla for state, event order and random
consumption, plus workload measurements and targeted mod compatibility checks.

## Launch-time work now implemented

- Fabric-side [compact resource readers](RESOURCE_READERS.md) retain native UTF-8 streams and JDK
  reader operations while reducing each initial character buffer from 8,192 to 2,048 characters.
  The independent GUI/config switch requires a restart; resource contents are not cached.
- An optional Java agent in the existing Knot Client JAR, installed through the recognized profile's JVM
  arguments alongside the existing main-class handoff. The existing one-time backup remains the recovery path.
- Raw JAR class-entry reuse during the current launch. Fabric still resolves URLs and applies classloader
  isolation before the cache is consulted; mutable directory resources and missing entries are not cached.
- Mixin target-reader reuse keyed by exact input bytes. Fabric's bytecode provider and pre-Mixin transforms
  still execute on every request. ASM constant-pool indexing and decoded strings are reused; mutable
  ClassNode trees are freshly parsed for every caller, respecting reader flags.
- The raw-read adapter uses bounded size hints for eligible local JAR entries, avoiding intermediate
  buffers up to 1 MiB. Unknown/large hints retain `InputStream.readAllBytes()`; EOF determines actual length.
- Byte/entry bounds, eviction, cache-hit diagnostics and release at initial game-load completion, with a
  three-minute fallback expiry for launches that never reach the callback.
- Exact class-byte fingerprints for the audited Fabric 0.19.3 hooks. Different loader bytecode and earlier
  agents' edits bypass the hooks. Missing optional ASM dependencies leave normal argument forwarding intact.

These features do not persist transformed classes, parallelize Mixin application, skip resource reload
listeners or claim that a modpack loads instantly. End-to-end launch improvements still need measurement.

## Loading-window milestone

Same-window OpenGL startup is now implemented in response to the later loading-window request. The
[bootstrap guide](BOOTSTRAP_WINDOW.md) records ownership, real activity/progress, installation, recovery
and remaining platform limits. Real launch probes pass across all nine game targets on the available
Windows/AMD machine. No full-platform or instantaneous-initialization claim is made.

Acceptance requires a promptly displayed pre-Fabric Kernel window; honest phase/activity reporting with
indeterminate progress when total work is unknown; normal handling of errors and user cancellation; and
adoption of the same native window and graphics context by Minecraft. Initial vanilla loading visuals
can be replaced only while preserving the actual resource preparation and completion lifecycle. Test
full-screen transitions, DPI, resize, focus, accessibility, context ownership and fallback recovery on
all supported game targets and the intended OS/driver matrix. Showing a window promptly is distinct
from completing Minecraft initialization instantly.

## Additional requested work

- [Frame Sync](FRAME_SYNC.md) is implemented: monitor-refresh cap and synchronized presentation,
  with actual paced FPS and a clearly labeled uncapped estimate in the HUD or F3.
- [Original post-processing](SHADERS.md) with sixteen logical color buffers, twelve normalized/floating-point formats, per-pass mipmaps, retained auxiliary history, pack-local PNG inputs and eight simultaneous outputs, Modrinth discovery/installation, ZIP drag/drop and Iris
  incompatibility are implemented. Native combined world/first-person depth, world projection/view matrices, camera positions and world/weather uniforms
  are available to post-processing passes. Full-world shader rendering still requires terrain/shadow,
  separate opaque-depth stages and hand projection inputs,
  integer/other unsupported formats, broader textures and shader-properties support; a downloader or partial pipeline is not full compatibility.
- Further deterministic singleplayer world-generation/world-loading improvements and reduced chunk
  loading stutters. Preserve vanilla world output, random-number consumption and mod lifecycle behavior.

Shader support and further world-generation work remain incomplete. No upstream shader engine, optimization mod or reference binary is bundled.
