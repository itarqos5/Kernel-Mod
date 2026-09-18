package dev.kernel.fabric.shader;

import java.util.Locale;

/**
 * Whether the running graphics backend can host Kernel's shader renderer.
 *
 * <p>Kernel's pipeline is raw OpenGL, and the shader packs it reads are written against OpenGL as well:
 * the Iris/OptiFine format has no Vulkan form, so there is nothing to run on that backend even in
 * principle. 26.2 is the first target that can select a Vulkan device, so on every earlier target this
 * question has one answer.
 *
 * <p>The result is cached after the first successful query. A backend cannot change without restarting
 * the game, and the settings screen asks this on every frame it draws.
 */
public final class ShaderBackend {
    private static volatile Boolean supported;

    private ShaderBackend() {}

    /** True when shader packs can run; false on a backend that cannot host them, such as Vulkan. */
    public static boolean supported() {
        Boolean known = supported;
        if (known != null) return known;
        // Null means the graphics device does not exist yet, which must not be cached as an answer.
        Boolean result = query();
        if (result != null) supported = result;
        return result == null || result;
    }

    /** The backend name for diagnostics and the disabled-tab explanation, or an empty string. */
    public static String name() {
        //? if >=26.2 {
        try {
            return com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().backendName();
        } catch (RuntimeException unavailable) { return ""; }
        //? } else {
        /*return "OpenGL";
        *///? }
    }

    /** The backend answer, or null while the graphics device does not exist yet. */
    private static Boolean query() {
        //? if >=26.2 {
        String backend;
        try {
            backend = com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().backendName();
        } catch (RuntimeException unavailable) { return null; }
        // Name the one backend Kernel cannot host rather than allow-listing names it has not seen, so an
        // OpenGL device reporting an unfamiliar name keeps working.
        return !backend.toLowerCase(Locale.ROOT).contains("vulkan");
        //? } else {
        /*return Boolean.TRUE;
        *///? }
    }
}
