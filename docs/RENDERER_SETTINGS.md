# Renderer controls

Open **Options → Video Settings → Kernel optimizations…**. The button is added to the existing video
options list, so it may require scrolling. Kernel's screen uses Minecraft buttons and text widgets,
including keyboard navigation, focus, tooltips and narration. It lists only the optimizations available
on the current game version; native 26.x fluid, model-scratch and staged-upload behavior is not presented
as a Kernel switch.

Changes remain drafts until **Save**. **Cancel** and Escape discard the draft. **Defaults** changes the
draft to the enabled defaults; it still needs Save. The status line shows whether a restart is needed,
and each option's tooltip reports the setting running in the current process. Saving never attempts to
unapply Mixins or partially switch renderer state during a frame. A save error leaves the screen open
with a retry message and logs the reason.

## Persistence and recovery

Settings belong to the game directory's `config/kernel-renderer.properties`. The file is created only
when settings are saved. These keys default to `true`:

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

Use `false` to disable an optimization on the next launch. Each control owns its complete Mixin group:
pose accessors follow pose reuse, and task adapters, cancellation callbacks and the queue are enabled
or disabled together. Startup-cache cleanup and access to the settings screen remain enabled.

Unknown keys survive a save. Invalid boolean values disable the affected optimization and produce a
diagnostic. An unreadable or malformed file disables Kernel renderer optimizations for that launch.
Malformed files are preserved rather than silently replaced; correct the file or rename it to recover
the defaults. Writes use a temporary file in the same directory followed by an atomic replacement when
the filesystem supports it. No profile-wide or global Java options are installed by these controls.

The initial active snapshot is immutable. Editing the file while Minecraft is running takes effect
after restarting. The screen reflects this process's snapshot and its own saved drafts; it does not
watch external file edits while open. The bootstrap startup-cache switch remains the separate
`-Dkernel.startupCache=false` option described in [startup caches](STARTUP_CACHES.md).

## Verification limits

Configuration tests cover defaults, immutable drafts, round trips, unknown keys, invalid values,
malformed-file preservation, Mixin-group ownership and translation-key coverage. The aggregate build
runs a separate Fabric process for every game target with all renderer groups disabled, verifies that
the sorter and native task adapters are absent, and links the transformed video settings screen.
The enabled sorter and queue have separate process tests.

These are runtime linkage and behavior checks without a graphics device. Visual layout, narration
with a real speech engine, controller integrations and compatibility with mods replacing the video
settings screen still need gameplay validation. Mod Menu integration is not implemented.
