# Shape query traversal

Kernel reuses the three nested callbacks used by `Shapes.joinIsNotEmpty` after Minecraft has merged the
two shapes' coordinates. This removes temporary callback creation for each visited X/Y row. The public
method's empty-shape checks, bounds comparisons, BooleanOp validation and coordinate mergers remain
native. The traversal still visits X, then Y, then Z; evaluates the first occupancy before the second;
calls the supplied operation once per visited cell; and stops at exactly the same result.

Each thread retains at most four small cursors, each with three callback objects. Deeper nesting receives
temporary cursors. A `finally` block releases each cursor and clears every shape, merger and operation
reference, including after exceptions. Nothing caches occupancy or holds worlds between calls. Mutating
the input shape remains visible to the next query.

Only exact native merger classes use the reusable callbacks. Custom mergers and subclasses run the
original method so callbacks they retain keep their independent inputs. Other mods that replace the
private traversal require compatibility testing or disabling this setting. Kernel yields this optimization
to Lithium when it is installed.

## Controls and version adapter

**Kernel → Optimizations → Shape query allocation** controls `shape_traversal=true` in
`config/kernel-world.properties`. The default is on. Changes require restarting Minecraft; unreadable
settings disable the world optimization group. Apply/Done/Cancel and unknown property preservation follow
the other world settings.

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

`buildAll` includes real enabled/disabled/Lithium-marker processes for every target. The checks exercise
the actual Mixin and verify custom mergers retain their original callback ownership. An allocation probe
uses a 16×16×16 query that cannot exit early. These isolated operations do not establish faster complete
launches, world loading or gameplay frame times. Startup recordings are diagnostic samples, not a claim of
Sodium or Lithium parity.

The full matrix passed 947 unit tests and all 27 shape-ownership processes. All nine release artifacts
passed access-widener validation, including the remapped legacy namespace, and contained only production
classes plus the matching bundled Knot Client. The two endpoint GUI launches verified persistence,
Apply/Done/Cancel and unchanged active settings until restart.

The isolated `shapeJoinBenchmark` warms both paths, alternates their order and reports the median of seven
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

Four 26.2 development launches with Java Flight Recorder enabled, in off/on/on/off order, reached the
native game-load callback at 10,091 / 9,863 / 10,006 / 9,953 ms of JVM uptime. All four adopted the original
bootstrap window and passed settings interaction. The small difference and overlapping observations do
not establish a reproducible total-startup improvement. JFR identified shape initialization and class
loading as substantial startup work; its sampled allocation weights are estimates, not exact byte counts.
