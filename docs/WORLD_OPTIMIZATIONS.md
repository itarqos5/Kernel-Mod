# World and biome work

## Biome selection

Kernel reuses the eight seed-dependent corner offsets used by vanilla biome selection. Nearby blocks
query the same quart cells repeatedly. Each thread holds a bounded 128-cell cache, keyed by the complete
seed and signed quart coordinates, with about 27 KiB of primitive arrays. Entries contain no worlds,
chunks, registries, biomes or block positions. Different seeds cannot reuse one another's offsets.

The native random-mixing and offset primitives are still used on a miss. Kernel preserves corner order,
floating-point operation order and strict tie selection, then calls the current biome source once for
the selected coordinates. It does not cache the biome-source result, alter generation random streams,
parallelize world mutations or retain chunks. The implementation serves both client biome queries and
the integrated server. This is one narrow optimization, not Lithium parity or a new terrain generator.

## Controls and compatibility

**Kernel → Optimizations → Biome offset reuse** stages the setting with Apply/Done/Cancel. Changes
require restarting Minecraft. The setting is stored as `biome_offsets=true` in
`config/kernel-world.properties` and defaults on. Set it to `false` to disable the Mixin. Unknown keys
survive saves; unreadable settings disable the optimization for recovery.

If Lithium is present, Kernel leaves biome selection to it and disables this control with an explanation.
Other mods that replace `BiomeManager.getBiome` need explicit compatibility testing or this switch off.
This does not claim support for dedicated-server installations; Kernel's Fabric environment remains client.

## Measurements and limits

The isolated benchmark compares the target game's native biome selection against the cache, checking
the chosen biome coordinates first. It warms the JVM, alternates measurement order and takes the median
of seven samples. Results below are from the available Ryzen 5 5600G on Windows:

| Target / runtime | Query pattern | Native ns/lookup | Kernel ns/lookup |
|---|---|---:|---:|
| 1.21.4 / Java 21.0.12 | Neighboring blocks | 97.66 | 29.08 |
| 1.21.4 / Java 21.0.12 | Scattered cells | 192.50 | 206.41 |
| 26.2 / Java 25.0.1 | Neighboring blocks | 95.77 | 29.80 |
| 26.2 / Java 25.0.1 | Scattered cells | 192.72 | 204.37 |

Local reuse improves this operation; misses add overhead. These measurements exclude the Mixin adapter
and thread-local lookup and are not world-loading, frame-time or FPS results. Total launch/world-load
improvements require repeated complete-workload measurements. The cache may be undesirable for mods
whose biome queries have little spatial locality.

```powershell
.\gradlew.bat :mod:1.21.4:biomeJitterBenchmark :mod:26.2:biomeJitterBenchmark
```

## Correctness checks

Unit tests compare actual vanilla biome selection over local cell boundaries, random full-width signed
coordinates, seed changes and cache evictions. Real Fabric/Mixin smoke tests exercise concurrent queries,
exact biome-source invocation, disabled settings and a test-only Lithium presence marker. The marker has
no third-party implementation and is never bundled in a release. These smoke tests run with `buildAll`.

```powershell
.\gradlew.bat :mod:26.2:worldGenerationComparison
```

This additional launch test creates separate ordinary-terrain worlds with a fixed seed, with reuse off
and on. A test-local worker limit and ordered terrain/decoration preparation of the surrounding halo
control generation ordering: unconstrained 26.2 baseline launches exhibited differing overlapping
stone deposits even with this optimization disabled. No worker limit is installed in user profiles.
The probe also compares every actual biome-source coordinate with the native selection throughout
generation, including worker-thread calls. It generates nine remote, non-ticking full chunks and compares
SHA-256 fingerprints of every
block state and biome entry. The game saves/exits normally. Existing user saves are never opened. The
reported generation-plus-fingerprint time is diagnostic, not a controlled end-to-end benchmark. The
tests cover selected seeds and positions, not every possible world, datapack or mod combination.

All nine supported targets passed the controlled off/on fingerprint comparison. Settings interaction
probes passed on 1.21.4 and 26.2, and the release build passed 749 unit tests plus real Mixin smoke checks.

## Noise interpolation arrays

Each supported version's `NoiseChunk.NoiseInterpolator.allocateSlice` first creates a complete rectangular
array and then replaces every row with another identically sized, zero-filled row. Kernel creates that
rectangular array once. Every call still owns its complete set of rows; dimensions, positive-zero values,
negative-size exceptions and integer overflow behavior are unchanged. Noise calculations, coordinates,
random streams, cell traversal and generation scheduling remain native.

