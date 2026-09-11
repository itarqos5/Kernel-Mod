# Packed block decoding

**Packed block decoding** in Kernel's Optimizations tab controls the restart-only `packed_storage`
setting in `config/kernel-world.properties`. It is enabled by default and yields to Lithium. The
setting applies to the client and its integrated server on all nine supported game versions.

Kernel intercepts packed-array reads inside `PalettedContainer`, used when Minecraft re-encodes chunk palettes for
storage and for loading formats that need palette conversion. Arrays of at least 256 values using
4 through 16 bits per value use constant-shift decoding. Each packed long is loaded once, its values
are written in the original order, and a partial final word writes only the remaining logical values.
No scratch array, persistent cache, new storage format or alternate palette mapping is introduced.
`SimpleBitStorage` itself remains unchanged. Through 1.21.8, separate pack/load call sites are adapted;
newer targets share a re-encoding call site. Native palette conversion, locking and IO remain intact.

Small arrays, other bit widths, custom subclasses and unsuitable output arrays use Minecraft's
original method. Fallback happens before any output write. In particular, short destinations retain
native partial-write/exception behavior. The packed source is never modified, and unused destination
elements retain their values. No source or output reference is retained between calls.

This reduces a specific decoding cost; it does not skip generation, move world state between threads,
change random-number consumption, replace chunk serialization or establish an overall world-load gain.

## Validation and measurement

The original decoder prototype matched 7,168 packed arrays against native bulk and indexed reads on
Java 21 and Java 25. Cases cover all 32 bit widths, arbitrary high/padding bits, signed packed words,
boundary/odd sizes and preserved output tails. Unit tests also cover source mutation between calls,
unsupported dimensions and fallback without partial writes. Fabric process probes exercise enabled,
disabled and Lithium-conflict ownership, native subclasses, null/short outputs and steady-state allocation.

The final `buildAll` run passed 1,295 tests in 367 suites with no failures, errors or skipped tests.
All 27 enabled/disabled/conflict process checks passed across the nine supported versions, including
zero steady-state allocation for the exercised native decoder and fallback paths. Nine native settings
and same-window bootstrap probes passed. Enabled/disabled Overworld runs on 1.21.4 and 26.2 produced
identical block/biome fingerprints for nine chunks each. The nine release mod JARs and single Knot Client
JAR passed metadata, bundled-client hash, Java-version and production-only packaging checks.

```powershell
.\gradlew.bat :mod:1.21.4:packedStorageBenchmark :mod:26.2:packedStorageBenchmark
```

The benchmark runs without Fabric. It alternates the native method and Kernel's decoder, uses eight
input arrays, warms both paths, and reports the median of nine rounds of 20,000 decodes. It measures
the decoder directly, without the Mixin wrapper or chunk IO. On the available Ryzen 5 5600G, these
representative results were recorded with no game or Gradle build running:

| Bits/value | Java 21 native / Kernel | Java 25 native / Kernel |
| --- | --- | --- |
| 4 | 2,389 / 1,356 ns | 2,181 / 1,337 ns |
| 5 | 2,663 / 1,306 ns | 2,437 / 1,352 ns |
| 8 | 3,414 / 1,068 ns | 3,067 / 1,074 ns |
| 15 | 5,155 / 1,356 ns | 4,465 / 1,077 ns |

Each measurement decodes 4,096 values into an existing destination. All thirteen implemented widths
were measured; these rows illustrate common local and larger palette widths. Results are isolated CPU
measurements on one machine, not multiplayer throughput, launch-time, world-load or frame-time claims.
