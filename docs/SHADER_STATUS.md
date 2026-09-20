# Shader support: what works and what does not

The honest state of Kernel's shader support, in one page. [Shader packs](SHADERS.md) is the reference
for what is supported and how; [shader world stages](SHADER_WORLD_STAGE.md) is the plan for the rest.
This file is the summary, and it is kept blunt on purpose: the gap between "reads the Iris pack format"
and "runs the shader packs people actually install" is wide, and it is easy to write a status that
obscures it.

**The short version.** Kernel loads Iris-format packs, runs their fullscreen passes, and — behind an
opt-in property, on part of the version range — draws world geometry with their own `gbuffers` programs.
It does not render shadows. No popular pack works yet, because every popular pack needs shadows.

## Works by default

| | Notes |
| --- | --- |
| `deferred`, `composite`, `final` passes | Ordered, with up to sixteen colour buffers and eight simultaneous outputs |
| Pack ingestion | Options in the `#define` and `const //[a b c]` syntax, `shaders.properties` screens and sliders, `world0`/`world-1`/`world1` dimension folders |
| Identity maps | `block.properties`, `entity.properties`, `item.properties`, resolved against the live registries |
| Pack textures | Pack-local PNG inputs, named samplers, noise textures, filtering and wrapping metadata |
| Installation | Modrinth browsing and install, ZIP drag and drop, verified downloads, persistent selection, recovery from a failed pack |
| Uniforms | Roughly thirty, including the projection, model-view and camera families, world time and weather, `depthtex0`, and the sun and moon positions |

A pack shipping `gbuffers_*`, `shadow*` or `prepare*` is **refused by name** unless the world stage is
opted into. That is deliberate: accepting a pack and drawing it wrongly is worse than declining it with
a reason.

## Works behind `-Dkernel.worldShaders=true`

World geometry drawn by the pack's own `gbuffers` programs, on **1.21.5 through 1.21.10**, on OpenGL.

- Each Minecraft core shader is mapped to the Iris program that replaces it, resolving the Iris fallback
  chain, and the translated program is returned where the game would have compiled its own.
- The environment a substituted program compiles in is read out of the Minecraft program being replaced,
  not assumed per version.
- Chunk meshing writes `mc_Entity` and `at_midBlock`, so a program can tell which block a vertex belongs
  to and where that block's centre is.
- A program Kernel cannot translate keeps Minecraft's own rendering rather than being approximated.

Verified on 1.21.5 by GPU probe: a pack that paints only where the block identity matches the one its
own `block.properties` declares covers 23 of 25 points sampled across the frame.

This is off by default because it is a part of the format, not the format. See the next section for what
a real pack would still be missing.

## Not implemented

| Missing | Why it matters |
| --- | --- |
| **The shadow pass** | A second full world render from the light direction into `shadowtex0`/`shadowtex1`. This is the single biggest gap and the one every popular pack depends on. |
| `shadowModelView`, `shadowProjection` | Describe a shadow map that is not rendered. Supplying them would light a pack from a texture that does not exist. |
| `mc_midTexCoord`, `at_tangent` | Drive normal and parallax mapping against LabPBR atlases Kernel does not build. |
| LabPBR normal and specular atlases | The partner of the two attributes above. |
| More than one colour output | A substituted program inherits Minecraft's pipeline, which declares one target. Lifting this needs Kernel-owned pipelines. |
| `prepare` and `shadowcomp` stages | Refused by name. |
| `blend.*`, `alphaTest.*`, `flip.*`, `size.buffer.*` | Properties that change how a pass draws; refused rather than honoured approximately. |
| Remaining uniforms | `isEyeInWater`, `blindness`, `nightVision`, `eyeBrightness`, `centerDepthSmooth`, `atlasSize`, `entityId`, `entityColor`, `heldItemId`, `wetness`, `hideGUI` and others. |
| 1.21.4 | Resolves core shaders through a different program system; no world stage at all. |
| 1.21.11, 26.1.2, 26.2 | Declare their shared matrices in a `std140` uniform block, which a prelude of plain declarations cannot reproduce. World programs are refused there rather than compiled against names they never declare. |
| Vulkan on 26.2 | The Shaders page is closed. The Iris format has no Vulkan form, so nothing could be listed there even in principle. |

### What this means for a real pack

BSL, Complementary and their like need shadows **and** the extended attributes **and** multiple colour
outputs. All three. So the answer to "does a popular shader pack work in Kernel" is **no**, and stays no
until the shadow pass and Kernel-owned pipelines land. What exists is the path to that, with the parts
built so far verified rather than assumed.

## Order of the remaining work

1. **The shadow pass.** Shadow framebuffer at `shadowMapResolution`, light-space matrices honouring
   `shadowDistance`, the terrain meshes drawn a second time with the pack's `shadow` programs, and
   `shadowtex0`/`shadowtex1` bound as samplers. Then `shadowModelView` and `shadowProjection` follow.
2. **Kernel-owned pipelines.** Lifts the single-colour-output limit that source substitution works
   within, and is what the format's `gbuffers` model actually assumes.
3. **Textures.** The block atlas, lightmap and LabPBR atlases, which unlock `mc_midTexCoord` and
   `at_tangent`.
4. **The remaining uniforms and the refused properties.**
5. **Block-aware prelude emission**, which brings 1.21.11 and 26.x back into the world stage.
6. **The 1.21.4 adapter**, which is deferrable.

Items 1 and 2 mean Kernel draws the world itself, which is the renderer-parity milestone `AGENTS.md`
records as incomplete. Shader support and that milestone converge there; it is worth choosing on its own
terms rather than arriving at as a side effect.

## How far the verification goes

- The GPU probe (`:mod:<version>:runShaderSmoke`) covers the settings screen, import, selection, real
  world rendering, failed-pack recovery and disabling. With `-PkernelWorldShaders=true` it also covers
  the world stage and the identity maps.
- All of it runs on **one Windows machine with an AMD GPU**. That is not a compatibility claim for any
  other driver, vendor or operating system.
- The world stage is exercised on 1.21.5. The other targets in its range compile and carry the same
  code, but have not been run.
- Pack parsing, translation, identity rules and the celestial maths are covered by unit tests that need
  no graphics device.

## Licensing

Iris is not bundled, copied or referenced in source. Kernel reads the pack format, which is documented
publicly at [shaders.properties](https://shaders.properties/). The implementation and its fixtures are
original Kernel code. The mod is `LGPL-3.0-only`; `COPYING` and `COPYING.LESSER` still have to be added
from gnu.org, because LGPL-3.0 is written as additional permissions on top of GPL-3.0 and both texts are
required.
