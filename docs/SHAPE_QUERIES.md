# Shape queries

Kernel preserves `Shapes.joinIsNotEmpty`'s public empty-shape checks, bounds comparisons, BooleanOp
validation and coordinate mergers. After merging, native AND queries on identical voxel grids can use
the two native BitSets directly. A first-cell intersection returns immediately; otherwise the JDK's word
intersection checks the remaining occupancy. The query borrows current storage without retaining,
copying or mutating it. Changes to a shape remain visible to the next query.

This path requires exact native BitSet shapes with equal dimensions, native identity mappings on all
three axes, and exact native BitSet storage within the grid bounds. Coordinate lists must be exact
`DoubleArrayList` or `CubePointRange` instances; custom list methods are not called by the fast path.
Cube mergers must map each index directly into both shapes.

For mismatched grids, a second AND path reads the native coordinate mappings once per axis. It accepts
exact native identical, cube and indirect mergers with at most 128 merged cells per axis. Indirect
mergers already own primitive mappings; their allocating coordinate-list wrapper is never requested.
The input coordinates and native epsilon rules are unchanged. Mappings outside either shape are omitted
because native `isFullWide` returns false there. Precomputed X/Y offsets and Z indices address the current
BitSets directly, avoiding repeated division and nested callbacks. The first mapped cell is tested before
preparing the full axes, preserving a cheap early exit. Identical/cube mappings start at zero; indirect
mappings supply their actual first signed index pair through a read-only native contract. Out-of-range
first pairs cannot intersect.

This path retains up to four reentrant scratch sets per thread, each with six 128-entry integer arrays
(3 KiB array payload). Deeper nesting uses temporary sets. Scratch holds no shapes, mergers, BitSets or
world references, and `finally` releases its nesting slot. Input occupancy is never copied or cached.
Overflow-sized grids, out-of-grid bits, custom storage/shapes, other operations, custom/non-overlapping
mappings and axes above the cap use the existing traversal. The matching-grid path does not have this
axis cap because it needs no coordinate scratch.

That traversal reuses its three nested callbacks, removing temporary callback creation for each visited
X/Y row. It still visits X, then Y, then Z; evaluates the first occupancy before the second; calls the
supplied operation once per visited cell; and stops at exactly the same result. A method wrapper avoids
allocating a cancellation callback for each query. Custom mergers call the original method through the
wrapper so their retained callbacks keep their original ownership.

Each thread retains at most four small cursors, each with three callback objects. Deeper nesting receives
temporary cursors. A `finally` block releases each cursor and clears every shape, merger and operation
reference, including after exceptions. Nothing caches occupancy or holds worlds between calls. Mutating
the input shape remains visible to the next query.

Only exact native merger classes use the reusable callbacks. Custom mergers and subclasses run the
original method so callbacks they retain keep their independent inputs. Other mods that replace the
private traversal require compatibility testing or disabling this setting. Kernel yields this optimization
to Lithium when it is installed.

## Controls and version adapter

**Kernel → Optimizations → Shape queries** controls `shape_traversal=true` in
`config/kernel-world.properties`. The default is on. Changes require restarting Minecraft; unreadable
settings disable the world optimization group. Apply/Done/Cancel and unknown property preservation follow
the other world settings.

Three configuration-gated Mixins expose read-only use of native occupancy, cube-coordinate divisors and
the first indirect index pair through ordinary Kernel interfaces. These contracts remain loadable with the feature disabled; disabling
the setting or detecting Lithium disables all three injected contracts and the query wrapper.