**Optimizations → Noise slice allocation** controls this independently of biome reuse. The restart-only
`noise_slices` setting in `config/kernel-world.properties` defaults on and is disabled alongside the biome
optimization when Lithium is present or configuration cannot be read. This is an allocation reduction,
not a terrain algorithm or a claim of complete world-loading acceleration.

The real Mixin checks instantiate only the allocation receiver without its world-dependent constructor
(test-only use of `Unsafe`) and invoke the actual mapped method. They check row shape, fresh ownership,
zero values and invalid dimensions, then measure allocated bytes with the JVM's thread-allocation counter.
All nine actual mapped methods measured 4,120 bytes per 5×49 slice with the optimization disabled and
2,080 bytes with it enabled on the available Java 21/25 runtimes. The Lithium-presence checks retained
the native 4,120-byte behavior. No world-load timing or FPS claim follows from this byte count.

The combined world optimizations also passed fresh off/on block/biome fingerprint comparisons on 1.21.4,
1.21.6 and 26.2. GUI Apply/Done/Cancel checks cover both independent switches on 1.21.4 and 26.2.
The full release build passed 821 unit tests, all 27 world activation/conflict checks, the existing renderer
and agent checks, and exact artifact/metadata/bytecode-level inspection across all supported targets.

Broader chunk I/O, world generation, server scheduling and stutter attribution remain separate work.

## End island heights

The native End island height function depends on an immutable simplex-noise source and two integer
coordinates. Its 25×25 neighborhood calculation repeats across vertical samples and nearby terrain
queries. Kernel caches the exact returned float, including native NaN/overflow behavior. Misses call
the original method. No noise arithmetic, random-number consumption or generation ordering changes.

Each thread retains at most 1,024 entries in about 13 KiB of primitive arrays. Full signed coordinate
keys and one of four weak source identities distinguish entries. Replacing a source invalidates its
entries; collected sources cannot keep a world alive. The cache holds no chunks, registries or contexts.
Custom `SimplexNoise` subclasses bypass caching because their values or call side effects may change.
Other mods that alter the native source's purity need explicit compatibility testing or this switch off.

**Optimizations → End island height reuse** controls `end_island_heights` in
`config/kernel-world.properties`. It defaults on, requires restart, preserves unknown settings and
disables itself when Lithium is installed or the config cannot be read. It is independent of the biome
and noise-array settings. Ordinary Overworld and Nether noise evaluation is not replaced.

Unit checks compare cached outputs with the actual target's native private method across multiple
sources, local/full-width signed coordinates, eviction and exact float bit patterns. Deterministic
weak-reference clearing checks source replacement without relying on GC timing. Real Mixin probes
compare native sources against an uncached subclass with identical noise, inspect cache population,
verify repeated subclass calls and run concurrent queries from four threads. Enabled, disabled and
Lithium-presence modes are included in `buildAll`.

```powershell
.\gradlew.bat :mod:1.21.4:endIslandBenchmark :mod:26.2:endIslandBenchmark
.\gradlew.bat :mod:26.2:endGenerationComparison
```

The isolated benchmark alternates native/cache measurement order and reports the median of seven
warmed samples for vertical reuse and scattered misses. It excludes Mixin dispatch and complete world
generation. The End launch comparison creates two new worlds, prepares a remote End halo in fixed
order and compares all block/biome entries of nine full chunks. Its generation-plus-fingerprint time
is diagnostic; it is not a startup, world-opening or gameplay FPS benchmark. Test saves remain isolated
and the clients save/exit normally.

On the available Ryzen 5 5600G, the isolated End-height results were:

| Target / runtime | Pattern | Native ns/query | Kernel ns/query |
|---|---|---:|---:|
| 1.21.4 / Java 21 | Local vertical reuse | 7,517.66 | 246.99 |
| 1.21.4 / Java 21 | Scattered | 9,058.03 | 8,869.07 |
| 26.2 / Java 25 | Local vertical reuse | 7,322.35 | 241.78 |
| 26.2 / Java 25 | Scattered | 8,612.13 | 8,619.58 |

The End fixture uses seed `2718281828`, a 5×5 preparation halo, and full chunks at x=128–130,
z=176–178. Each comparison fingerprints 100,394 non-air blocks as well as all air and biome entries.
An earlier sparse fixture at x/z=1024–1026 also matched on the endpoints. These correctness launches
showed variable generation-plus-hashing times and do not establish a consistent end-to-end speedup.

All nine dense End comparisons matched exactly. All 27 enabled/disabled/Lithium-presence checks passed,
as did endpoint bootstrap/window-adoption and GUI interaction checks for the three independent world
controls. The release build passed 884 unit tests and exact artifact/metadata inspection for all targets.
