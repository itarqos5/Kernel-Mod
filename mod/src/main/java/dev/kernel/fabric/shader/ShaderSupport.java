package dev.kernel.fabric.shader;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Whether Kernel's shader hooks are actually working on the running game, and why not when they are not.
 *
 * <p>Shader packs reach further into Minecraft's renderer than anything else Kernel does, so they are
 * the most likely part to meet a version whose shape nobody has read. When that happens the game must
 * keep running: shaders are optional, and a version Kernel cannot host is a reason to close the Shaders
 * page, not to stop the player from playing. This class is what the page reads to decide that, and it
 * holds no Minecraft state so it can be consulted from a Mixin plugin before the game exists.
 *
 * <p>There are two ways a version defeats the hooks, and both are covered. A hook that cannot be applied
 * at all is caught by Mixin, because the shader mixins live in their own configuration that is not
 * required; nothing here runs, and the missing per-frame heartbeat is what reveals it. A hook that
 * applies but then meets a method or a field that has moved throws at the call, which {@link #guard}
 * turns into a disabled page and one log line rather than a crashed frame.
 */
public final class ShaderSupport {
    private static final AtomicLong FRAMES = new AtomicLong();
    private static volatile String failure = "";

    private ShaderSupport() {}

    /** True while shader packs can be hosted on this game. */
    public static boolean available() { return failure.isEmpty(); }

    /** Why shaders are unavailable, or an empty string while they are available. */
    public static String failure() { return failure; }

    /** Counted by the per-frame hook, so its absence is detectable without asking Mixin. */
    public static void observeFrame() { FRAMES.incrementAndGet(); }

    /**
     * True once the game has drawn frames without Kernel's shader hook running.
     *
     * <p>The hook sits on the renderer's own frame method, which runs on the title screen as well as in
     * a world, so by the time a player can open the settings screen it has run thousands of times. None
     * at all means the mixin did not apply, which is what a game version Kernel has not been taught
     * looks like from the outside.
     */
    public static boolean hooksMissing() { return FRAMES.get() == 0; }

    /** Records that shaders cannot run here. The first reason is kept; later ones are consequences. */
    public static void disable(String reason, Throwable cause) {
        if (!failure.isEmpty()) return;
        failure = reason == null || reason.isBlank() ? "Shaders are unavailable on this game version" : reason;
        var log = org.slf4j.LoggerFactory.getLogger("Kernel");
        String message = "Kernel shader support is off for this launch; the rest of the game is unaffected: {}";
        if (cause == null) log.warn(message, failure); else log.warn(message, failure, cause);
    }

    /**
     * Runs one piece of shader work, disabling shaders rather than letting a failure reach the game.
     *
     * <p>{@code LinkageError} is caught alongside exceptions on purpose: a method or field that a later
     * version moved arrives as {@code NoSuchMethodError} or {@code NoClassDefFoundError}, which is
     * exactly the case this exists for and is not an exception.
     */
    public static void guard(String what, Runnable work) {
        if (!failure.isEmpty()) return;
        try {
            work.run();
        } catch (Exception | LinkageError problem) {
            disable(what + ": " + problem, problem);
        }
    }
}
