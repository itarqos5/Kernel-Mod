package dev.kernel.client.loading;

import java.util.concurrent.atomic.AtomicLong;

/** Real activity labels; early stages intentionally have no invented completion percentage. */
public final class StartupProgress {
    private static final AtomicLong CLASSES = new AtomicLong();
    private static volatile boolean recording;
    private static volatile String phase = "STARTING KERNEL";
    private static volatile String detail = "PREPARING THE GAME PROCESS";
    private static volatile Runnable eventPump;
    private static volatile long visibleAt;
    private static volatile long fabricAt;
    private StartupProgress() {}

    public static void start(Runnable pump) { recording = true; eventPump = pump; }
    public static void classLoaded(String name) {
        if (!recording || name == null) return;
        CLASSES.incrementAndGet();
        if (!name.startsWith("java/") && !name.startsWith("jdk/") && !name.startsWith("sun/") && !name.startsWith("dev/kernel/client/")) {
            detail = "CLASS: " + name.replace('/', '.');
        }
        pump();
    }
    public static void stage(String name) { if (recording) { phase = name; pump(); } }
    public static void mixin(String name) { if (recording) { detail = "MIXIN: " + name; pump(); } }
    public static void mod(String name) { if (recording) { phase = "INITIALIZING MODS"; detail = "MOD: " + name; pump(); } }
    public static void resource(String name) { if (recording) detail = "ASSET LOOKUP: " + name; }
    public static void resources() { recording = true; phase = "LOADING RESOURCES"; detail = "PREPARING RESOURCE PACKS"; }
    public static void finish() { recording = false; eventPump = null; }
    public static String phase() { return phase; }
    public static String detail() { return detail; }
    public static long classes() { return CLASSES.get(); }
    public static void windowVisible() { visibleAt = System.nanoTime(); }
    public static void fabricClassLoaded() { if (fabricAt == 0) fabricAt = System.nanoTime(); }
    public static boolean visibleBeforeFabric() { return visibleAt > 0 && fabricAt > visibleAt; }
    private static void pump() {
        Runnable pump = eventPump;
        if (pump != null) {
            try { pump.run(); }
            catch (RuntimeException | LinkageError exception) {
                eventPump = null;
                System.err.println("[Kernel] Early event polling disabled after an optional display failure: " + exception);
            }
        }
    }
}
