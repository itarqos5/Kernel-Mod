package dev.kernel.fabric.bootstrap;

import java.lang.reflect.Method;
import org.slf4j.LoggerFactory;

/** A normal mod-only launch has no bootstrap dependency. Resolve the optional owner once. */
public final class StartupWindowBridge {
    private static final Api API = find();
    private static volatile boolean adopted;
    private static volatile boolean failed;
    private static volatile boolean resources;
    private static final java.util.concurrent.atomic.AtomicLong NEXT_ASSET = new java.util.concurrent.atomic.AtomicLong();
    private record Api(Method adopt, Method frame, Method resources, Method resource, Method finished) {}
    private StartupWindowBridge() {}

    private static Api find() {
        try {
            Class<?> owner = Class.forName("dev.kernel.client.loading.EarlyLoadingWindow", false, ClassLoader.getSystemClassLoader());
            return new Api(owner.getMethod("adopt", int.class, int.class, String.class, long.class, long.class, boolean.class, long.class),
                owner.getMethod("frame", int.class, int.class, double.class), owner.getMethod("resources"),
                owner.getMethod("resource", String.class), owner.getMethod("finished"));
        } catch (ClassNotFoundException ignored) { return null; }
        catch (ReflectiveOperationException | LinkageError exception) {
            LoggerFactory.getLogger("Kernel").warn("Kernel loading-window bridge is unavailable", exception); return null;
        }
    }

    public static long adopt(int width, int height, String title, long monitor, long share, boolean compatibleBackend) {
        if (API == null || failed) return 0;
        try {
            long nativeInit = org.lwjgl.glfw.GLFW.getLibrary().getFunctionAddress("glfwInit");
            long handle = (long) API.adopt.invoke(null, width, height, title, monitor, share, compatibleBackend, nativeInit);
            adopted = handle != 0; return handle;
        } catch (ReflectiveOperationException | LinkageError exception) { fail(exception); return 0; }
    }
    public static boolean adopted() { return adopted && !failed; }
    public static void beginResources() {
        if (!adopted()) return;
        try { API.resources.invoke(null); resources = true; }
        catch (ReflectiveOperationException | LinkageError exception) { fail(exception); }
    }
    public static void asset(Object identifier) {
        if (!resources || !adopted()) return;
        long now = System.nanoTime(), next = NEXT_ASSET.get();
        if (now < next || !NEXT_ASSET.compareAndSet(next, now + 50_000_000L)) return;
        try { API.resource.invoke(null, identifier.toString()); }
        catch (ReflectiveOperationException | LinkageError exception) { fail(exception); }
    }
    public static int[] frame(int width, int height, double progress) {
        if (!adopted()) return null;
        try { return (int[]) API.frame.invoke(null, width, height, progress); }
        catch (ReflectiveOperationException | LinkageError exception) { fail(exception); return null; }
    }
    public static void finish() {
        resources = false;
        if (!adopted()) return;
        try { API.finished.invoke(null); }
        catch (ReflectiveOperationException | LinkageError exception) { fail(exception); }
    }
    private static void fail(Throwable exception) {
        if (!failed) LoggerFactory.getLogger("Kernel").warn("Kernel loading display failed; retaining Minecraft's normal loading lifecycle", exception);
        failed = true; resources = false;
    }
}
