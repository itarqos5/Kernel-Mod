# Deferred legacy chunk buffers

**Deferred chunk buffers** controls `section_buffers` in `config/kernel-renderer.properties`.
It defaults to enabled and takes effect after restarting. It is available only on Minecraft 1.21.4.
The other eight supported targets retain their native upload-time allocation and do not display this
control. This does not change the supported game-version matrix.

## Ownership and lifecycle

The 1.21.4 renderer eagerly creates five `VertexBuffer` objects per render section. Each immediately
reserves a GPU buffer name and a vertex-array name, even if that layer is never populated. Kernel
creates a final marker subclass at this one native section factory. It preserves the original map,
stable per-layer object identity, constructor thread check and worker lookup behavior, while deferring
the vertex-array name until the first render-thread bind and the underlying `GpuBuffer` until vertex
upload. A direct vertex upload also creates an array if it has not yet been bound.

The native upload still owns mesh cleanup, index updates, vertex formats and drawing. Allocation does
not bind a different vertex array. Direct upload initialization stays inside the native resource-close
scope after its closed-buffer and thread checks, so Java allocation failure still closes the mesh.
Index-only uploads retain the native behavior of
writing the currently bound vertex array; they do not require vertex storage to be allocated first.

Unused layers close without creating GPU resources. Native close still releases any index storage and
marks the object invalid. Uploads queued after release close their input through the normal native
path and cannot recreate storage. Repositioned sections retain previously allocated buffers as before.
Once a layer has been used, Kernel does not reclaim it early or copy/compact its geometry.

Ordinary buffers and independently created subclasses remain eager. The allocation wrapper delegates
their constructor operation normally, including other wrappers. Its varargs forwarding adds 32 bytes
to an ordinary eager construction in the measured Java 21 runtime; section placeholders avoid that
forwarding. This tradeoff concerns infrequent non-section buffer construction, not every draw. A
competing replacement of the section factory or these buffer internals may require disabling the
feature. Newer renderers are untouched.

Names are reserved objects, not a full allocation of vertex data. OpenGL creates usable vertex-array
state on binding; see the [Khronos object lifecycle reference](https://wikis.khronos.org/opengl/GLAPI/glGenVertexArrays).
The avoided name creation and Java objects are not evidence of an equivalent reduction in VRAM.

## Validation

Native driver checks construct actual render sections, inspect unused allocation, perform worker
lookups and reject off-thread first binding. They compare uploaded vertex bytes and rendered pixels,
exercise repeated upload and transparency-index replacement, reposition a section, and release layers
both before and after upload. Closed uploads must dispose of their mesh without resurrecting storage;
index-only buffers and independently created subclasses are checked separately.

The test index-only upload uses an isolated vertex array and restores the prior binding. The game
probe additionally renders an actual flat world with Kernel shaders and counts allocated and unused
layers. The GUI probe checks Cancel, Apply, Done and restart-only activation. These helpers are not
packaged in release JARs.

```powershell
.\gradlew.bat :mod:1.21.4:runShaderSmoke -PkernelSectionBuffers=true -PkernelSectionBufferBenchmark=true
.\gradlew.bat :mod:1.21.4:runShaderSmoke -PkernelSectionBuffers=false -PkernelSectionBufferBenchmark=true
.\gradlew.bat :mod:1.21.4:runBootstrapSmoke
.\gradlew.bat buildAll
```

The optional benchmark measures create-and-close of unused buffers on the render thread. It uses
4,096 buffers per round, three warmup rounds, nine measured rounds and alternating order. These
isolated costs do not measure full world entry, gameplay frame times or broader renderer parity.

On the available Windows/Ryzen 5 5600G/Radeon RX 580 system with Java 21.0.12, the final enabled run
measured 807.52 ns and 112 bytes for ordinary eager create-and-close, versus 48.19 ns and 48 bytes for
an unused deferred section buffer. In the disabled control, both classes remained eager: 866.11/839.60 ns
and 80/80 bytes respectively. The extra 32 bytes on the enabled ordinary path are the forwarding
tradeoff described above; they must not be counted as savings from native section allocation.

The actual flat-world probe contained 1,944 render sections with five layers each. The disabled run
allocated all 9,720 layer buffers. The final enabled run had 120 allocated layers and 9,600 layers
without GPU objects. This is a snapshot of that fixture, not a general world-memory or loading-time
claim; populated-layer counts vary with camera and chunk arrival timing. Native shader-world rendering,
resorting and normal world save/exit passed in both modes.

The final nine-target `buildAll` passed 1,331 tests in 385 suites with no failures, errors or skipped
tests, plus nine native renderer checks, 27 world-optimization ownership checks and Java 21/25 agent
checks. All nine bootstrap/settings probes adopted the original loading window, and the 1.21.4/26.2
shader-world probes passed. Inspection verified exactly nine mod JARs and one Knot Client JAR,
including correct version/Java metadata, native adapters, bundled bootstrap identity and exclusion of
test/benchmark classes. Both component versions remain 0.1.0. Other physical GPU/OS validation remains
outstanding; this change does not establish full Sodium parity or an overall launch-time improvement.
