# Kernel loading window

Kernel's installed Knot Client opens an OpenGL 3.3 GLFW window before calling Fabric. It draws the
Kernel bolt, original bitmap lettering, current loading activity and a progress bar. Minecraft later
adopts that exact native window. The desktop window is not closed and replaced at the handoff.

This takes effect on the next launch after the Fabric mod installs or updates the bundled Knot Client
in a recognized official-launcher-style profile. An ordinary Fabric-only launch cannot display a mod's
window before Fabric itself loads. Unknown launcher layouts and main classes remain untouched. The
installer does not patch game or loader JARs; [installation and recovery](STARTUP_CACHES.md) describe the
content-addressed library, profile-local Java agent and one-time JSON backup.

## Progress and visual handoff

Before resources provide a measurable total, the bar is indeterminate. The agent observes actual class
definitions and counts them. Audited Fabric 0.19.3/Mixin hooks identify entrypoint construction and Mixin
validation. Unknown bytecode bypasses those optional detail hooks. No total number of startup tasks or
completion percentage is invented.

The launch thread owns GLFW initialization, window operations and event polling. A temporary painter
owns the current GL context between frames. At Minecraft's native window-creation call, the painter
stops and releases the context before the mod returns the original GLFW handle to Minecraft. Minecraft
then owns rendering, callbacks, fullscreen changes and normal window destruction. Close requests remain
on the window and are handled by Minecraft's normal close lifecycle.

During resource loading the mod draws Kernel's opaque loading visuals over the native loading frame.
The bar uses `ReloadInstance.getActualProgress()` and explicitly describes resource progress. Resource
lookups are sampled at up to 20 updates per second, avoiding per-asset reflection/string allocations.
The native completion/error callbacks still execute. Once they complete, Kernel skips the remaining
presentation fade so the game screen appears directly in the same window. Settings do not open at
startup; one-time hardware recommendations are separate from this display.

## Native-library boundaries

Fabric's classloader can load separate Java copies of LWJGL. A GLFW window pointer must never cross
between different native GLFW libraries, even if their versions look compatible. The bootstrap resolves
the library it actually loaded and selects that absolute library path through the process-local LWJGL
`org.lwjgl.glfw.libname` property. Adoption also compares the native `glfwInit` function address. The
original property is restored on bootstrap cleanup when another component has not changed it.

The early painter resolves five OpenGL functions through GLFW and uses LWJGL's explicit JNI function
calls. It does not initialize LWJGL's OpenGL module or its JVM-wide dispatch table. This leaves normal
game initialization intact even when both Java classloaders use the same LWJGL native core. No native
library or third-party implementation is bundled; Minecraft supplies its existing LWJGL dependencies.

## Recovery and limits

Use the profile JVM option `-Dkernel.loadingWindow=false` to disable the early window. Startup caches
have the separate `-Dkernel.startupCache=false` switch. Neither setting changes other Java processes.
Removing the agent/main-class customization through the saved profile backup restores ordinary Fabric
startup. Keep the launcher and game closed while restoring the profile.

Missing dependencies, failed early context creation, different native-library ownership, a missing
mod-side adoption adapter, context-sharing requests and non-OpenGL backends fall back to Minecraft's
normal window. Kernel never calls `glfwTerminate` while Minecraft may own GLFW state. A painter that
cannot release its context within the bounded handoff wait is hidden and not adopted.

OpenGL adoption is implemented for all nine game targets. It is not implemented for Vulkan or other
backends. Actual probes have run on Windows with the available AMD GPU; macOS/Linux, other drivers,
multiple displays, DPI changes, fullscreen transitions and accessibility tools need wider testing.
The first window appears before Fabric initialization; this is not a claim of instantaneous game
initialization or a measured reduction in total launch time.

## Verification

`buildAll` checks packaged artifacts and Java 21/25 agent fallback behavior. Unit tests cover audited
activity-hook bytecode, stopped/failed progress polling and loading-layout bounds. The separate
`:mod:<version>:runBootstrapSmoke` task starts an isolated client through the packaged agent and Knot
Client, checks that the window was visible before Fabric's KnotClient class definition, compares its
handle with Minecraft's, exercises settings drafts and exits normally. Test helpers and screenshots
stay in build directories and are not included in release artifacts.
