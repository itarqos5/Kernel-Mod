# Startup caches

On a recognized official-launcher-style Fabric profile, Kernel installs its content-addressed Knot Client
JAR and one profile-local `-javaagent:` argument for that same JAR. The launcher expands
`${library_directory}` so the profile does not contain a machine-specific absolute path. This takes
effect on the next launch after installing or updating the mod. No global JVM settings, loader JARs or
game JARs are changed.

The first profile backup at `<profile>.json.kernel-backup` is retained. Restoration is still manual:
restore that backup while the launcher and game are closed. There is no automatic rollback or uninstall
UI. Unknown main classes and malformed JVM argument structures are left untouched.

Use the game profile's JVM option `-Dkernel.startupCache=false` to disable the cache hooks. This is a
process-local troubleshooting option, not a change to every Java process. An unsupported Fabric build
or absent ASM dependency also leaves normal loading in place. Audited hooks currently match the exact
released Fabric Loader 0.19.3 class bytes; other versions are not claimed to benefit from the caches.

The [early loading window](BOOTSTRAP_WINDOW.md) has its own `-Dkernel.loadingWindow=false` switch.
Disabling caches leaves that display and its optional activity labels available.

## What is cached

The raw cache retains untransformed `.class` bytes from immutable, already-open local JAR entries. Keys
include the JDK's JarFile identity, entry name, size and CRC. Returned arrays are independent copies.
Fabric still performs its own URL lookup and parent-classloader validation before requesting the bytes.
Changing the active archive, resource source or classpath cannot bypass those checks. Directory files,
custom URL handlers, failures and missing resources are not cached. As with Java's own JAR loader, loaded
archives are assumed immutable for that process; replacing installed mods requires a fresh launch.

On a miss, eligible class entries up to 1 MiB are read into a buffer sized from the JAR entry metadata.
This avoids `readAllBytes()`'s intermediate chunk buffers and final assembly copy. EOF still determines
the returned data: short hints continue reading, long hints return only the actual bytes, and errors after
the declared length still propagate. Unknown or larger sizes use the existing stream reader. The size
hint never permits unbounded preallocation, and a mismatched length cannot enter the cache. Streams close
on success and failure. This improvement also applies to first reads that receive no later cache hit.

The Mixin target cache retains ASM ClassReaders, not mutable target ClassNodes. Keys compare the complete
input byte array, including changes introduced by access wideners or other pre-Mixin transformers. Each
request still obtains those bytes from Fabric, then parses a new ClassNode using its requested reader
flags. This avoids sharing plugin-mutable trees or skipping normal transformation stages.

The raw-byte budget is 32 MiB with at most 4,096 entries. The target-reader budget charges four times
the input length against 16 MiB, with at most 2,048 entries, to allow for reader tables in addition to
the byte arrays. These are retention accounting bounds, not exact JVM heap-size measurements. JDK-owned
JAR handles are borrowed and are never closed by Kernel.

Both caches clear and stop accepting entries when Minecraft calls its initial game-load-completion
hook. A three-minute expiry handles missing completion hooks or removal of the Fabric mod; normal exit
through the Knot Client also clears them. The log reports hit/miss counts when they are released. Those
counts show reuse, not the number of seconds saved.

## Verification and measurement

`buildAll` includes Java 21 and Java 25 process smoke tests of the packaged agent. They execute both real
Fabric hooks, verify that warm entries cannot bypass parent-loader restrictions, verify that the bytecode
provider still runs on every target request, and check memory release and dependency fallback. These are
loader integration tests; they are not interactive Minecraft or GPU compatibility tests.

Run isolated benchmarks without other Gradle work running concurrently:

```powershell
.\gradlew.bat :knot-client:startupCacheBenchmark21 :knot-client:startupCacheBenchmark25 --no-parallel --max-workers=1
.\gradlew.bat :mod:1.21.4:visibilityBenchmark :mod:26.2:visibilityBenchmark --no-parallel --max-workers=1
```

The startup benchmark repeatedly reads/parses four real dependency classes in a warmed JVM. It reports
median time and allocated bytes per operation across seven samples, alternating the comparison order.
It deliberately excludes mod discovery, game initialization, texture/model preparation, shader compilation,
disk-cold startup and the distribution of cache hits in a real modpack. It cannot establish a total
launch-time improvement. The visibility benchmark compares the actual mapped vanilla implementation
with Kernel on identical section data, including the returned VisibilitySet allocation in both paths.