The algorithm is shared across all nine supported targets. Older versions make `IndexMerger` package
private. A single class-only Fabric access widener exposes that interface, without changing any fields,
methods, final modifiers or class inheritance. Loom remaps the legacy `named` file to `intermediary` in
release JARs; 26.x uses `official`. Each artifact includes only its matching file. This follows
[Fabric's access-widening mechanism](https://docs.fabricmc.net/develop/class-tweakers/access-widening).

## Validation and measurement

Tests extract the target Minecraft JAR's original traversal and its three lambda bodies into a temporary
test class in memory. This preserves an independent native reference even under Fabric's transforming
classloader; no reference code or test classes ship in Kernel. Tests compare all sixteen BooleanOp truth
tables, native discrete/identical/indirect/non-overlapping mergers, close coordinate boundaries, infinities,
sparse occupancy, callback ordering, early exit, mutation, deep reentrancy, exceptions and concurrent use.

Matching-grid checks additionally compare the actual transformed method against that reference for
discrete/identical mergers, mixed native coordinate-list types, rectangular grids, sparse occupancy and
mutation. They verify disabled/conflicting activation, nonidentity cube mappings, observable custom
coordinate lists and operations, zero-sized grids, out-of-grid storage and overflowing dimensions.
Four concurrent workers use independent shapes; the fast path retains no thread-local or global occupancy.

Mapped-grid checks compare random anisotropic grids, native cube/indirect mappings, epsilon-adjacent
coordinates and infinities before and after adding occupancy, and again after clearing it. They exercise
the 128/129-cell boundary, shifted indirect first cells, indices outside input dimensions, custom shape
and storage contracts, custom coordinate access, overflow guards and four independent concurrent workers.

`buildAll` includes real enabled/disabled/Lithium-marker processes for every target. The checks exercise
the actual Mixin and verify custom mergers retain their original callback ownership. An allocation probe
uses a 16×16×16 query that cannot exit early. These isolated operations do not establish faster complete
launches, world loading or gameplay frame times. Startup recordings are diagnostic samples, not a claim of
Sodium or Lithium parity.

The matching-grid update passed `buildAll`, 1,223 unit tests in 331 suites and all 27 shape-ownership
processes, each including native grid differential checks. All nine mod artifacts passed class and
access-widener validation, including the remapped legacy namespace, and contained only production code
plus the matching bundled Knot Client. Endpoint GUI launches verified original-window adoption,
persistence, Apply/Done/Cancel and unchanged active settings until restart. Both endpoints also passed
the shader import/render/disable gameplay probe with the shape optimization active.

The subsequent bounded mapped-grid update passed the same unit/process matrix and endpoint launch and
shader probes, including enabled/disabled/Lithium-marker activation of the additional indirect-mapping
contract. Release checks verified all ten correctly named artifacts, Java levels, metadata, access
wideners and bundled Knot Client identity. The world-generation comparison profiles now explicitly switch
`shape_traversal` with the other three world settings. Fresh baseline/optimized worlds on 1.21.4 and 26.2
produced identical block and biome fingerprints for nine remote Overworld chunks per run. Their combined
generation/fingerprinting timings moved in opposite directions between targets; these single samples
do not establish a total world-generation speedup or isolate this optimization's contribution.

The callback-only `shapeJoinBenchmark` warms both paths, alternates their order and reports the median of seven
8,192-query batches on the available Windows/Ryzen 5 5600G machine. The larger fixtures have opposing
checkerboard occupancy so a full traversal is required; the one-cell fixture exits immediately.

| Target / runtime | Grid | Native ns/query | Kernel ns/query | Native bytes/query | Kernel bytes/query |
|---|---|---:|---:|---:|---:|
| 1.21.4 / Java 21.0.12 | 1³ | 59.31 | 58.51 | 112 | 0 |
| 1.21.4 / Java 21.0.12 | 8³ | 4,207.30 | 3,528.89 | 2,912 | 0 |
| 1.21.4 / Java 21.0.12 | 16³ | 30,957.69 | 25,809.27 | 10,912 | 0 |
| 26.2 / Java 25.0.1 | 1³ | 28.78 | 53.75 | 112 | 0 |
| 26.2 / Java 25.0.1 | 8³ | 4,637.63 | 3,499.18 | 2,912 | 0 |
| 26.2 / Java 25.0.1 | 16³ | 28,588.46 | 26,572.78 | 10,912 | 0 |

This benchmark includes the thread-local cursor lookup but excludes coordinate merging and the Mixin
adapter. Allocation accounting reports no callback allocation after warm-up for these fixtures; native
non-overlapping mergers can still allocate their own internal callbacks. The one-cell Java 25 case
regresses, showing that eliminating allocation does not guarantee lower latency for every query.

```powershell
.\gradlew.bat :mod:1.21.4:shapeJoinBenchmark :mod:26.2:shapeJoinBenchmark --no-parallel --max-workers=1
```

The grid benchmark compares the original target-JAR method with the actual transformed method,
including Kernel's ownership checks and wrapper. It rotates through 128 separate shape/merger pairs,
warms each workload for at least 750 ms, alternates sample order and reports the median of seven
4,096-query batches. This avoids measuring one invariant pair. Coordinate merger construction is outside
the timed region. `first` and `last` intersect only at the corresponding corner; `disjoint` uses opposing
occupancy. `unaligned` gives one shape half as many X cells. The following measurements preceded the
mapped-grid path and exercised the callback fallback in that case:

| Target / runtime | Grid / case | Native ns/query | Kernel ns/query | Native B/query | Kernel B/query |
|---|---|---:|---:|---:|---:|
| 1.21.4 / Java 21.0.12 | 8³ disjoint | 3,856.62 | 20.02 | 2,880 | 0 |
| 1.21.4 / Java 21.0.12 | 16³ disjoint | 31,563.38 | 62.40 | 10,880 | 0 |
| 1.21.4 / Java 21.0.12 | 16³ first | 24.39 | 12.50 | 80 | 0 |
| 1.21.4 / Java 21.0.12 | 16³ unaligned | 31,668.63 | 38,935.28 | 10,880 | 0 |
| 26.2 / Java 25.0.1 | 8³ disjoint | 4,303.44 | 20.80 | 2,880 | 0 |
| 26.2 / Java 25.0.1 | 16³ disjoint | 32,079.47 | 49.78 | 10,880 | 0 |
| 26.2 / Java 25.0.1 | 16³ first | 23.34 | 14.14 | 80 | 0 |
| 26.2 / Java 25.0.1 | 16³ unaligned | 31,974.02 | 41,613.94 | 10,880 | 0 |

The bounded mapped-grid path addresses that slower fallback. Its updated benchmark also checks
`unaligned-first` (different X dimensions with both first cells occupied), `indirect` (a native indirect
X mapping with opposing occupancy), and `indirect-first` (the latter mapping with first-cell overlap).
Live endpoint measurements including the first-pair check, with the same warm-up/sampling method:

| Target / runtime | Grid / case | Native ns/query | Kernel ns/query | Native B/query | Kernel B/query |
|---|---|---:|---:|---:|---:|
| 1.21.4 / Java 21.0.12 | 8³ unaligned | 4,619.31 | 843.36 | 2,912 | 0 |
| 1.21.4 / Java 21.0.12 | 16³ unaligned | 35,202.98 | 4,943.53 | 10,912 | 0 |
| 1.21.4 / Java 21.0.12 | 16³ unaligned-first | 28.93 | 34.52 | 112 | 0 |
| 1.21.4 / Java 21.0.12 | 16³ indirect | 34,568.36 | 4,961.33 | 10,912 | 0 |
| 1.21.4 / Java 21.0.12 | 16³ indirect-first | 30.57 | 37.65 | 112 | 0 |
| 26.2 / Java 25.0.1 | 8³ unaligned | 4,569.51 | 982.40 | 2,912 | 0 |
| 26.2 / Java 25.0.1 | 16³ unaligned | 33,952.42 | 6,323.12 | 10,912 | 0 |
| 26.2 / Java 25.0.1 | 16³ unaligned-first | 30.20 | 29.83 | 112 | 0 |
| 26.2 / Java 25.0.1 | 16³ indirect | 35,334.55 | 6,325.02 | 10,912 | 0 |
| 26.2 / Java 25.0.1 | 16³ indirect-first | 31.08 | 33.13 | 112 | 0 |

Full mismatched-grid scans improve in these fixtures. Some immediate-hit cases still cost more than
native, such as Java 21's 16³ unaligned-first and indirect-first cases, because guarded dispatch has overhead. The results
do not establish a speedup for every query. They are from the available Windows/Ryzen machine and are
not total world-loading, startup or gameplay performance evidence. The benchmark is opt-in and does not
run during ordinary `buildAll`:

```powershell
.\gradlew.bat :mod:1.21.4:worldOptimizationEnabledSmoke :mod:26.2:worldOptimizationEnabledSmoke -PkernelShapeGridBenchmark=true --no-parallel --max-workers=1
```

Four 26.2 development launches with Java Flight Recorder enabled, in off/on/on/off order, reached the
native game-load callback at 10,091 / 9,863 / 10,006 / 9,953 ms of JVM uptime. All four adopted the original
bootstrap window and passed settings interaction. The small difference and overlapping observations do
not establish a reproducible total-startup improvement. JFR identified shape initialization and class
loading as substantial startup work; its sampled allocation weights are estimates, not exact byte counts.
