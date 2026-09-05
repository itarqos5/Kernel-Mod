# Kernel Agent Guide

These instructions apply to the entire repository.

## Product intent

Kernel is intended to become an all-in-one, client-side Fabric optimization mod. Its main target is perceptual smoothness: consistent frame delivery, stronger frame-time lows, and fewer visible micro-stutters. It may later address startup and world-loading costs where a normal mod or an explicitly installed bootstrap component can do so safely.

Kernel now contains narrowly scoped allocation reductions in common renderer hot paths, but it is not a complete renderer replacement and does not yet have reproducible end-to-end performance evidence. Do not claim Sodium parity or broad FPS/frame-time gains until benchmarks support those claims.

## Architecture

Keep the two runtime layers separate:

### Fabric mod (`mod/`)

The Fabric mod runs inside Fabric Loader and owns all Minecraft-facing behavior. Future renderer, chunk, memory, frame-time, configuration, Mod Menu, and compatibility integrations belong here.

Stonecutter owns the Minecraft-version matrix. Shared source lives under `mod/src/`; generated/preprocessed version projects live under `mod/versions/`.

### Kernel Knot Client (`knot-client/`)

The Knot Client is a small, version-independent launcher layer. Its current `dev.kernel.client.KernelKnotClient` main class forwards the original arguments to Fabric Loader. Knot Client code uses the `dev.kernel.client` namespace and Gradle group. It may eventually provide an early loading window, startup measurements, and narrowly scoped pre-Fabric behavior.

Do not move ordinary Minecraft mod behavior into the Knot Client. Before Fabric and Minecraft initialize, game registries, Fabric APIs, renderer state, resources, and normal mod lifecycle objects are unavailable or unsafe.

The Knot Client must remain auditable and recoverable. Do not patch or overwrite Minecraft or Fabric Loader JARs unless the user explicitly changes that architectural decision. The current installer may only rewrite a recognized Fabric version-profile JSON after creating a one-time backup; unknown launchers and main classes must fail open without modification.

## Supported targets

The configured Minecraft targets are:

- `1.21.4`
- `1.21.5`
- `1.21.6`
- `1.21.8`
- `1.21.9`
- `1.21.10`
- `1.21.11`
- `26.1.2`
- `26.2`

Java 21 is used for 1.21.x and the Knot Client. Java 25 is used for 26.x.

Do not remove, add, or broaden supported game versions without explicit user direction. Prefer shared code plus small version-specific branches/adapters over reflection-heavy universal patches.

## Versions and artifacts

Both components currently start at `0.1.0`.

- Fabric mod version: `mod_version` in `gradle.properties`
- Knot Client version: `knot_client_version` in `gradle.properties`

The Fabric mod uses the `dev.kernel.fabric` namespace and Gradle group. The Knot Client uses `dev.kernel.client`.

Expected artifacts:

- `kernel-fabric-[mod version]+[Minecraft version].jar`
- `kernel-knot-client-[Knot Client version].jar`

Never bump either version automatically. Only change `mod_version` or `knot_client_version` when the user explicitly instructs you to bump that component. If only one component changes, do not assume the other version should change.

## Current implementation status

Implemented:

- Stonecutter 0.9.8 multi-version structure.
- Loom Back Compat configuration for official mappings across 1.21.x and 26.x.
- Fabric startup installation of the bundled Knot Client for official-launcher-style Fabric profiles.
- Strict validation of the launcher layout and existing Fabric `mainClass` before mutation.
- One-time profile backup plus atomic JSON replacement.
- Content-addressed Knot Client library paths and idempotent profile updates.
- `dev.kernel.client.KernelKnotClient` forwarding to current or legacy Fabric Knot client packages.
- Unit coverage for argument forwarding, installation, idempotency, backup, and refusal of unknown main classes.
- Aggregate `buildAll` task and release-shaped artifact collection.
- Original baked-quad upload paths for every supported target that avoid Minecraft's temporary native buffer on 1.21.4 through 1.21.10, avoid per-vertex transformed-position allocations on all supported targets, and reuse convenience-upload arrays on 1.21.x.
- Allocation-free scalar immediate position and 2D matrix transforms, plus thread-local normal-transform scratch storage, across the supported versions where those APIs exist.
- Allocation-reduced entity/model-part transforms and cube emission that reuse per-thread quaternion and normal scratch values and use scalar position transforms.
- Reusable pose-stack entries on 1.21.4, where vanilla still allocates matrix pairs on every push, and reusable temporary normal matrices for pose multiplication on every supported target.
- Allocation-free cached block-face visibility lookups on every supported target using a bounded, thread-local identity-pair cache while preserving Minecraft's original occlusion test.
- Reentrant per-thread block-quad upload scratch on 1.21.4 that removes the temporary brightness and light arrays allocated for every tessellated quad.
- Scalar weighted fluid-corner height accumulation on every supported 1.21.x target, removing the temporary two-float accumulator allocated for each calculation.
- Unit coverage for scalar 3D and 2D vertex transforms and the legacy packed-color behavior used by the optimized paths.
- Unit coverage for reusable render scratch values, pool reentrancy and thread isolation, exact lighting-array updates, bit-for-bit legacy fluid-height parity, rotation semantics, normal-matrix extraction, and block-face cache identity and eviction behavior.
- Fabric metadata that marks Sodium as incompatible, identifies `literal.uu` as the author, and includes the approved Kernel lightning icon.

