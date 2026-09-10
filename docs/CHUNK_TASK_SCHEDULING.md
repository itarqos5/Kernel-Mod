# Pending section-task scheduling

Kernel replaces the client renderer's pending section-task queue on all nine supported versions.
It retains Minecraft's worker executor, buffer-builder pool, mesh compiler, task bodies, GPU uploads,
completion callbacks and mesh cleanup. It does not change server chunk tickets or world generation.

## Selection and cancellation

Two indexes separate initial builds from recompiles. Selection uses the exact squared distance to the
section origin block's center, including its `+0.5` offset. It does not substitute the section center.
Equal distances retain insertion order; initial builds win ties against recompiles. A nearer recompile
can precede an initial build twice before the initial-build quota resets, matching vanilla's rules.

Native rebuild and transparency-resort tasks provide fixed origin coordinates while pending. Their
cancellation methods notify the queue directly, so obsolete jobs do not wait for a complete list scan.
The callback registration rechecks cancellation, and stale removal handles cannot remove a requeued
task. Repeated insertion of the same pending native task is coalesced. Unknown task subclasses retain
live getter evaluation and duplicate entries, including custom changes to position or priority.

Large moving-camera queries use an original median-partitioned 3D point index with nearest-bound pruning.
Insertion uses a small pending buffer; enough insertions or removed entries trigger a lazy rebuild.
Small indexes use a direct scan. Repeated stationary queries build an indexed distance heap, avoiding
repeated spatial searches while the camera is unchanged. Cancellation removes heap entries directly.
Median selection falls back to sorting when partition work exceeds its budget.

All queue mutations and queries share one monitor. Clearing the queue detaches every listener, cancels
remaining tasks in insertion order, and completes cancellation even if a custom callback throws. In-flight
jobs still obey Minecraft's cancellation and completion checks. The index does not hard-cap live backlog
size or prevent every obsolete in-flight mesh from finishing; those dispatcher policies remain separate work.

Memory is proportional to pending jobs, including task entries, point nodes, identity lookup and reference
arrays. This trades additional per-job bookkeeping for cheaper selection and cancellation. It is not a
memory-reduction claim. No worker threads, global executors or native GPU allocations are added.

## Compatibility and validation

The adapter uses `CompileTaskDynamicQueue` through 26.1.2 and `SectionTaskDynamicQueue` in 26.2. It handles
the corresponding task-base and concrete rebuild-class renames with Stonecutter branches. Exact native
classes use fixed coordinates; subclasses use the live path. Mods that replace the queue or change the
native task-origin lifetime still need explicit compatibility testing.

Tests compare selections against an independent expression of vanilla's priority rules, and compare
3D nearest queries against each target's actual `BlockPos.distToCenterSqr`. Coverage includes ties,
extreme integer origins, nonfinite cameras, index/heap transitions, custom task mutation, registration
races, stale callbacks, simultaneous producers/cancellation/polling, large ordered backlogs and clear
callbacks that fail. Real Fabric/Mixin smoke tests construct native tasks and exercise cancellation and
selection without starting Minecraft or a graphics device.

Run the complete matrix with `./gradlew buildAll` (or `./gradlew.bat buildAll` on Windows). The isolated
workload can be rerun with:

```powershell
.\gradlew.bat :mod:1.21.4:chunkTaskQueueBenchmark :mod:26.2:chunkTaskQueueBenchmark
```

That benchmark measures inserting, optionally cancelling, and draining randomly positioned section jobs.
It compares the index with the vanilla linear selection rules using a FastUtil list, across stationary
and moving cameras. It includes queue bookkeeping but excludes task construction, mesh compilation,
uploads and rendering. Its results are not gameplay frame-time or world-load measurements.

An isolated warmed run on this Windows development machine (2026-09-10, Java 21/25, fixed 512 MiB heap,
eight warmups and seven samples, median reported) measured the following complete 8,192-job workloads.
The reference is an independent implementation of vanilla's linear rules, not a running game.

| Runtime | Camera | Cancellation | Linear | Kernel |
| --- | --- | --- | --- | --- |
| Java 21 | Stationary | None | 151.4 ms | 5.8 ms |
| Java 21 | Moves each poll | None | 151.5 ms | 44.1 ms |
| Java 21 | Moves each poll | Every second job | 39.7 ms | 16.8 ms |
| Java 25 | Stationary | None | 173.5 ms | 5.6 ms |
| Java 25 | Moves each poll | None | 111.0 ms | 46.4 ms |
| Java 25 | Moves each poll | Every second job | 25.4 ms | 18.6 ms |

Small workloads showed much smaller differences and timing noise. These measurements do not establish
frame-time gains, memory savings or performance parity with another renderer. Reproduce them alongside
real chunk-build traces before using them to predict gameplay behavior.
