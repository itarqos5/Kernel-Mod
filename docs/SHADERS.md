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

This is an original, limited **color post-processing** renderer, not Iris/OptiFine shader compatibility.
Kernel currently supports up to 16 ordered `composite`, `composite1` through `composite99`, and `final`
passes, with up to eight simultaneous outputs mapped to sixteen logical color buffers. The world image,
including the hand, starts in `colortex0` and is processed before the HUD. Each pass samples the current
images and writes separate alternate images, then flips its declared targets. Passes that only write
auxiliary buffers preserve the displayed color. `final` always writes the displayed color image.
Linear filtering and edge clamping are supplied by an owned sampler. Intermediates use RGBA8 at the
native window resolution; required buffer pairs are allocated lazily within a 512 MiB combined budget.
There is no automatic shader download or activation.

Supported uniforms are scalar `viewWidth`, `viewHeight`, `aspectRatio`, `frameTime`, `frameTimeCounter`
(seconds modulo 3600), integer `frameCounter` (modulo 720720), and `colortex0` through `colortex15`.
Legacy aliases are `gcolor`/`texture` (0), `gnormal` (2), `composite` (3), and `gaux1` through `gaux4` (4–7).
`gdepth` remains unsupported because its legacy precision upgrade is not implemented. Buffer 1 starts
white each frame; auxiliary buffers 2–15 start transparent black. A buffer's previous-frame contents
are not retained. These buffers do not yet contain terrain normals, material data or depth automatically.

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
Buffer-format/mipmap/clear directives, Minecraft shader macros, fragment depth writes and discard-based
passes are rejected until their semantics are implemented.

Terrain/geometry programs, shadow rendering, depth-based effects, custom color formats, compute or
geometry stages, shader properties/options, custom textures and broad
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
unsupported output declarations and rejection of unsupported pipeline stages.

```powershell
.\gradlew.bat :mod:1.21.4:runShaderSmoke :mod:26.2:runShaderSmoke
```

The GUI probe uses only original tiny shader fixtures. It checks actual driver pixels for one and two
passes, nonzero/multiple targets, legacy and modern output locations, aliases, buffer-15 feedback,
per-frame clearing, preservation of the main color through auxiliary-only passes, resize, allocation
bounds, indexed GL-state restoration and failure recovery. It then
imports a ZIP through the native drop handler, persists activation, creates a separate flat test world,
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
Final validation passed `buildAll`, 1,022 unit tests in 259 suites, 27 world activation/conflict probes,
the renderer/bootstrap checks, and repeated native shader/gameplay probes on 1.21.4 and 26.2.

Buffer routing follows the documented [render-target declarations](https://shaders.properties/current/reference/constants/rendertargets/)
and [color-buffer conventions](https://shaders.properties/current/reference/buffers/colortex/), within the
explicit format/stage limits above. The implementation and test fixtures are original Kernel code.

API references: [project search](https://docs.modrinth.com/api/operations/searchprojects/) and
[project versions](https://docs.modrinth.com/api/operations/getprojectversions/).
