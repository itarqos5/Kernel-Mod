# Shader world stages

Kernel's shader renderer runs a pack's `deferred`, `composite` and `final` passes over the image
Minecraft itself drew. Behind `-Dkernel.worldShaders=true` it also draws world geometry with a pack's own
`gbuffers` programs, verified so far on 1.21.5; by default it does not, and a pack that ships
`gbuffers_*`, `shadow*` or `prepare*` is refused by name rather than rendered from stages Kernel does not
run. See [shader packs](SHADERS.md) for what is supported today.

This document records what remains before Iris-format packs render correctly, and the order to build it.
It is a plan, not an implementation status. Nothing here should be read as working until the
implementation sections of `AGENTS.md` and `README.md` say so.

## The renderer Kernel has to work with

The nine supported targets do not share one core-shader architecture, but they are closer than they look.
Read from the actual game jars rather than from release notes:

| Target | Core shader system |
| --- | --- |
| 1.21.4 | `CompiledShaderProgram`, linked from two `CompiledShader`s and a `VertexFormat`, with named `Uniform` fields written one at a time |
| 1.21.5 – 26.2 | `RenderPipeline`, an immutable descriptor of shader identifiers, `ShaderDefines`, bind-group layouts, colour target state and vertex formats, compiled by the graphics backend |

So one adapter covers eight of the nine targets and 1.21.4 needs its own. 26.2 additionally ships a
Vulkan backend (`VulkanRenderPipeline`); Kernel's post-processing is raw OpenGL and already refuses other
backends with an explicit error.

On 1.21.5 and later the backend compiles a pipeline two ways, and both have to be covered:

- `GpuDevice.precompilePipeline(RenderPipeline, …)`, used by `ShaderManager.apply` to build every static
  pipeline at a resource reload, is handed a source supplier bound to `CompilationCache::getShaderSource`.
- `getOrCompilePipeline`, used lazily the first time a pipeline is drawn with, uses the device's own
  `defaultShaderSource`. Minecraft constructs the device with `getShaderManager().getShader(…)` for that,
  and `ShaderManager.getShader` delegates straight to the same `compilationCache.getShaderSource`.

So `ShaderManager$CompilationCache.getShaderSource` is the one substitution point that both paths pass
through, and it is where Kernel hooks. `ShaderManager.getShader` is only a public convenience: hooking it
changes what other callers read without covering the precompile path. Its shape is identical from 1.21.5
through 26.2 — only the `ResourceLocation` to `Identifier` rename at 1.21.11 differs.

Pipelines are cached per `RenderPipeline` and shader modules per identifier, type and `ShaderDefines`
together, so one core shader is compiled once per distinct set of defines: `core/terrain` becomes three
modules per stage, shared across the five terrain pipelines. `clearPipelineCache()` empties both caches
and closes the GL programs, and `GlRenderPass.setPipeline` recompiles on the next draw, so clearing it
when a pack is adopted is sufficient to re-reach every pipeline.

Vanilla's own shader environment is not constant across those eight targets, which matters because a
substituted program has to compile in it:

- 1.21.5 declares `ProjMat` and `ModelViewMat` directly in the program that uses them.
- From 1.21.6 the matrices move behind `#moj_import <minecraft:projection.glsl>`, and by 26.x the shared
  state is in `std140` uniform blocks reached through `globals.glsl`, `chunksection.glsl` and
  `dynamictransforms.glsl`.
- Attributes move too. On 1.21.5 terrain supplies `Normal`; on 26.2 it does not, and vertex positions are
  chunk-relative, reconstructed as `Position + (ChunkPosition - CameraBlockPos) + CameraOffset`.

Kernel does not carry a table of those differences; it reads them out of the program it is replacing.
That works because `ShaderManager.loadShader` runs the GLSL preprocessor when it *loads* a shader, so the
source handed out later has every `#moj_import` already resolved. A declaration that vanilla wrote in an
included file is an ordinary declaration by the time Kernel sees it, and re-emitting it needs no
knowledge of where it came from. What Kernel supplies per version is the short prelude — which attributes
exist, which uniforms to redeclare, and how to rebuild a world position from them.

It also bounds the approach. A prelude of plain declarations cannot reproduce a `std140` uniform block,
so on the targets that moved the shared matrices into one, a substituted program would refer to names it
never declares and the driver would reject it — and a rejected pipeline draws nothing, where a refusal
leaves Minecraft drawing the world. `ShaderWorldEnvironment` therefore requires `ProjMat` and
`ModelViewMat` to be present as plain uniforms and refuses otherwise, which confines the world stage to
the targets that declare them that way until the prelude understands blocks.

Two further consequences follow from substituting source rather than pipelines, and they bound what
source substitution alone can ever do:

