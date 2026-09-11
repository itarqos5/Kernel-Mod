# Voxel-shape construction

**Shape construction** is an independent, restart-only setting in Kernel's Optimizations tab.
`shape_construction=true` in `config/kernel-world.properties` enables it by default on all nine targets.
It yields to Lithium and is separate from the existing overlap-query optimizations.

Native materialized voxel joins allocate nested callbacks and temporary boolean arrays for each X/Y
row. Kernel reuses three callbacks, scalar traversal coordinates and scalar bounds in a per-thread
cursor. Each result still receives a new native shape and its own BitSet storage. Geometry is neither
cached nor shared with previous results, and input shapes are not mutated.

The coordinate mergers still drive iteration. Each visited cell reads the first shape, then the
second shape, then calls the supplied boolean operation. Row and slice bounds update in the same
order as native construction. The unusual native empty-join bounds are preserved: minimum coordinates
are `Integer.MAX_VALUE` and maximum coordinates are `Integer.MIN_VALUE + 1`.

Only exact native cube, identical, indirect and non-overlapping mergers use pooled callbacks. Other
mergers and subclasses call the original method so retained callbacks keep their independent state.
Custom shapes and operations retain normal invocation order and may recursively construct shapes.
Four nested cursors are retained per thread; deeper nesting uses temporary cursors. `finally` releases
the nesting slot and clears all shape, merger, operation and result-storage references, including on
failure. A competing replacement of the native join may require this setting to be disabled.

## Validation and measurement

The test harness reads the installed target JAR's original join bytecode, its three callbacks and the
minimal native result methods into an isolated in-memory class. No Minecraft source or bytecode is
bundled in Kernel's artifacts. Differential checks compare complete cell occupancy, dimensions, all
six bounds and ordered box extraction, including sixteen boolean truth tables, mixed coordinate
grids, input changes, independent output ownership and empty axes. Further checks cover missing or
invalid arguments, custom callback retention and invocation order, eight-deep nesting, thrown
operations, reuse after failure and four concurrent threads.

The native join and its three callback bodies have matching instructions on all nine targets after
normalizing constant-pool indices. The current validation still runs each version's actual classes.
Enabled, disabled and Lithium-presence checks are part of `buildAll`; the GUI probe exercises every
world setting's Cancel, Apply, Done and restart-only activation.

```powershell
.\gradlew.bat :mod:1.21.4:worldOptimizationEnabledSmoke :mod:1.21.4:worldOptimizationDisabledSmoke -PkernelShapeConstructionBenchmark=true
.\gradlew.bat :mod:26.2:worldOptimizationEnabledSmoke :mod:26.2:worldOptimizationDisabledSmoke -PkernelShapeConstructionBenchmark=true
.\gradlew.bat buildAll
```

The optional isolated benchmark alternates the original bytecode and live native entry point across
four warmup rounds and nine measured rounds of 512 joins, including result allocation. Separate
disabled processes provide a control for the test class and wrapper. Tiny-fixture timings differ
between equivalent compiled methods, so those timings alone cannot establish a speedup.

On the available Ryzen 5 5600G with Java 21.0.12 and Java 25.0.1, measured allocation was identical on
both runtimes. Each 1-cell result used 352 bytes natively and 104 with Kernel; a 4×4×4 result used
1,912/104 bytes; a 16×16×16 result used 24,496/608 bytes. This includes the independently owned native
output. The disabled live entry point matched the original reference's allocation in every fixture.
For a mixed OR join at 16×16×16, the enabled original/live medians were 52.00/49.55 microseconds on
Java 21 and 51.86/44.63 on Java 25. These are warmed CPU operation costs, not world-load or FPS results.

For whole-startup investigation, the developer-only init script records JFR while running the existing
isolated bootstrap probe. It writes recordings and the game-load callback's JVM uptime to
`build/startup-profiles/`. It does not modify launcher profiles or install JVM arguments:

```powershell
.\gradlew.bat :mod:26.2:runBootstrapSmoke -I scripts/profile-startup.gradle -PkernelProfileVersion=26.2 -PkernelProfileConstruction=false
.\gradlew.bat :mod:26.2:runBootstrapSmoke -I scripts/profile-startup.gradle -PkernelProfileVersion=26.2 -PkernelProfileConstruction=true
```

Keep `kernelProfileCaches` and `kernelProfileShapes` fixed between comparisons; both default to true.
The script fixes the other world optimization flags to enabled in the isolated probe directory.
Alternate order across repeated runs, keep all failures, and distinguish warm filesystem measurements
from cold startup. Allocation sampling weights are estimates, not exact totals. Reproducible end-to-end
startup and gameplay improvements require evidence beyond the isolated operation tests.

Six 26.2/JFR launches on the available Windows/AMD machine, in off/on/on/off/off/on order, reached the
game-load callback at 10,298 / 9,938 / 9,945 / 10,081 / 9,794 / 9,885 ms of JVM uptime. Startup caches,
existing shape-query optimizations and the other world optimizations remained enabled. All six settings
and native-window adoption probes passed. The overlapping ranges do not establish a consistent
end-to-end startup improvement; the demonstrated result is lower temporary allocation in shape joins.

Final validation passed all nine bootstrap/settings/window-adoption probes, all 27 enabled/disabled/
Lithium-presence world checks and `buildAll` (1,331 JUnit tests in 385 suites, no failures, errors or
skips). Separate 1.21.4 and 26.2 generated-world runs produced matching block/biome fingerprints for
nine remote chunks with the world optimizations enabled and disabled. All ten release artifacts had
the expected names, Java levels, metadata and identical bundled Knot Client; no test or third-party
classes were packaged. These comparisons establish tested behavior, not general performance parity.