For end-to-end claims, use the same Java runtime, game version, mod/pack list, JVM arguments and machine;
separate cold and warm filesystem runs; alternate cache-enabled and disabled launches; and record the
time from process start to initial game-load completion alongside hit counts, allocation and peak memory.
Keep failures and slow runs in the dataset. A game-loading screen must not be used as a fabricated timer
or as evidence of a speedup.

### Local observations, 2026-09-10

The standalone commands above ran on Windows 10 Pro N (10.0.19045), AMD Ryzen 5 5600G, with a fixed
512 MiB JVM heap. These are one machine's warmed microbenchmarks, not measurements of its GPU or of
whole-game startup. Java 21 was Temurin 21.0.12+8; Java 25 was Temurin 25.0.1+8.

| Repeated operation | Java | Uncached ns/op | Cached ns/op | Uncached bytes/op | Cached bytes/op |
| --- | --- | ---: | ---: | ---: | ---: |
| Mixin target parse | 21 | 136523 | 106254 | 346382 | 303672 |
| Mixin target parse | 25 | 131199 | 106463 | 346382 | 303648 |
| JAR class read | 21 | 122261 | 7537 | 119750 | 36510 |
| JAR class read | 25 | 119572 | 7041 | 100324 | 36446 |

These warmed cache runs have essentially all hits. They do not measure the cold-miss overhead or the
hit rate of a real launch. The comparison read uses `openStream().readAllBytes()`, so it does not count
the additional intermediate-copy overhead in Fabric's original read loop.

The final visibility run produced these values, including both implementations' result allocation:

| Section fixture | 1.21.4 vanilla ns/op | 1.21.4 Kernel ns/op | 26.2 vanilla ns/op | 26.2 Kernel ns/op |
| --- | ---: | ---: | ---: | ---: |
| Empty | 59 | 125 | 127 | 188 |
| Solid | 244 | 205 | 287 | 286 |
| One opaque plane | 87409 | 6604 | 94741 | 6223 |
| Layered terrain | 50346 | 9818 | 50266 | 9010 |
| Checkerboard | 43781 | 13643 | 33064 | 13365 |
| Random, 25% opaque | 86676 | 12877 | 90563 | 11379 |
| Random, 50% opaque | 59003 | 18040 | 66320 | 17264 |
| Random, 90% opaque | 6294 | 2633 | 8569 | 5962 |

Kernel allocated 64 bytes per visibility result in every fixture; vanilla also allocated 64 bytes for
the empty/solid shortcuts, and 3,520–86,592 bytes for the tested nontrivial fixtures. The empty shortcut
was slightly slower in this microbenchmark; no blanket improvement across every input is claimed.

### Cache-miss stream reader, 2026-09-11

The `jar-entry-miss-read` case in `startupCacheBenchmark21` / `startupCacheBenchmark25` compares the
previous `readAllBytes()` stream operation with the bounded known-size reader. Both repeatedly open and
decompress the same four dependency classes. It excludes cache lookup/insertion, retained-byte cloning,
resource discovery, Mixin work and whole-game startup. Seven warmed samples alternate execution order.

| Runtime | Previous ns/read | Sized ns/read | Previous bytes/read | Sized bytes/read |
|---|---:|---:|---:|---:|
| Java 21.0.12 | 114,520 | 105,959 | 119,654 | 70,126 |
| Java 25.0.1 | 112,703 | 104,724 | 100,230 | 50,702 |

These results establish reduced temporary allocation for the tested misses; they do not establish a
modpack-wide launch-time improvement. Real startup on the bare development profile has only about
19 raw-class hits in more than 9,000 reads, so warmed all-hit benchmarks do not represent that launch.

Four 26.2/JFR launches with the complete cache hooks off/on/on/off reached the game-load callback at
9,958 / 9,956 / 9,795 / 9,775 ms of JVM uptime. Their overlapping results do not show a consistent
end-to-end advantage. JFR profiles are retained locally for diagnosis; their allocation weights are
samples, not exact memory totals or controlled performance proof.

The combined nine-target bootstrap/GUI and `buildAll` run passed 950 unit tests, Java 21/25 packaged-agent
checks and all nine real window-adoption/settings probes. The ten collected artifacts have matching
bundled Knot Client bytes, correct namespaces and no test classes. Both component versions remain 0.1.0.