- The pack program inherits the vanilla pipeline's **vertex format**. It can only read attributes
  Minecraft already supplies, so `mc_Entity`, `mc_midTexCoord`, `at_tangent` and `at_midBlock` are
  unavailable until Kernel writes them during chunk meshing.
- It inherits the vanilla pipeline's **colour target state**, which declares one target. A program
  writing `gl_FragData[1]` and beyond has nowhere to write until Kernel owns the pipeline.

Compiled pipelines are cached, so changing or disabling a pack has to clear that cache.

## What is missing

### 1. Program substitution — implemented

Map each vanilla render type to the Iris program that replaces it — solid and cutout to
`gbuffers_terrain`, translucent to `gbuffers_water`, and so on for entities, block entities, the sky,
clouds, weather and the hand — and resolve the Iris fallback chain when a pack does not ship the exact
program, so `gbuffers_terrain` falls back through `gbuffers_textured_lit` and `gbuffers_textured` to
`gbuffers_basic`.

### 2. World GLSL translation — implemented

`ShaderSource` remains the fullscreen adapter, which rewrites `gl_Vertex` into a hardcoded triangle and
rejects `discard` and `gl_FragDepth` outright. `ShaderWorldTranslation` is the world counterpart, because
cutout terrain cannot render under those rules. World programs need real attribute binding for `gl_Vertex`, `gl_MultiTexCoord0`, the lightmap coordinates,
`gl_Color` and `gl_Normal`; genuine `gl_ModelViewMatrix`, `gl_ProjectionMatrix`, `gl_NormalMatrix` and
`gl_TextureMatrix` uniforms; and `ftransform()`.

### 3. Extended vertex attributes

`mc_Entity`, `mc_midTexCoord`, `at_tangent` and `at_midBlock` do not exist in Minecraft's vertex formats.
Supplying them means writing them during chunk meshing, in the same build and upload path that Kernel's
section scheduling, quad sorting and visibility work already own. This is the most invasive item here and
the one most likely to disturb existing behaviour.

### 4. Identifier maps

`block.properties`, `entity.properties` and `item.properties` map game identifiers to the numeric values
packs read from `mc_Entity.x`. These are registry-backed and have to be rebuilt on resource reload.

### 5. The shadow pass

A second full world render from the light direction into `shadowtex0`, `shadowtex1`, `shadowcolor0` and
`shadowcolor1`, with its own frustum and culling, honouring `shadowMapResolution`, `shadowDistance` and
the pack's distortion. This is effectively a second renderer, and it is where packs begin to look like
themselves.

### 6. The remaining uniforms

Kernel supplies roughly twenty-five of the format's uniforms: the projection and model-view matrices with
their inverses and previous-frame copies, the camera position variants, `eyeAltitude`, view size, frame
counters, world time and day, moon phase, rain and thunder, and `depthtex0`.

Still missing, and load-bearing for real packs: `sunPosition`, `moonPosition`, `shadowLightPosition`,
`upPosition`, `sunAngle`, `shadowAngle`, `shadowModelView` and `shadowProjection` with their inverses,
`skyColor`, `fogColor`, `fogStart`, `fogEnd`, `fogDensity`, `isEyeInWater`, `blindness`, `nightVision`,
`darknessFactor`, `screenBrightness`, `eyeBrightness` and its smoothed form, `centerDepthSmooth`,
`atlasSize`, `entityId`, `entityColor`, `heldItemId`, `heldBlockLightValue`, `wetness` and `hideGUI`.

### 7. The refused properties

`prepare` and `shadowcomp` stages, and the `shaders.properties` keys that change how a pass draws:
`blend.*`, `alphaTest.*`, `flip.*`, `size.buffer.*` and per-program sampler settings. Kernel refuses
these today rather than honouring them approximately.

### 8. Textures

The block atlas, the lightmap, the LabPBR normal and specular atlases, and a generated `noisetex`.

### 9. The 26.2 backend — decided

Shaders require the OpenGL backend. On a Vulkan device the Shaders page is closed rather than opened onto
a renderer that could never run anything listed there, and any pipeline built before the device was known
is released. See the note under stage E.

## Order of work

**Stage A — first pixels, reached.** Program substitution and world GLSL translation through
`ShaderManager$CompilationCache.getShaderSource`, on 1.21.5 and later, on the OpenGL backend. Deliberately limited to programs
that need only one colour output and only inputs the vanilla pipeline already supplies, so the pixels a
pack produces are the pixels it asked for. Packs needing more stay refused by name. It splits in two:

