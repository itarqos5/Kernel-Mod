# Shader packs

Kernel's **Shaders** tab uses the same translucent panels and white highlights as its video settings.
It lists ZIPs in the game's `shaderpacks` directory. Drop one or more ZIPs onto the screen to import them,
or choose **Browse Modrinth**, search, and click **Install**. Select **Enable** on an installed pack to
prepare and compile it. **Shaders off** restores native world rendering. Shader changes apply immediately;
video/optimization drafts in the other settings tabs remain staged until their own Apply/Done action.
Settings never open automatically at startup.

The browser queries Modrinth's public shader-project API for the exact running Minecraft version and
prefers a stable, listed release with a primary ZIP. Metadata matching does not establish compatibility
with Kernel's renderer. Downloads are anonymous, restricted to Modrinth HTTPS hosts and verified against
the release's SHA-512 and byte count. It does not install dependencies or launch external programs.
The downloaded ZIP remains an independently installed user pack and is not bundled into Kernel.

Imports copy the original, preserve existing files and use content-derived suffixes for name collisions.
Identical packs are reused. Archive paths and shader includes are bounded and confined to the ZIP; files
are not extracted. Network and archive preparation run on a daemon worker. Cancel interrupts that work;
an in-progress network read can take up to its 20-second timeout to return. Temporary files are removed.
Downloads stop after five minutes of transfer, and archive/source/entry limits bound resource use.

## Current rendering contract

This is an original, limited **post-processing** renderer, not Iris/OptiFine shader compatibility.
Kernel currently supports up to 16 ordered `composite`, `composite1` through `composite99`, and `final`
passes, with up to eight simultaneous outputs mapped to sixteen logical color buffers. The world image,
including the hand, starts in `colortex0` and is processed before the HUD. Each pass samples the current
images and writes separate alternate images, then flips its declared targets. Passes that only write
auxiliary buffers preserve the displayed color. `final` always writes the displayed color image.
Linear filtering and edge clamping are supplied by an owned sampler. Intermediates default to RGBA8 at
the native window resolution; required buffer pairs are allocated lazily within a 512 MiB combined budget.
The budget counts both images, any complete mip chains using each selected format's declared bytes
per pixel, active custom PNG textures, and the optional four-byte-per-pixel depth snapshot; driver overhead and internal padding can add physical GPU
memory beyond that accounting.
Imports and downloads require selection before first activation. A saved selection is restored on later
launches; Kernel does not download packs automatically.

Supported uniforms are scalar `viewWidth`, `viewHeight`, `aspectRatio`, `frameTime`, `frameTimeCounter`
(seconds modulo 3600), integer `frameCounter` (modulo 720720), and `colortex0` through `colortex15`.
Legacy aliases are `gcolor`/`texture` (0), `gdepth` (1), `gnormal` (2), `composite` (3), and `gaux1` through
`gaux4` (4–7). An active, non-overridden `gdepth` sampler upgrades buffer 1 to RGBA32F unless the pack explicitly sets
its format. This is a color-buffer alias, not a depth image. Buffer 1 defaults to white; auxiliary buffers
2–15 default to transparent black. These buffers do not contain terrain normals, material data or depth
automatically. By default they clear each frame; supported clear declarations can retain auxiliary history.

