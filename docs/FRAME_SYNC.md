# Frame Sync

Frame Sync is enabled by default and can be changed in **Kernel → Video → Frame Sync**.
Apply and Done save `frame_sync` in the game directory's `config/kernel-display.properties`;
Cancel discards the draft. An unreadable configuration disables the feature for that launch.
Unknown properties are preserved. Set `frame_sync=false` to recover without opening the GUI.

Kernel requests Minecraft's synchronized presentation and uses the current monitor's refresh rate
as the foreground FPS limit. It polls the monitor once per second, including after moving the window.
The game's minimized, menu and AFK throttles remain in control. When synchronized presentation already
paces the foreground frame, Kernel omits the redundant CPU limiter; lower idle caps still run it.
Saved Minecraft FPS and VSync preferences are retained and resume when Frame Sync is disabled.
Those two controls are disabled in Kernel's screen while the Frame Sync draft is on.

This is conventional synchronized presentation, not a guarantee of maximum refresh-rate throughput,
adaptive sync, or lower input latency. GPU load, driver overrides, the compositor and the monitor can
affect delivery. Kernel does not change the display mode or global driver settings. An unknown monitor
rate keeps the native cap. Native presentation still controls graphics-backend support.

## Counters

- **FPS-fs** is Minecraft's measured FPS, with its normal rolling update interval.
- **FPS: ~… (est.)** estimates uncapped throughput from a bounded history of render-frame work.
  It excludes presentation and deliberate limiter waits and uses the larger of CPU and GPU duration,
  since those can overlap. GPU duration uses nonblocking OpenGL timestamp pairs. Unsupported backends
  or unavailable timer queries use an explicitly labeled **CPU est.** instead.

The second value is an estimate, not a measurement of frames that were never rendered. Driver queues,
power management, simulation work and measurement overhead can make it differ from an actual uncapped
run. No benchmark or FPS-improvement claim follows from it. Query storage is bounded; pending results
are never waited on. These queries do not nest Minecraft's own elapsed-time query.

The counters appear at the top left during gameplay, respect the hidden HUD, and become the first
two lines of the left F3 panel while debug information is visible. They are absent when Frame Sync
is disabled. Turning the feature off releases its GPU queries.

## Validation

`runFrameSyncSmoke` is available on every version project, for example:

```powershell
.\gradlew.bat :mod:26.2:runFrameSyncSmoke
```

It runs in that target's isolated `build/frame-sync-smoke-game` directory, checks native presentation,
monitor cap, original-option restoration, Apply/Done/Cancel and persistence, creates a new flat world,
captures the normal and F3 HUD, and saves/exits normally. It never opens an existing user world.
Controls also report native VSync-only and uncapped FPS; these short observations are not benchmarks.
The helper mod is excluded from release artifacts. Unit tests cover configuration recovery and bounded
estimate behavior; real Fabric smoke tests check the version-specific Mixin targets.

Only the available Windows/AMD machine has been exercised interactively. Multi-monitor transitions,
adaptive-refresh displays, other graphics backends and the wider OS/driver matrix require testing.
