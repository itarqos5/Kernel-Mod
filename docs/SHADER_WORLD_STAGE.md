# Shader world stages

Kernel's shader renderer runs a pack's `deferred`, `composite` and `final` passes over the image
Minecraft itself drew. It does not draw world geometry with the pack's own programs, so a pack that
ships `gbuffers_*`, `shadow*` or `prepare*` is refused by name rather than rendered from stages Kernel
does not run. See [shader packs](SHADERS.md) for what is supported today.

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

On 1.21.5 and later the backend compiles a pipeline through
`GpuDevice.precompilePipeline(RenderPipeline, …)`, where the second argument supplies GLSL source for a
shader identifier. The source itself is resolved by `ShaderManager.getShader(identifier, type)`, whose
shape is identical from 1.21.5 through 26.2 — only the `ResourceLocation` to `Identifier` rename at
1.21.11 differs. That single method is the substitution point: when the game asks for the source of a
core shader Kernel has a pack replacement for, Kernel can return the translated pack program instead of
building and managing its own pipelines.

Vanilla's own shader environment is not constant across those eight targets, which matters because a
substituted program has to compile in it:

- 1.21.5 declares `ProjMat` and `ModelViewMat` directly in the program that uses them.
- From 1.21.6 the matrices move behind `#moj_import <minecraft:projection.glsl>`, and by 26.x the shared
  state is in `std140` uniform blocks reached through `globals.glsl`, `chunksection.glsl` and
  `dynamictransforms.glsl`.
- Attributes move too. On 1.21.5 terrain supplies `Normal`; on 26.2 it does not, and vertex positions are
  chunk-relative, reconstructed as `Position + (ChunkPosition - CameraBlockPos) + CameraOffset`.

Kernel does not have to reimplement any of that. `#moj_import` is resolved by the same preprocessor that
runs on whatever source `getShader` returns, so a translated program can open with the exact import lines
vanilla's own program for that version uses and inherit its uniform environment. What Kernel supplies per
version is the short prelude — which imports to emit, which attributes exist, and how to rebuild a world
position from them — rather than a whole uniform system.

Two further consequences follow from substituting source rather than pipelines, and they bound what
source substitution alone can ever do:

- The pack program inherits the vanilla pipeline's **vertex format**. It can only read attributes
  Minecraft already supplies, so `mc_Entity`, `mc_midTexCoord`, `at_tangent` and `at_midBlock` are
  unavailable until Kernel writes them during chunk meshing.
- It inherits the vanilla pipeline's **colour target state**, which declares one target. A program
  writing `gl_FragData[1]` and beyond has nowhere to write until Kernel owns the pipeline.

Compiled pipelines are cached, so changing or disabling a pack has to clear that cache.

## What is missing

### 1. Program substitution

Map each vanilla render type to the Iris program that replaces it — solid and cutout to
`gbuffers_terrain`, translucent to `gbuffers_water`, and so on for entities, block entities, the sky,
clouds, weather and the hand — and resolve the Iris fallback chain when a pack does not ship the exact
program, so `gbuffers_terrain` falls back through `gbuffers_textured_lit` and `gbuffers_textured` to
`gbuffers_basic`.

### 2. World GLSL translation

`ShaderSource` is a fullscreen adapter today. It rewrites `gl_Vertex` into a hardcoded triangle and
rejects `discard` and `gl_FragDepth` outright, which cutout terrain cannot render without. World
programs need real attribute binding for `gl_Vertex`, `gl_MultiTexCoord0`, the lightmap coordinates,
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

**Stage A — first pixels.** Program substitution and world GLSL translation through
`ShaderManager.getShader`, on 1.21.5 and later, on the OpenGL backend. Deliberately limited to programs
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

- **A2, substitution — implemented, opt-in, and not yet correct.** The hook targets
  `ShaderManager$CompilationCache.getShaderSource`, not `ShaderManager.getShader`: pipeline compilation is
  handed a source supplier bound to the cache, and `getShader` is only a public convenience that changes
  what callers read but not what the device compiles. Hooking the wrong one substituted nothing, which is
  what the GPU probe caught.

  On 1.21.5 the probe now shows six core shader stages compiled from a pack, so a translated program does
  compile and link inside a real Minecraft pipeline on a real driver. What it draws is still wrong: a
  `gbuffers_terrain` writing a constant green produces magenta in `colortex0`. The final pass is not at
  fault — making it output a constant produces that constant — so the defect is in the world translation
  or in how its output reaches the colour target.

  Because of that, a pack shipping `gbuffers_*` is still refused by default, exactly as before. Set
  `-Dkernel.worldShaders=true` to opt into the incomplete path; the shader probe exercises the world stage
  only under the same property. Accepting a pack and drawing it wrongly is worse than declining it with a
  reason, so this stays off until the pixels are right.

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
