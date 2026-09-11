# Shape coordinates

**Shape coordinates** in Optimizations controls `shape_coordinates` in `config/kernel-world.properties`.
It defaults to enabled on every supported target, takes effect at the next launch and yields to
Lithium. The setting is independent of shape-query and shape-construction optimizations.

Native cube shapes construct an immutable `CubePointRange` whenever a caller asks for axis coordinates.
Kernel shares the native lists for 1–64 subdivisions in a fixed table of 64 objects and 65 references.
No shape, world, block, mutable coordinate array or dynamically sized lookup table is retained.
Coordinate lists can consequently share object identity across axes and different cube shapes.
Values and the native class stay unchanged; iterators and sublists keep their independent traversal
state. These lists already reject mutation through the collection API. Reflective/Mixin mutation of
the supposedly immutable native class is outside this optimization's ownership contract.

The native getter still queries the current shape dimension on every call, including custom
`DiscreteVoxelShape` behavior and errors. The constructor intercept only substitutes the resulting
list for common positive dimensions. Larger dimensions create an ordinary new native list; invalid
dimensions retain constructor exceptions. Even native out-of-range `getDouble` behavior is retained:
that method divides the supplied integer index by the subdivision count without checking bounds.
Direct construction and other voxel-shape coordinate implementations are unaffected.

The optional redirect uses priority 900 and applies after standard constructor redirects. An ordinary
priority-1000 owner can displace it; injection-count debugging permits that skip. A synthetic competing
owner is checked separately. This does not establish compatibility with arbitrary mod injection orders
or with mods that make the native coordinate class mutable. Disable the setting for such replacements.

## Verification and measurement

The native differential harness reads each installed target JAR's original cube getter into an isolated
test subclass. No Minecraft bytecode or source is included in Kernel artifacts. It checks exact double
bits, list values/hash/arrays, cache bounds and identity, independently created shapes, iterators,
sublist and mutation behavior, changing/custom dimension queries, invalid/overflow dimensions,
exceptions and concurrent callers. Actual getter allocation is checked with the setting enabled,
disabled and displaced by a competing owner. Existing generated-world and shape-geometry checks remain
part of the validation.

```powershell
.\gradlew.bat :mod:1.21.4:worldOptimizationEnabledSmoke :mod:1.21.4:worldOptimizationDisabledSmoke -PkernelShapeCoordinatesBenchmark=true
.\gradlew.bat :mod:26.2:worldOptimizationEnabledSmoke :mod:26.2:worldOptimizationDisabledSmoke -PkernelShapeCoordinatesBenchmark=true
.\gradlew.bat :mod:1.21.4:shapeCoordinatesOwnershipSmoke :mod:26.2:shapeCoordinatesOwnershipSmoke
.\gradlew.bat buildAll
```

The optional native benchmark alternates live and original getters across twelve warmup rounds and
fifteen measured rounds of 65,536 calls. It exercises dimensions inside and outside the bounded table
and distinguishes returned objects that escape from immediate scalar consumption. Separate disabled
processes control for reference-class/JIT differences. Allocation and isolated query cost do not
establish whole-startup, gameplay or Sodium/Lithium performance parity.

Exploratory constructor-only measurements on the available Ryzen 5 5600G saved 16 bytes per escaped
coordinate list. When Java eliminated native allocation during immediate scalar consumption, both
paths allocated zero and measured about 1.03–1.05 ns/query on Java 21 and 25. The actual transformed
getter has additional native dispatch and dimension-query work.

Both endpoint native process tests passed with the feature enabled, disabled and displaced by a
standard-priority constructor owner. Actual getters for dimensions 1, 16 and 64 used 16 bytes per
escaping result natively and zero with Kernel; dimension 65 retained 16 bytes in both paths.
The immutable table is warmed before these per-call measurements.

On Java 21.0.12, mixed dimensions 1–64 measured 3.52/4.06 ns/query (reference/live) for escaping
results and 3.22/4.07 for immediate scalar consumption. On Java 25.0.1 the corresponding pairs were
3.63/3.29 and 2.46/2.83. Java 25 eliminated the native scalar allocation; Java 21 retained it in this
native dispatch fixture. Larger uncached dimensions retained allocation and added roughly 0.1–0.3 ns
in the tested workloads. Separate disabled reference/live timings varied, including a 6.49/6.27 ns
escaping pair on Java 25. This is a demonstrated allocation reduction with a small CPU tradeoff in
some cases, not a universal query-speed improvement.

Six isolated 26.2 JFR launches alternated disabled/enabled/enabled/disabled/disabled/enabled,
holding the other Kernel options fixed. Time to the ready callback was 9,997 / 9,769 / 9,763 /
9,810 / 14,406 / 9,969 ms. The 14,406 ms run included 4,297 ms parked in Minecraft's account-property
lookup; it remains part of the recorded results. The other enabled and disabled ranges overlap.
These trials do not establish an end-to-end startup improvement. All six adopted the bootstrap's
native window and completed the GUI checks.

Final validation ran `buildAll`, all nine `runBootstrapSmoke` tasks, both endpoint
`worldGenerationComparison` tasks and both endpoint `shapeCoordinatesOwnershipSmoke` tasks.
The matrix passed 1,358 JUnit tests in 394 suites with no failures, errors or skipped tests;
all 29 coordinate checks (enabled, disabled, Lithium conflict and competing constructor owner)
passed. Both nine-chunk Overworld block/biome fingerprints matched, and all nine native window
adoptions completed. The nine mod JARs and single Knot Client JAR passed metadata, Java bytecode,
access-widener, bundled-bootstrap hash, manifest and production-only content inspection.
These results cover the available Windows/AMD machine, not a physical driver/platform matrix.
