# Chunk camera snapshots

Minecraft 1.21.11, 26.1.2 and 26.2 copy the same camera matrix for every populated section while preparing
chunk draw commands. Kernel can reuse an unchanged copy within that preparation invocation. Earlier
supported targets keep their original paths because they do not use this particular batch uploader.

The **Chunk camera snapshots** switch in Kernel's Optimizations tab controls `chunk_uniforms` in
`config/kernel-renderer.properties`. It defaults to enabled on the three applicable targets and takes
effect at the next launch. Unsupported targets hide the option. Malformed configuration follows the
existing renderer recovery rules and disables the optimization.

## Ownership and correctness

A MixinExtras invocation-local reference holds the last copied matrix. Before reusing it, Kernel checks
all sixteen raw float values and JOML property flags against the current input. A changed input causes
a fresh constructor call. Signed zero, NaN payloads and manually changed property flags remain distinct.
Custom matrix implementations/subclasses and constructor results that alias the caller's input are not
retained. There is no cross-frame cache or thread-local pool.

Kernel never changes a copied matrix after handing it to a native chunk uniform. This is necessary
because Minecraft's uniform storage retains its last record after the upload. New preparation calls,
including nested calls and calls on other threads, receive independent references through
[MixinExtras Share](https://github.com/LlamaLad7/MixinExtras/wiki/Share). Normal stack unwinding handles
exceptions without a separate release callback.

The section records, upload ordering, shader layout, chunk visibility values, draw commands and native
GPU buffer lifecycle stay under Minecraft's control. This optimization reduces one source of temporary
allocation; it is not a replacement chunk renderer or evidence of Sodium parity.

## Verification

CPU tests vary every matrix component, property flags, signed zero, NaN payloads, input mutations and
custom matrix ownership. On the three applicable targets, 4,096 records compare bytes written by the
actual native `ChunkSectionInfo.write` implementation and check that retained records stay unchanged.
The writer requires a direct buffer, which the tests allocate explicitly.

The isolated shader/gameplay probe observes records immediately before the native chunk uniform upload.
It checks sharing when enabled, independent native copies when disabled, independent preparation
invocations, and unchanged retained snapshots over later frames. These helpers are excluded from JARs.

An isolated benchmark compares the original matrix constructor with the raw-value reuse check. It
stores results in escaping arrays, changes the camera between batches, alternates measurement order,
warms both paths, and reports the median of seven samples with per-thread allocation counts. It excludes
the injected invocation-local reference, the rest of chunk preparation and GPU work. Gameplay frame-time
improvements require separate measurement; no broad FPS gain is claimed.

On the available Ryzen 5 5600G / Windows machine, the median constructor/check measurements were:

| Sections per batch | Java 21 native → Kernel, ns | Java 25 native → Kernel, ns | Native → Kernel bytes per batch |
| --- | ---: | ---: | ---: |
| 1 | 43.7 → 44.0 | 39.4 → 42.4 | 80 → 80 |
| 32 | 879.9 → 192.3 | 463.9 → 126.8 | 2,560 → 80 |
| 512 | 5,613.7 → 2,542.4 | 5,430.3 → 1,941.9 | 40,960 → 80 |
| 2,048 | 20,468.4 → 10,179.8 | 21,508.1 → 7,705.4 | 163,840 → 80 |

Single-section work has no matrix-allocation benefit and a small measured comparison overhead. The
actual Mixin also introduces one small local-reference holder per preparation invocation, excluded
from these isolated figures. The live on/off probes passed on all three applicable game versions.
Final validation passed all nine shader/gameplay probes, both endpoint bootstrap/GUI probes, `buildAll`,
1,052 unit tests in 268 suites, the renderer/world/agent process checks and exact inspection of all ten
release artifacts. Physical rendering coverage remains limited to the available Windows/AMD system.

```powershell
.\gradlew.bat :mod:1.21.11:chunkMatrixBenchmark :mod:26.2:chunkMatrixBenchmark
.\gradlew.bat :mod:1.21.11:runShaderSmoke :mod:26.1.2:runShaderSmoke :mod:26.2:runShaderSmoke -PkernelChunkUniforms=false
.\gradlew.bat :mod:1.21.11:runShaderSmoke :mod:26.1.2:runShaderSmoke :mod:26.2:runShaderSmoke -PkernelChunkUniforms=true
```

The probe property changes only its isolated `build/shader-smoke-game` configuration. It does not
override renderer choices in a normal game installation.
