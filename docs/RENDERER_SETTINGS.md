# Kernel video settings

The lightning button immediately left of **Options** opens Kernel from the title and pause menus.
The existing **Options → Video Settings → Kernel video settings** entry is also available. The
screen stays closed during startup. It uses the approved Kernel icon, translucent dark panels and
white selection/hover overlays, with Video, Graphics, Optimizations and Other tabs.

Video controls include fullscreen display modes (resolution and refresh rate together), fullscreen,
Vsync, framerate, render/simulation distance and entity distance. Exclusive fullscreen uses Minecraft's
native setting on 26.2. Graphics controls include lighting, clouds, particles, shadows, mipmaps and
brightness, plus the native fast/fancy quality setting before 1.21.11 or detailed leaves on newer
versions. Other controls include field of view, GUI scale, view bobbing and screen effects. Unsupported
features are not invented: for example, Kernel does not add borderless fullscreen to older games.
Minecraft's original video screen remains available for settings not exposed here.

Changes are drafts until **Apply** or **Done**. Apply keeps the screen open; Done applies and returns.
**Cancel** and Escape discard only changes since the last Apply. Native video settings use Minecraft's
option callbacks and persistence. Mipmap changes request the native texture reload; GUI-scale and
fullscreen changes use the game's window/GUI lifecycle. Optimization switches take effect on the
next launch, because saving does not unapply Mixins during a frame. Each optimization's tooltip reports
its current active state. Save errors keep the screen open and log the reason.

Pages adapt to the GUI height and can be changed with their buttons or the mouse wheel. Native button
and slider input retain keyboard focus and narration. Text has English translation fallbacks and the
icon is registered as a dynamic texture, so the screen works with bare Fabric Loader without Fabric
API resource-pack registration. Other resource packs can override translations.

## Automatic starting point

After the game initializes its options and graphics device, Kernel applies a conservative hardware
starting point once per game directory. It reads logical CPU count, the current JVM's maximum heap
and the active OpenGL renderer string. It does not launch a benchmark, infer VRAM from a name, tune
global JVM flags or change the screen resolution. This is a heuristic starting point, not a measured
optimal configuration or a performance guarantee.

- Software rendering, fewer than four logical CPUs, or less than 2 GiB maximum heap: 6 render chunks,
  5 simulation chunks, fast clouds and decreased particles.
- Recognized discrete GPU, at least eight logical CPUs and at least 4 GiB maximum heap: 12 render
  chunks, 8 simulation chunks, fancy clouds and all particles.
- Other hardware, including unknown/integrated GPUs: 8 render chunks, 5 simulation chunks, fast clouds
  and decreased particles.

Before changing settings, Kernel saves existing `options.txt` as
`config/kernel-options.before-recommendations.txt`, without replacing an existing backup. It creates
`config/kernel-hardware.properties` before applying changes to prevent repeated application after a
failed launch. Once that marker exists, subsequent launches preserve manual choices. A failure is
logged and loading continues. To prevent first-run application manually, create the marker file before
launching. To restore the backup, close the game and copy it over `options.txt`, leaving the marker in
place. Deleting the marker permits a new one-time application.

**Recommended** puts those four recommended video values into the current draft; Apply or Done saves
them. It does not reset unrelated options or disabled optimization switches. The tooltip explains the
CPU/heap inputs and recommended distances. The Other tab identifies the active GPU.

## Optimization persistence and recovery

Optimization settings belong to the game directory's `config/kernel-renderer.properties`. This file
is created when the screen saves settings. All ten feature keys default to `true`:

```properties
vertex=true
pose=true
model=true
block_face=true
block_model=true
fluid=true
chunk_upload=true
chunk_queue=true
visibility=true
quad_sorting=true
```

Use `false` to disable a feature at next launch. Each switch owns its entire Mixin group, including
accessors and cancellation adapters. Only features supported by the running game are listed. Startup
cache cleanup and access to the settings screen remain enabled. The bootstrap cache switch remains
`-Dkernel.startupCache=false`, documented in [startup caches](STARTUP_CACHES.md).

Unknown keys survive saves. Invalid booleans disable the affected feature; unreadable or malformed
files disable Kernel renderer optimizations for that launch and log diagnostics. Malformed files are
preserved: correct or rename them to recover defaults. Writes use a temporary file and an atomic
replacement where supported. The active snapshot is immutable and external edits are not watched
while the screen is open.

## Verification

Unit tests cover configuration, draft ownership, unchanged values, heuristic boundaries, translation
fallbacks and the version-specific native background lifecycle. `buildAll` also runs real Fabric/Mixin
process checks with renderer groups enabled and disabled for every supported version.

The separate `:mod:<version>:runGuiSmoke` development task starts an isolated client, interacts with
the menu icon and settings, checks save/cancel and one-time recommendation behavior, captures the title
and settings frames, then exits. Its helper mod and automatic interaction are never included in release
JARs. Test files live only under that version project's `build/gui-smoke-game` directory. The task sets
Minecraft's accessibility onboarding as already completed in that isolated directory to reach the title
menu. It does not change onboarding in ordinary game installations.

Speech-engine output, controller integrations, third-party menu replacements and the full hardware
compatibility matrix still require further testing. Mod Menu integration is not implemented. The
separate pre-Fabric loading window and native-window adoption remain planned; this settings screen
neither implements nor replaces that window.