Not implemented:

- Automatic game exit or relaunch messaging.
- Custom installer/helper GUI, custom title bar, taskbar integration, or approved artwork.
- Launcher layouts other than the official-launcher-style version JSON structure.
- Automatic rollback, restoration, cleanup of old content-addressed Knot Client JARs, or uninstall UI.
- Early GLFW window, progress reporting, OpenGL context transfer, or Minecraft window adoption.
- Asynchronous Mixin preparation or transformed-class caching.
- Resource-pack preparation changes or processed-resource caching.
- Startup profiler or stutter-attribution overlay.
- Complete chunk-renderer replacement, GPU submission redesign, occlusion system, renderer settings UI, or verified Sodium feature/performance parity.
- Frame-time governor, integrated-server coordination, input changes, chunk scheduling, memory optimization, or world-generation optimization.
- Mod Menu integration and user-facing settings.
- Sodium, FerriteCore, ModernFix, Lithium, C2ME, Entity Culling, or any other third-party source or bundled code.

The user approved the supplied black-and-white lightning-bolt icon. The mod includes a cleaned, high-resolution rendition at `assets/kernel/icon.png`.

## Ideas under consideration, not decisions

- An all-in-one set of renderer, memory, chunk, world-generation, and smoothness improvements.
- Expanding the thin Knot Client installed through reversible launcher metadata rather than a loader fork.
- A NeoForge-style early window whose GLFW handle is later adopted by Minecraft.
- Parallel preparation with ordered, single-threaded application for startup work.
- Strictly keyed processed-resource and startup caches.
- Frame-time attribution and cooperative work-budget coordination.

Treat every item above as unimplemented research. Check overlap, licenses, compatibility, and measurable benefit before recommending or implementing one. Kernel's current renderer work is original and Sodium is marked incompatible, but Kernel is not yet a complete Sodium-class renderer. Never copy or bundle Sodium or another mod merely to accelerate development; obtain explicit approval and satisfy its license first.

## Change reporting

Every agent that changes this repository must report these sections in its final handoff:

- **Fabric mod:** exact mod-side changes, or `No changes`.
- **Knot Client:** exact Knot Client-side changes, or `No changes`.
- **Build/docs:** build-system and documentation changes.
- **Validation:** commands run and their results.
- **Versions:** current mod and Knot Client versions, explicitly stating whether either changed.
- **Unimplemented:** any requested behavior deliberately left incomplete or blocked.

Whenever a feature, capability, architecture decision, supported platform, user workflow, or important limitation is implemented or changed, update the implementation-status sections in both this file and `README.md`. Keep those files useful as the current high-level truth for future agents.

Do not turn the README into a bug diary. Routine bug fixes and internal corrections normally belong only in Git history. Before changing an area, inspect its history with `git log --oneline --decorate --graph` and use `git log -- <path>` plus `git show <commit>` when the reason for existing code is unclear. Update the README for a bug fix only when it changes user-visible behavior, compatibility, usage, architecture, or the documented implementation status.

## Commit policy

Make atomic commits: one coherent concern per commit. Use short imperative subjects with a type prefix, for example:

- `feat: add frame-time telemetry`
- `bug: prevent stale bootstrap replacement`
- `perf: reduce chunk upload allocations`
- `chore: update Stonecutter targets`
- `build: collect Knot Client artifact`
- `docs: record bootstrap constraints`
- `test: cover cache invalidation`
- `refactor: isolate version adapters`

Do not combine unrelated Fabric mod, Knot Client, build, and documentation work merely to reduce the number of commits. Do not rewrite or squash user commits unless explicitly asked.

For every `bug:` commit, the subject must identify the actual failure being corrected rather than merely saying "fix bug". The commit body must include concise `Cause:` and `Fix:` lines explaining what was wrong and how the change corrects it. Add tests or state why a practical regression test is not possible.

## Validation

For build-system or shared-source changes, run:

```powershell
.\gradlew.bat buildAll
```

Confirm that `build/libs/` contains one correctly named mod JAR per supported Minecraft version and exactly one correctly named Knot Client JAR. Inspect `fabric.mod.json`, bundled Knot Client resources, and the Knot Client manifest when metadata or packaging changes.

For a change isolated to one component, a narrower task may be used during iteration, but the final validation should be proportional to the risk and reported honestly.

## Compatibility and safety rules

- Preserve Fabric's normal mod lifecycle and allow unrelated mods to load normally.
- Prefer public Fabric APIs; isolate unavoidable Mixins and version-specific internals.
- Fail open for optional optimization behavior when safe: disabling a Kernel optimization is better than corrupting state or preventing recovery.
- Never falsify loading progress or benchmark results.
- Never silently install global JVM options or alter every Java process on the machine.
- Make bootstrap installation reversible and retain enough state to restore the original launcher metadata.
- Avoid competing ownership of systems already replaced by another renderer or optimization mod; detect conflicts and provide clear diagnostics.
- Keep third-party source, assets, and binaries out of the repository until their licenses and the integration strategy have been explicitly approved.
