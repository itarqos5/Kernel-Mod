# Translucent quad sorting

Kernel replaces the index-sorting step returned by `VertexSorting.byDistance(DistanceFunction)` on every
supported Minecraft target. The implementation is original and does not include third-party mod code.
Minecraft still computes quad centroids, decides when to resort, emits indices, uploads buffers and blends
the resulting geometry. Custom `VertexSorting` implementations supplied directly by other mods remain
in control of their sorting.

## Ordering and compatibility

Each distance callback runs once per input point, in input order. Kernel preserves the supplied function
rather than substituting its own distance calculation. Keys use the same descending `Float.compare` order
as vanilla: equal keys retain input order, NaNs compare equal to one another and precede infinity, and
positive zero precedes negative zero. The integer encoding canonicalizes NaNs before sorting, consistent
with [Java's float comparison contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Float.html#compare(float,float)).

The adapter accepts `Vector3f[]` through 1.21.8 and `CompactVectorArray` from 1.21.9 onward. The newer adapter
creates one scratch vector per sort, matching vanilla's callback-visible object lifetime. It does not
reuse that vector across calls because custom callbacks can retain it. Subclasses of `CompactVectorArray`
can override reads or size checks, so they retain the original sorter and its access sequence.

The Mixin wraps the original factory result at method return. Vanilla's camera-origin and orthographic
factories continue to select their own distance functions, including reference semantics for mutable
camera-origin vectors. The returned index array always belongs to its caller and is never pooled.

## Algorithm and memory

Empty inputs return an independent empty array. Batches below 512 quads keep the original sorter because
the radix setup cost is not justified by the small-batch measurements. From 512 quads onward, Kernel
evaluates keys and detects already ordered inputs, then uses stable least-significant-byte radix passes
for unordered inputs. Histograms are computed together and constant bytes are skipped. At most four
radix passes are needed.

Each thread retains a primary scratch object with two integer arrays, capped at 16,384 entries each,
plus 1,024 histogram integers. The maximum retained array payload is 132 KiB per participating thread,
excluding JVM array/object headers. Arrays grow on demand. Larger inputs remain supported using temporary
scratch without enlarging the retained arrays. Nested callback-driven sorts receive independent scratch
that is not retained, and exceptions release the outer lease in a `finally` block. No vectors, meshes,
camera objects or distance callbacks are retained in thread-local storage.

## Verification

`buildAll` runs differential JUnit tests against each target's actual unmodified vanilla sorter. Cases
include random camera positions, orthographic keys, ties, infinities, both zeros, noncanonical NaNs,
input sizes across algorithm/capacity boundaries, mutable callbacks, nested and concurrent sorting,
failure recovery, output ownership and large-mesh retention limits.

Each target also runs `vertexSortingSmoke` in a fresh JVM. It initializes real Fabric Loader and Mixin,
loads the transformed sorting factory and checks the built-in constants, factory overloads, custom
distance callbacks and stable output. It does not invoke Minecraft's main method, open a window, load
a world or install a launcher profile. Its temporary game directory is inside that target's build directory.

Run the isolated CPU/allocation benchmark without concurrent build work:

```powershell
.\gradlew.bat :mod:1.21.4:vertexSortingBenchmark :mod:26.2:vertexSortingBenchmark --no-parallel --max-workers=1
```

The benchmark checks vanilla/Kernel output equivalence before timing eight prepared inputs per scenario.
It warms both paths for 2,048 operations and reports the median of seven 512-operation samples, alternating
the comparison order. Batches through 512 quads use 20,000 warm-up operations and 20,000 operations per
sample so short operations have enough repetitions to reach steady compilation. Distance evaluation,
adapter overhead and returned index allocation are included;
fixture construction, centroid decoding, index-buffer emission and GPU work are excluded. The heap is
fixed at 512 MiB. These measurements cannot establish gameplay FPS, frame-time lows or total launch gains.

Kernel still uses vanilla's centroid-distance ordering. Faster sorting does not solve intersecting-quad
artifacts or establish Sodium translucency parity. Visual reference tests, mod/pack integration runs,
gameplay profiling and physical GPU/OS validation remain required.

## Local observations, 2026-09-10

The final benchmark ran on Windows 10 Pro N (10.0.19045), AMD Ryzen 5 5600G, with Temurin 21.0.12+8
for 1.21.4 and Temurin 25.0.1+8 for 26.2. The command above used `--offline` with the already cached
dependencies. These are warmed, single-thread operation timings in nanoseconds, including the adapter:

| Input | Quads | 1.21.4 vanilla | 1.21.4 Kernel | 26.2 vanilla | 26.2 Kernel |
| --- | ---: | ---: | ---: | ---: | ---: |
| Random | 0 | 27 | 18 | 20 | 14 |
| Random | 16 | 112 | 149 | 105 | 147 |
| Random | 32 | 440 | 405 | 393 | 395 |
| Random | 33 | 410 | 405 | 282 | 283 |
| Random | 128 | 1462 | 1460 | 1716 | 2063 |
| Random | 256 | 4372 | 4443 | 4356 | 4338 |
| Random | 257 | 4594 | 4533 | 4449 | 4357 |
| Random | 512 | 22490 | 7734 | 20717 | 7354 |
| Random | 1024 | 55935 | 16339 | 52256 | 15736 |
| Random | 4096 | 273002 | 75124 | 261382 | 75398 |
| Random | 16384 | 1336606 | 309815 | 1253033 | 302299 |
| Already ordered | 4096 | 27016 | 13362 | 26996 | 17464 |
| Reversed | 4096 | 76897 | 53065 | 70701 | 53489 |
| Repeated keys | 4096 | 225067 | 48131 | 219866 | 52750 |
| Random, above retention cap | 32768 | 3026774 | 700494 | 2861143 | 583527 |

For the 4,096-quad random input, allocation fell from 49,216 to 16,424 bytes per operation on 1.21.4,
and from 49,216 to 16,448 on 26.2. The 32,768-quad input exceeded the retention cap and allocated slightly
more than vanilla: 397,456/397,480 bytes versus 393,280, because its scratch and histograms are temporary.
The tested nonempty batches below 512 quads retained vanilla's allocation totals. Factory construction
is outside the timing loop; wrapping the original factory result also creates a Kernel sorter object.

The wrapper does not improve every input: the 16-quad case was about 40 ns slower, and the 128-quad
26.2 case was 347 ns slower in this run despite selecting the original sorter. These costs and run-to-run
variation must remain visible in the comparison. No broad FPS, frame-time or launch-time gain is claimed.