World-dependent passes can also use integer `worldTime`, `worldDay` and `moonPhase`, and float
`rainStrength` and `thunderStrength`. These follow the [world/weather uniform contract](https://shaders.properties/current/reference/uniforms/world/).
Native day-clock ticks provide time within a 24,000-tick day and elapsed days, with the legacy integer day
counter wrapping modulo `Integer.MAX_VALUE`. Normal clocks yield `worldTime` from 0 through 23999;
negative custom clock values retain Java's signed remainder/division behavior. The 26.x adapter reads
the Overworld clock to retain these legacy semantics; custom timeline periods are not substituted.
Moon phases come from the native level through 1.21.10 and camera environment attributes on newer
targets. Rain and thunder use native world interpolation, including Minecraft's rain-weighted thunder.

All passes share one immutable world-input snapshot captured on the render thread. Packs without active
world uniforms incur no world snapshot or environment lookup. A world-dependent program requires actual
world inputs, and an unavailable world produces an explicit error instead of default zero values. These
names are reserved against custom PNG sampler bindings. Sun/shadow transforms, wetness smoothing and
the remaining world uniforms are still unsupported; these inputs do not provide terrain stages.

### Native depth input

`uniform sampler2D depthtex0;` and its alias `gdepthtex` sample an owned R32F snapshot at native window
resolution. Only its red channel carries depth. It uses nearest filtering, edge clamping, no mipmaps
and no flipping. The image represents native depth-writing world geometry, including transparent
targets, followed by the native first-person pass. The background has forward depth 1.0. Both names
are reserved against custom PNG bindings; `gdepth` remains the separate color-buffer-1 alias.

Kernel inserts a capture pass before Minecraft's late debug/always-on-top pass, keeping the main,
translucent, item/entity, particle, weather and rendered-cloud targets alive until they have been read.
It merges their nearest depths without modifying native textures or their samplers. Disabled cloud
passes are excluded. The capture retains no native render targets between frames. Late debug and
always-on-top gizmos are intentionally absent from this scene-depth snapshot.

Minecraft clears depth before its first-person pass. Kernel overlays only pixels whose depth differs
from that clear value, preserving world depth elsewhere; the first-person image takes precedence even over closer
world pixels. Its native FOV, clip planes and projection remain unchanged. `MC_HAND_DEPTH` is therefore
defined as **1.0**: Kernel applies no extra clip-space hand-depth scaling. This follows the meaning of
the [hand-depth multiplier](https://shaders.properties/current/reference/macros/mc_hand_depth/), rather
than assuming another renderer's multiplier. Screen effects that write native first-person depth are
also included. The world projection inputs below do not describe the separately projected hand.

All supported versions through 26.1.2 use forward depth. The 26.2 adapter converts native reverse-Z
with `1 - depth`, including its zero clear value, into the forward convention. R32F storage limits the
precision of this conversion; it does not preserve reverse-Z's extra far-distance precision. The
three [standard depth-buffer names](https://shaders.properties/current/reference/buffers/depthtex/)
describe different geometry subsets: `depthtex1` and `depthtex2` remain unsupported until native opaque
and hand stages can be separated correctly. They are not aliases of this combined snapshot.

The built-in capture program compiles during pack activation, before replacing the previous pipeline,
so it does not add shader compilation to the first world frame. Depth storage remains lazy until the
world resolution is known. Packs without an active depth sampler allocate no depth image and schedule no capture pass. Active
depth adds one world capture and, when Minecraft executes its first-person pass, one overlay draw.
Both use the GPU; production code does not read depth back to the CPU. Its R32F image shares the
512 MiB allocation budget with the color buffers and PNG inputs. Holding native temporary targets
until capture can also extend their frame-graph lifetimes. Missing, stale or differently sized captures
fail explicitly and restore native rendering; world changes invalidate depth along with color history.

### World projection inputs

Post-processing programs can request `mat4 gbufferProjection`, `gbufferProjectionInverse` and
`gbufferPreviousProjection`. The names follow the documented [matrix inputs](https://shaders.properties/current/reference/uniforms/matrices/).
Kernel copies Minecraft's actual world projection upload after hurt/view bobbing and portal/nausea
distortion. The inverse is computed once on the CPU only when requested. Matrix objects and uniform
arrays belong to the render thread; the native upload argument is returned unchanged. Capture adds no
GPU readback or synchronization, and packs without these active uniforms allocate no matrix state.

The supplied matrix transforms native world view coordinates to forward clip depth in [-1, 1].
Minecraft 26.x can use either clip-depth range depending on the graphics backend, independently of
26.2's reverse-Z projection. Kernel preserves clip X/Y/W and converts clip Z as follows:

| Native convention | Supplied clip Z |
| --- | --- |
| Forward [-1, 1] | Z |
| Forward [0, 1] | 2Z - W |
| Reverse [-1, 1] | -Z |
| Reverse [0, 1] | W - 2Z |

This matches the forward screen-depth convention of the owned world-depth snapshot. It does not
recover precision lost in depth storage. Minecraft's hand uses different native FOV/clip planes;
the world inverse must not be used to reconstruct hand geometry as though it shared this projection.
Hand projection inputs remain unsupported.

Previous projection means the last successfully completed world shader frame at the same resolution.
First use, a resize, a world change or pipeline replacement uses the current matrix as the previous
value. Incomplete renders do not advance history. Each render requires a fresh capture. Minecraft can
briefly supply a nonfinite projection, or one without a finite requested inverse, on world entry; Kernel leaves that frame native
and resumes on the next valid capture without advancing history or disabling the selected pack.
An absent capture still produces an explicit rendering error through normal recovery.
These uniform names cannot be overridden by custom PNG bindings, and arrays or non-mat4 declarations
are rejected when active.

### World view and camera inputs

`mat4 gbufferModelView`, `gbufferModelViewInverse` and `gbufferPreviousModelView` describe the native
world view transform. Kernel copies the exact `LevelRenderer` matrix argument and matching camera
position, including detached cameras. It returns the original objects unchanged. The inverse is only
calculated when active; packs without active view/camera inputs allocate no camera state. Matrix
history uses the last completed world shader frame, with the same freshness and reset rules as projection.

These names follow the [matrix input conventions](https://shaders.properties/current/reference/uniforms/matrices/).
In Kernel, view bobbing and screen distortion remain in the captured **projection**, where Minecraft
applies them. The view matrix maps camera-relative, world-aligned positions into native view space.
For depth-written world geometry, applying projection inverse and then model-view inverse reconstructs
that camera-relative position. The supplied matrices must be used together; adding another bob transform
would apply the effect twice. This differs from engines that place bobbing in model-view and does not
establish terrain-program or hand compatibility. See the [coordinate-space reference](https://shaders.properties/current/how-to/coordinate_spaces/)
for the distinction between camera-relative and world coordinates.

Supported [camera inputs](https://shaders.properties/current/reference/uniforms/camera/):

| Uniform | Type | Kernel value |
| --- | --- | --- |
| `cameraPosition` | `vec3` | Current camera, with horizontal coordinates rebased for precision |
| `previousCameraPosition` | `vec3` | Previous completed camera expressed in the current horizontal origin |
| `cameraPositionInt`, `previousCameraPositionInt` | `ivec3` | Unshifted floor of each world-coordinate component |
| `cameraPositionFract`, `previousCameraPositionFract` | `vec3` | Corresponding fractional remainder in [0, 1) |
| `eyeAltitude` | `float` | Current native camera Y coordinate |

Kernel maintains a horizontal origin until X or Z exceeds 30,000 blocks from it, then moves that axis's
origin to a nearby multiple of 30,000. Both frames are rebased together, preserving their relative motion.
Y is never shifted. This is Kernel's own rebasing policy, not a claim of identical upstream reset timing.
Integer/fractional inputs preserve negative and far-world coordinates without the large rounding error
of one absolute float. Fractional values that would round to 1 are capped at the nearest float below 1.

For packs using view/camera inputs, a movement exceeding 1,000 blocks between completed frames resets
retained color images and projection/view/camera history. First use, resize, world change and replacement
also start with the current camera as previous. An unfinished or invalid frame never commits camera
history or a new origin. Nonfinite cameras or coordinates outside the signed 32-bit block range leave
that frame native and resume after a valid capture; this range includes Minecraft's normal world border.
Active uniform types are checked, arrays are rejected, and custom PNG bindings cannot replace these names.

### Color formats and history

Supported formats are `R8`, `RG8`, `RGBA8`, `R16`, `RG16`, `RGBA16`, `R16F`, `RG16F`, `RGBA16F`,
`R32F`, `RG32F` and `RGBA32F` (`RGBA` aliases RGBA8). For example,
`/* const int colortex7Format = RGBA16F; */` selects half-float storage.
`const bool colortex7Clear = false;` retains the latest completed image between frames, and
`const vec4 colortex7ClearColor = vec4(0.0, 0.0, 0.0, 1.0);` changes its initialization/clear color.
All four clear components must be finite literal numbers. Both sides initialize before first use;
resizing, changing worlds or switching pipelines invalidates retained history. Clear-enabled buffers
still clear every frame.

Buffer settings are collected from expanded fragment and vertex programs, including block-comment
declarations. Each declaration must occupy its own line, be unconditional and use literal values;
conflicting settings are errors. A declaration inside a preprocessor conditional/header guard is
currently rejected. Format identifiers in live GLSL must be defined by the pack; Kernel does not
replace pack macros. Main-buffer retention/custom clearing requires terrain integration and remains
unsupported. A non-RGBA8 main format first converts the native world image to the selected format;
this does not recover HDR values or precision already lost during native world rendering. Floating-point
intermediates preserve subsequent shader calculations; final output converts back to the native target.

An unconditional block comment on its own line selects composite outputs: `/* DRAWBUFFERS:037 */`
maps output locations 0, 1 and 2 to buffers 0, 3 and 7; `/* RENDERTARGETS:3,15 */` also supports two-digit
indices. Without a declaration, locations 0–7 map to buffers 0–7. Duplicate targets, conflicting
declarations and conditional target configuration are rejected. The final pass ignores these comments.
Declared but unwritten outputs have no defined contents; packs must write every output they later read.

Version-120 fullscreen shaders have a small compatibility adapter for `varying`, `texture2D`,
`gl_Vertex`, `gl_MultiTexCoord0`, the unit-quad texture/projection transforms, `ftransform`, `gl_FragColor`
and literal `gl_FragData[0]` through `gl_FragData[7]`. Modern fragment shaders use separate `vec4` outputs
with explicit `layout(location=N)` qualifiers or the names `outColor0` through `outColor7`.
An unnumbered output defaults to location zero. Arrays, interface blocks, other output types and
macro-generated output declarations/names are rejected, including when combined with a valid output.
Conditional branches and macro definitions must balance their braces and parentheses independently.
Comment scanning is linear even for malformed input.
A missing vertex shader uses Kernel's original fullscreen triangle with a `texcoord` output.
Relative and pack-root `#include` paths are supported, with source IDs and line directives in diagnostics.
Includes retain native GLSL conditionals and header guards: the graphics driver evaluates macros and
branch expressions. Missing included files and excessive recursion emit a local `#error`, which the
driver ignores only when that branch is inactive. Guarded recursive headers can therefore compile,
while active missing files and unguarded cycles produce visible compilation errors. Missing root files,
unsafe paths, unreadable sources and resource-limit violations still fail during preparation.

Expansion is limited to 32 levels, 16 million source/expanded characters and 262,144 cached source lines.
Cached line arrays are reused during recursive expansion. Backslash/newline pairs are handled before
comments, including on older GLSL versions, while subsequent line numbers and include source IDs remain
available to the compiler. Macro-generated include filenames are not supported.
An unconditional fragment-program declaration such as `const bool colortex7MipmapEnabled = true;`
generates that buffer's mip chain immediately before the requesting pass. This request is local to each
pass; a later request regenerates the chain after intervening writes. Only active sampler inputs need
generation/storage. Mipmap-enabled inputs use trilinear minification, with linear magnification and edge
clamping; other inputs retain base-level linear sampling. Literal false disables the request for that pass.
Vertex-stage requests are rejected. These controls support `texture2DLod`/`textureLod` in fullscreen
programs within the existing GLSL adapter limits.

Kernel creates mip levels only on its owned images. When the native world image needs mipmaps, it is
first copied to an owned buffer; Minecraft's source texture and sampler settings remain unchanged.
Chains include all levels down to 1×1, with each odd dimension halved and rounded down, independently
clamped to at least one. Allocation checks account for both complete chains, detect arithmetic overflow
and enforce the device's texture-size limit. Shader operations still add rendering work; mipmaps are a
shader capability, not a general FPS optimization.

Pack-local PNG inputs can be declared in `shaders/shaders.properties`:

```properties
customTexture.lookup = textures/lookup.png
texture.composite.gaux4 = textures/pattern.png
texture.noise = textures/noise.png
```

These create `lookup`, override color-buffer-7 aliases, and provide `noisetex`, respectively. Composite
overrides also apply to `final`; writes still target the corresponding color buffers. PNGs use RGBA8,
nearest filtering and wrapping by default. An adjacent `image.png.mcmeta` can select bilinear filtering
and edge clamping with `{"texture":{"blur":true,"clamp":true}}`. Only these literal properties and
metadata fields are accepted. Names must avoid native scalar/color bindings and reserved GLSL/Kernel
prefixes. Conditional properties, conflicts, other stages, raw/resource-pack/atlas textures and animated
metadata produce explicit errors. This subset follows the documented
[custom texture conventions](https://shaders.properties/current/reference/buffers/custom_textures/).

Preparation limits each PNG to 16 MiB encoded, 64 MiB RGBA and 16,384 pixels per axis, with at most 32
bindings and 128 MiB total unique RGBA data. Canonical paths share one decoded image. PNG chunk checksums,
dimensions and the bounded decompressed scanline stream are checked before native decoding, including
[Adam7 interlacing](https://www.w3.org/TR/png-3/). Metadata is limited to 64 KiB. Decoding runs on Kernel's
IO worker using Minecraft's existing STB runtime, preserves row order and alpha, and publishes immutable
pixels only after cancellation checks. These are payload budgets; temporary decoding buffers also use memory.

Only images used by compiled programs receive GPU storage. Sampler units are assigned per pass and
shared by aliases of the same input. Custom images have no mip chain; an active color-mipmap request
on an overridden image is rejected. Allocation and upload restore Minecraft's texture bindings and
pixel-unpack state. Failed replacement, disabling and shutdown release owned textures and samplers.

Minecraft shader macros other than `MC_HAND_DEPTH`, fragment depth writes and discard-based passes
are rejected until their semantics are implemented.

Terrain/geometry programs, shadow rendering, opaque-only depth, model-view/camera and hand projection inputs,
integer/other unsupported formats, compute or geometry stages, other shader properties/options, non-PNG/resource-pack textures and broad
legacy GLSL translation are **not implemented**. Packs requiring them are rejected with a visible reason.
Popular full-world shader packs are not currently supported merely because they appear in Modrinth search.
Kernel does not silently discard those stages or count a download as successful rendering.

Compilation completes before replacing a working pipeline. A rejected or failed pack leaves the previous
one active. Resizing recreates owned intermediate targets; disabling and shutdown free GPU resources.
Render errors disable the current pipeline for the session. The choice is stored in
`config/kernel-shaders.properties`; an empty `selected=` value provides manual recovery. A startup error
leaves native rendering active and is available in the Shaders screen and game log.

The current pipeline requires OpenGL 3.3 or newer. Other graphics backends receive an explicit error.
Kernel saves and restores the GL bindings/state it changes, including each used texture/sampler unit
and each output's blend enable and color mask, so Minecraft's graphics-state caches retain
their expected values. This interoperation adds render overhead; no shader FPS/performance improvement
is claimed. Fabric metadata declares Iris incompatible because both components would own shader rendering.

## Validation

Unit tests cover bounded archive access, conditional includes/comments/continuations, path escapes, bounded recursion, duplicate
entries, oversized sources, download hashes, collisions, source preservation, Modrinth version selection,
configuration recovery, draw-buffer parsing, legacy output translation, immutable target mappings,
unsupported output declarations, literal buffer settings/conflicts, immutable format settings, byte-based
allocation accounting and rejection of unsupported pipeline stages. PNG checks cover format/interlace
combinations, oversized decompressed streams, header/chunk corruption, metadata types, binding limits,
malformed paths and bounded whitespace scanning.

```powershell
.\gradlew.bat :mod:1.21.4:runShaderSmoke :mod:26.2:runShaderSmoke
```

The GUI probe uses only original tiny shader fixtures. It checks actual driver pixels for one and two
passes, nonzero/multiple targets, legacy and modern output locations, aliases, buffer-15 feedback,
per-frame clearing, preservation of the main color through auxiliary-only passes, resize, allocation
bounds, indexed GL-state restoration and failure recovery. Additional native fixtures check all twelve
allocated formats, channel precision, normalized clamping, values outside 0–1 in float images, main-image
conversion, custom clears, history across frames, explicit reset, resize, legacy `gdepth` precision and
float-format allocation limits. Mipmap fixtures additionally check all twelve formats, checkerboard
downsampling, regeneration after pass feedback, source texture ownership, per-pass configuration,
odd/one-dimensional byte accounting, device-size rejection and sampler/state restoration. PNG fixtures
check every channel against a CPU filtering/wrapping reference, aliases and noise inputs, shared images,
missing files, cancellation before/after decoding, per-pass unit reuse, GPU lifecycle and nondefault
pixel-upload state. Repeated decode/free cycles compare all RGBA bytes, including hidden color under
transparent pixels, and ensure native cleanup preserves later image allocations.
World-input pixel fixtures distinguish frame count from world time/day and moon phase, update all five
inputs across frames, reject incorrect types/arrays, and recover from missing world data. Pure tests
cover midnight, time resets, signed custom clocks and the legacy day-counter wrap. In the real world,
the probe changes the isolated client's weather and verifies nonzero rain/thunder in the shader output.
Depth fixtures compare actual D32F input and R32F output pixels for one, three and six sources, both depth
conventions, odd/single-pixel dimensions, first-person overwrite and clear-only preservation. They verify
aliases, the native hand macro, stale/missing-frame rejection, resize, invalid types, combined memory
bounds, native texture/sampler ownership and nondefault GL state. Native frame-graph tests prove capture
ordering before late clears and transient resource release after the capture. Real-world probes read
the actual world/first-person targets and compare them with the owned snapshot and final shader pixels,
with improved transparency off/on, clouds on/off, and a third-person view that preserves world depth.
Projection tests check 65,536 transformed positions across both clip ranges and both Z directions,
matrix ownership, inverse reconstruction, requested-input subsets and history invalidation. Native
RGBA32F pixel fixtures distinguish current, inverse and previous matrices, including column order,
wrong types and stale capture rejection. Gameplay probes independently read Minecraft's uploaded world
projection and compare all 48 matrix components with encoded shader pixels, then repeat after live FOV
and portal-distortion changes. A deliberately invalid Kernel capture also verifies next-frame recovery
without changing Minecraft's native upload. GPU reads and private native-buffer access are confined to test code.
The probe then imports a uniquely named original PNG-sampling ZIP through the native drop handler,
verifies the installed bytes against that exact source,
persists activation, creates a separate flat test world,
checks the world-pass pixels, rejects an unsupported shader while retaining the working one, and disables
shaders before saving/exiting. It never opens existing user worlds. Physical GPU/OS coverage remains
limited to the available Windows/AMD system; broader pack and platform support is still required.

The native pixel fixtures also cover function-macro branch conditions, guarded recursive includes,
inactive missing files, continued directives/comments, restored `__LINE__`/`__FILE__` values, and active
include errors. GLSL condition and macro behavior is delegated to the driver according to the
[Khronos language specification](https://raw.githubusercontent.com/KhronosGroup/GLSL/main/chapters/basics.adoc).

All nine supported Minecraft targets passed these native rendering/GUI probes and a live Modrinth
browser query. The live query checks project discovery only; deterministic download tests use original
ZIP fixtures with injected transport and verify integrity/collision handling without downloading another
author's shader pack. Set `-PkernelShaderLive=true` to include live browser discovery in the GUI probe.
The native probes also cover nondefault clip/rasterization state; earlier bootstrap probes verify
same-window startup with shader integration installed. The multi-target update passed all nine expanded
pixel and gameplay/GUI probes. The final parser checks additionally exercise conditional scope changes
and a megabyte of malformed comment prefixes. Release artifacts are checked for exact game/Java
metadata, matching bundled bootstrap bytes and absence of test or third-party implementation classes.
The current adapter passed `buildAll`, 1,322 unit tests in 376 suites, 27 world
activation/conflict probes, the renderer/bootstrap checks and all nine native shader/gameplay probes.
The twelve-format pixel, precision, conversion, retained-history and mipmap checks run inside each
supported Minecraft target. Release verification confirms nine mod JARs and one matching Knot Client JAR.
The depth update passed all nine expanded GPU/gameplay probes and both endpoint window-adoption probes.
After moving capture-program compilation into pack activation and updating the scope label, the final
Java 21/25 endpoint gameplay probes and complete `buildAll` passed again. The projection update then
passed all nine expanded native pixel/gameplay probes, including live FOV/distortion and transient
invalid-frame recovery, and a complete `buildAll` with exact artifact verification. The camera update passed
all nine GPU/gameplay probes, including 27 first-person/rear/front camera-mode checks. GPU output checks
cover all view/projection matrices, exact integer camera bits, negative/far coordinates, rebasing and
temporal image/matrix reset after teleports. Both endpoint bootstrap handoffs and the complete release
build passed again with zero failed, errored or skipped unit tests. No end-to-end performance gain
is claimed for this optional rendering feature.
Legacy `texture2D` calls retain support for a sampler named `texture`, including fragment bias and
vertex sampling; native pixel checks exercise those cases on every supported target.

Buffer routing follows the documented [render-target declarations](https://shaders.properties/current/reference/constants/rendertargets/)
and [color-buffer conventions](https://shaders.properties/current/reference/buffers/colortex/), within the
explicit format/stage limits above. Buffer settings follow the documented
[formats](https://shaders.properties/current/reference/constants/buffer_format/),
[clear modes](https://shaders.properties/current/reference/constants/buffer_clear/) and
[clear colors](https://shaders.properties/current/reference/constants/buffer_clear_color/) within those limits.
Per-program generation follows the [color mipmap declarations](https://shaders.properties/current/reference/constants/colortex_mipmaps/).
The implementation and test fixtures are original Kernel code.

API references: [project search](https://docs.modrinth.com/api/operations/searchprojects/) and
[project versions](https://docs.modrinth.com/api/operations/getprojectversions/).
