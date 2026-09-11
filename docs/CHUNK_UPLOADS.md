# Chunk upload scheduling

On 1.21.x, Kernel drains Minecraft's native chunk-upload queue in FIFO order. Each normal
pass performs at least one task, then stops after 32 tasks or its time budget. A task already
running cannot be interrupted, so the budget is a scheduling threshold, not a hard stall limit.
Renderer shutdown drains the queue completely. Deferred mesh cleanup on 1.21.6 and newer
retains Minecraft's existing lifecycle.

The budget now adapts between 0.25 and 2 ms using recent render-thread CPU work. The frame
clock excludes native presentation and FPS-limiter waits, including nested calls. Upload
time is subtracted before estimating the rest of the frame's cost. The target interval comes
from the monitor refresh rate, or a lower active native FPS limit, with a 60 Hz fallback.
The estimator reserves one eighth of that interval, with a minimum 0.5 ms reserve.

Higher work samples reduce the next budget immediately. Lower samples decay the estimate
gradually, and the budget recovers by at most 0.125 ms per frame. This avoids immediately
releasing a large upload burst after one light frame. Calls outside an active frame use the
previous 2 ms limit. This is CPU scheduling; it does not predict GPU completion or prevent
an individual native upload from blocking inside a graphics driver.

The Optimizations tab's **Chunk upload budget** switch controls the whole feature and
requires a restart. Its persisted key remains `chunk_upload` in
`config/kernel-renderer.properties`. For a process-local comparison or recovery, explicitly
launching with `-Dkernel.chunkUpload.adaptive=false` retains the fixed 2 ms scheduling limit.
Kernel does not install this option into launcher profiles or global JVM settings.

Minecraft 26.x uses native staging buffers, paired vertex/index completion and different
mesh-lifetime rules. Kernel does not apply this legacy queue budget or clock to that path.

## Validation

Unit tests use a deterministic clock to cover nested frames/waits, excluded upload cost,
overload response, gradual recovery, reset and invalid samples. Existing scheduler tests
cover FIFO order, task limits, guaranteed progress and complete shutdown draining.

`runChunkUploadFixed` and `runChunkUploadAdaptive` launch a real client, create a separate
flat test world, move across chunks and verify that visible sections finish rendering. On
1.21.x the helper adds 18 ms of controlled render-thread work, checks the selected upload
budget while native uploads occur, removes the load and verifies full budget recovery.
On 26.x it verifies that the legacy clock stays inactive. These helpers and their mixins are
test-only and are excluded from release JARs.

The controlled-load checks pass on the available Windows/AMD machine. They establish
response and lifecycle behavior, not an improvement in gameplay percentiles: the initial
fixed/adaptive runs did not show a consistent tail-frame improvement. Real repeatable
terrain-traversal benchmarks, pressure from larger modpacks and other GPUs remain required
before claiming measured stutter reduction. Scheduling less work can also increase the
time needed to display a large chunk backlog.