- **A1, program resolution — implemented.** `ShaderWorldPrograms` maps the vanilla core shaders Kernel is
  willing to substitute to the Iris program that replaces each, and resolves the Iris fallback chain so a
  pack shipping only `gbuffers_basic` still replaces every one of them while a pack shipping nothing
  usable replaces none. Only core shader names present on both 1.21.5 and 26.2 are mapped, so a version
  that renames one keeps vanilla rendering rather than drawing something the pack did not describe.
  Nothing in A1 is wired to rendering: it decides names, and it is covered by unit tests.
- **A2, translation — implemented, not yet wired.** `ShaderWorldEnvironment` reads the version, imports,
  attributes, uniforms, fragment output and world-space position expression out of the Minecraft program
  being replaced, so the prelude is correct on versions nobody has read rather than carried in a table,
  and a version whose declaration syntax changes is reported as undescribable instead of guessed at.
  `ShaderWorldTranslation` then binds the pack's legacy names to that environment: the position keeps
  whatever chunk-relative rebasing the version uses, matrices and attributes bind to their Minecraft
  names, the pack's colour sampler is renamed onto Minecraft's binding before `texture2D` becomes the
  builtin, and the fragment output is renamed to the one the version declares. It refuses rather than
  approximates — vertex attributes Kernel does not write, stages Kernel does not render, more than one
  colour output, and vertex normals on a version that no longer supplies them. Unit tested against
  original fixtures shaped like a core program; no Minecraft shader source is copied into this repository.

- **A2, substitution — implemented, opt-in, and reaching the screen.** The hook targets
  `ShaderManager$CompilationCache.getShaderSource` for the reasons given above. A decision is made for a
  whole program rather than a stage, because a vertex and fragment pair have to agree about their
  varyings, and a program Kernel cannot translate falls back to Minecraft's own source unchanged.

  Verified on 1.21.5 by the GPU probe. A pack whose `gbuffers_terrain` ignores every input and writes one
  colour produces six substituted stages — three `ShaderDefines` variants of `core/terrain` times two
  stages — and every one of the five terrain pipelines links it: `solid`, `cutout`, `cutout_mipped`,
  `translucent` and `tripwire` each log that the program does not use the samplers the pipeline declares,
  which is exactly what a constant-colour fragment program does. 22 of 25 points sampled across the frame
  are the pack's colour; the remainder are the hand, which this pack does not replace.

  Three earlier readings of this said the opposite and were all measurement faults, which is worth
  recording so the next one is recognised sooner. The probe read pixels from the tick path, where the
  presented back buffer's contents are undefined; then from `height * 3 / 4`, which is the *upper*
  quarter because `glReadPixels` measures from the bottom, and so sampled the sky; then from a camera
  whose type an earlier stage had left on third person, putting the sample inside the player's own model.
  A truncated log read during the first of these produced a confident diagnosis of a pipeline
  invalidation defect that does not exist. The probe now pins the camera, measures a grid rather than a
  pixel, and saves the frame the assertion is made about.

  A pack shipping `gbuffers_*` is still refused by default. Set `-Dkernel.worldShaders=true` to opt in;
  the shader probe exercises the world stage only under the same property. What is missing is not the
  substitution but the rest of the format — shadows, the extended attributes, the identifier maps and
  more than one colour output — and a real pack needing any of those is refused and drawn by Minecraft.
  A partly rendered pack is worse than a declined one, so this stays off until Stage C.

**Stage B — shadows.** Item 5 and its uniforms.

**Stage C — correctness.** Items 3 and 4. Without these, packs render but their materials are wrong.

**Stage D — completeness.** Items 6, 7 and 8, and Kernel-owned pipelines with multiple colour targets,
which lifts the single-output limit Stage A works within.

**Stage E — the straggler.** The 1.21.4 adapter, which is deferrable.

The 26.2 backend question is settled: the Shaders page is closed on a Vulkan device. Shader packs are
written for OpenGL and the Iris/OptiFine format has no Vulkan form, so there is nothing that could be
listed there even in principle. `ShaderBackend` reports the device's backend name on 26.2 and names the
one backend Kernel cannot host rather than allow-listing names it has not seen, so an OpenGL device
reporting an unfamiliar name keeps working. Every earlier target has no Vulkan backend to select.

## Two decisions to take deliberately

Stages B and C mean Kernel draws the world itself, which is the renderer-parity milestone `AGENTS.md`
already records as incomplete. That work should be chosen on its own terms rather than arrived at as a
side effect of shader support.

None of stages A through D can be developed without a real graphics device. The existing
`:mod:<version>:runShaderSmoke` probes are the right vehicle, and the validation currently available is a
single Windows and AMD machine, which is not a compatibility claim for anything else.

## Licensing

Iris is not bundled, copied or referenced in source. Kernel reads the pack format, which is documented
publicly at [shaders.properties](https://shaders.properties/). The implementation and its test fixtures
are original Kernel code, as required by `AGENTS.md`.
