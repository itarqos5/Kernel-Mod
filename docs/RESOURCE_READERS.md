# Resource reader buffers

**Resource reader buffers** in Optimizations controls `compact_readers` in
`config/kernel-resources.properties`. It defaults to enabled on all nine supported targets and takes
effect on the next launch. Apply/Done save a draft; Cancel discards changes since the last Apply.
Unreadable or malformed preferences disable this optional optimization for that launch and preserve
the file. Unknown keys survive saves. Set `compact_readers=false` to recover native buffering.

Minecraft's `Resource.openAsReader()` opens its normal stream and UTF-8 decoder, then constructs a
JDK `BufferedReader`. Kernel changes only that constructor to request 2,048 characters. The tested
Java 21 and 25 defaults use 8,192 characters, so each initial character array saves 12,288 bytes.
Resources and readers remain independently owned; there is no cache, pooling or global JVM change.
Resource reloads and integrated-server data loading use the same behavior as initial startup.

This uses the [JDK BufferedReader contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/BufferedReader.html),
including bulk-read bypass, mark/reset, skipping, line handling and close. Large files and lines are
still supported. A large mark read-ahead limit can grow the buffer. Read chunk sizes, prefetch timing,
mark expiration after exceeding its limit and decoder allocation may differ from a default-sized
reader. Applications must follow the Reader contract rather than depending on its private capacity.
Smaller initial buffers do not guarantee lower total allocation or faster parsing for every input.

Only the constructor inside the native factory is intercepted. Resource subclasses overriding the
reader retain ownership; inherited calls still use their overridden `open()` stream. A competing
constructor redirect or overwritten factory can take ownership, in which case the optional injection
may be skipped. The redirect applies after standard constructor redirects so Mixin's constructor
validation sees a winning redirect already applied. Kernel uses priority 900 to yield to ordinary
priority-1000 owners; optional injection-count checks also permit a skip. A synthetic ordinary-priority
owner is exercised separately with Mixin injection-count debugging enabled. This is not a guarantee
for arbitrary mod injection orders. The GUI reports the configured launch state, not a claim about every third-party
resource implementation. No other mod's implementation has been copied or bundled.

## Validation

The shared native harness launches Fabric/Mixin on each target. It compares complete decoded content
for arbitrary/malformed UTF-8 and multibyte boundaries, short streams, empty/large inputs, bulk reads,
long mixed-newline lines, marks beyond the compact capacity, skipping, CharBuffer positions, transfer,
line streams, closure, errors, custom resource overrides and concurrent independent readers. It also
measures actual native factory allocation with the setting enabled, disabled and a competing owner.

```powershell
.\gradlew.bat :mod:1.21.4:resourceReaderEnabledSmoke :mod:1.21.4:resourceReaderDisabledSmoke -PkernelResourceReaderBenchmark=true
.\gradlew.bat :mod:26.2:resourceReaderEnabledSmoke :mod:26.2:resourceReaderDisabledSmoke -PkernelResourceReaderBenchmark=true
.\gradlew.bat :mod:1.21.4:resourceReaderCompetingSmoke :mod:26.2:resourceReaderCompetingSmoke
.\gradlew.bat buildAll
```

The optional benchmark reads a size-distributed sample of up to 192 models, language files and shader
sources from the installed target JAR before measuring. Synthetic large Unicode JSON and long lines
exercise larger inputs. Both paths open the same memory-backed resource, use the same UTF-8 decoder
and perform the same parsing/read operation. Eight warmup rounds precede eleven alternating measured
rounds; medians report time and thread allocation per resource. Separate disabled runs control for
equivalent native/live factories. These exclude disk I/O and resource discovery and cannot establish
whole-launch gains. No game assets are included in Kernel's source or artifacts.

Initial development comparisons tested 1,024/2,048/4,096/8,192-character buffers on Java 21 and 25.
Smaller buffers reduced allocation in the sampled small resources. Large Unicode inputs showed extra
decoder allocation at smaller sizes, and long-line costs were similar. The 2,048-character choice
balances initial allocation reduction with that large-input tradeoff.

On the available Ryzen 5 5600G, the enabled native factory checks measured 25,000/12,712 bytes per
empty reader on Java 21.0.12 and 24,872/12,584 on Java 25.0.1 (reference/Kernel). The disabled live
factory matched the native reference. Sampled model JSON allocation was 30,463.6/18,177.9 bytes
per file on Java 21 and 30,212.9/17,927.2 on Java 25. The synthetic large Unicode
JSON allocated 11,288 bytes more with the smaller buffer while parsing about 11.2 MB of objects;
this demonstrates why the initial-array saving is not a universal total-allocation claim.

Isolated model parsing medians were 11.50/9.92 microseconds on Java 21 and 7.28/5.72 on Java 25.
Equivalent disabled factories measured 6.85/6.74 and 7.20/7.34, showing substantial process/JIT
variation, particularly on Java 21. Large Unicode JSON and long-line timings were similar or slightly
slower with the smaller buffer. No universal parsing speedup or end-to-end startup gain is claimed.

Six 26.2/JFR bootstrap launches in off/on/on/off/off/on order reached game-ready at
10,164 / 9,831 / 10,246 / 9,842 / 10,097 / 9,709 ms of JVM uptime. Startup caches and all six world
optimizations stayed enabled. All six same-window and settings probes passed. The enabled and
disabled ranges overlap, so these warm filesystem measurements do not establish a consistent
startup-time gain. The developer init script accepts `-PkernelProfileReaders=true|false` to repeat
this comparison without changing launcher profiles or installing JVM options.

Final validation passed `buildAll` with 1,358 JUnit tests in 394 suites (no failures, errors or skips),
27 native reader processes across the nine targets, all 27 existing world-optimization ownership
checks, nine renderer checks and both Java generations' agent/fallback checks. All nine bootstrap
probes adopted their original native window and exercised the resource toggle's Cancel/Apply/Done
and restart behavior. The 1.21.4 and 26.2 shader probes also passed native GPU and world-render checks.
All ten release artifacts had the correct names, metadata, bytecode levels and identical bundled
Knot Client; no test or third-party classes were packaged. Both component versions remain 0.1.0.
