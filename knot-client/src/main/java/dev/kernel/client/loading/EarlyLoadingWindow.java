package dev.kernel.client.loading;

import org.lwjgl.glfw.GLFW;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Owns GLFW on the launch thread and lends only the GL context to a temporary painter. */
public final class EarlyLoadingWindow {
    private static EarlyLoadingWindow instance;
    private final Thread owner = Thread.currentThread();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private long handle;
    private volatile int width;
    private volatile int height;
    private volatile boolean stop;
    private volatile boolean missingAdapter;
    private volatile boolean adopted;
    private Thread painter;
    private EarlyGl gl;
    private long lastPoll;
    private String previousLibrary;
    private String sharedLibrary;
    private final int[] framebufferWidth = new int[1], framebufferHeight = new int[1];

    private EarlyLoadingWindow() {}

    public static void start(String[] arguments) {
        if (instance != null || "false".equalsIgnoreCase(System.getProperty("kernel.loadingWindow"))) return;
        EarlyLoadingWindow window = new EarlyLoadingWindow();
        instance = window;
        try { window.open(arguments); }
        catch (RuntimeException | LinkageError exception) {
            System.err.println("[Kernel] Early window unavailable; continuing with Minecraft's window: " + exception);
            window.close(); instance = null;
        }
    }

    private void open(String[] arguments) {
        int requestedWidth = argument(arguments, "--width", 960), requestedHeight = argument(arguments, "--height", 540);
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        // Knot loads its own Java bindings. Pin those bindings to this exact native library:
        // a GLFWwindow pointer belongs to one library's global state, even when ABIs match.
        String library = GLFW.getLibrary().getPath();
        if (library == null || !java.nio.file.Path.of(library).isAbsolute()) {
            throw new IllegalStateException("Cannot identify the native GLFW library for safe window adoption");
        }
        previousLibrary = System.getProperty("org.lwjgl.glfw.libname");
        sharedLibrary = library;
        System.setProperty("org.lwjgl.glfw.libname", library);
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        handle = GLFW.glfwCreateWindow(requestedWidth, requestedHeight, "Kernel", 0, 0);
        if (handle == 0) throw new IllegalStateException("Cannot create an OpenGL 3.3 early window");
        long monitor = GLFW.glfwGetPrimaryMonitor();
        var mode = monitor == 0 ? null : GLFW.glfwGetVideoMode(monitor);
        if (mode != null) GLFW.glfwSetWindowPos(handle, Math.max(0, (mode.width() - requestedWidth) / 2), Math.max(0, (mode.height() - requestedHeight) / 2));
        readSize();
        GLFW.glfwMakeContextCurrent(handle);
        gl = new EarlyGl();
        GLFW.glfwSwapInterval(0);
        draw();
        GLFW.glfwShowWindow(handle);
        GLFW.glfwPollEvents();
        StartupProgress.windowVisible();
        GLFW.glfwMakeContextCurrent(0);
        painter = new Thread(this::paint, "Kernel early window painter");
        painter.setDaemon(true);
        painter.start();
        StartupProgress.start(this::poll);
        StartupProgress.stage("STARTING FABRIC");
        System.err.println("[Kernel] Early window visible before Fabric initialization; handle=" + handle);
    }

    private static int argument(String[] arguments, String key, int fallback) {
        for (int i = 0; i + 1 < arguments.length; i++) {
            if (arguments[i].equals(key)) {
                try { return Math.clamp(Integer.parseInt(arguments[i + 1]), 320, 7680); }
                catch (NumberFormatException ignored) { return fallback; }
            }
        }
        return fallback;
    }

    private void paint() {
        try {
            GLFW.glfwMakeContextCurrent(handle);
            GLFW.glfwSwapInterval(0);
            while (!stop) {
                if (width > 0 && height > 0) draw();
                try { Thread.sleep(50); }
                catch (InterruptedException ignored) { break; }
            }
        } catch (RuntimeException | LinkageError exception) {
            System.err.println("[Kernel] Early painter stopped: " + exception);
        } finally {
            try {
                GLFW.glfwMakeContextCurrent(0);
            } finally { stopped.countDown(); }
        }
    }

    private void draw() {
        int frameWidth = width, frameHeight = height;
        int[] commands = LoadingDraw.frame(frameWidth, frameHeight, -1, System.nanoTime() / 1_000_000);
        gl.draw(commands, frameHeight);
        GLFW.glfwSwapBuffers(handle);
    }

    private void readSize() {
        GLFW.glfwGetFramebufferSize(handle, framebufferWidth, framebufferHeight);
        width = framebufferWidth[0]; height = framebufferHeight[0];
    }

    private void poll() {
        if (Thread.currentThread() != owner || adopted || handle == 0) return;
        if (missingAdapter) { close(); return; }
        long now = System.nanoTime();
        if (now - lastPoll < 50_000_000L) return;
        lastPoll = now;
        GLFW.glfwPollEvents(); readSize();
        // A close request remains on the adopted window so Minecraft exits through its normal lifecycle.
    }

    public static void windowClass(boolean adapterPresent) {
        EarlyLoadingWindow window = instance;
        if (window != null && !adapterPresent && !window.adopted) { window.missingAdapter = true; window.poll(); }
    }

    /** Called from Minecraft's normal glfwCreateWindow site; zero asks it to create its own window. */
    public static long adopt(int width, int height, String title, long monitor, long share, boolean compatibleBackend, long nativeInit) {
        EarlyLoadingWindow window = instance;
        if (window == null || window.adopted || window.handle == 0) return 0;
        if (Thread.currentThread() != window.owner || share != 0 || !compatibleBackend
            || nativeInit != GLFW.getLibrary().getFunctionAddress("glfwInit")) {
            if (Thread.currentThread() == window.owner) window.close();
            return 0;
        }
        if (!window.stopPainter()) { GLFW.glfwHideWindow(window.handle); return 0; }
        long handle = window.handle;
        GLFW.glfwSetWindowTitle(handle, "Kernel | " + title);
        if (monitor != 0) GLFW.glfwSetWindowMonitor(handle, monitor, 0, 0, width, height, GLFW.GLFW_DONT_CARE);
        else GLFW.glfwSetWindowSize(handle, width, height);
        window.adopted = true;
        StartupProgress.stage("INITIALIZING MINECRAFT");
        System.err.println("[Kernel] Minecraft adopted the early window; handle=" + handle);
        return handle;
    }

    public static boolean adopted() { return instance != null && instance.adopted; }
    public static long handle() { return instance == null ? 0 : instance.handle; }
    public static int[] frame(int width, int height, double progress) { return LoadingDraw.frame(width, height, progress, System.nanoTime() / 1_000_000); }
    public static void resources() { StartupProgress.resources(); }
    public static void resource(String name) { StartupProgress.resource(name); }
    public static void finished() { StartupProgress.finish(); }

    private boolean stopPainter() {
        stop = true;
        if (painter == null) return true;
        painter.interrupt();
        try {
            if (stopped.await(5, TimeUnit.SECONDS)) return true;
            System.err.println("[Kernel] Early context did not release in time; using a separate native window.");
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        return false;
    }

    private void close() {
        StartupProgress.finish();
        if (handle != 0 && !adopted && Thread.currentThread() == owner && stopPainter()) {
            if (GLFW.glfwGetCurrentContext() == handle) {
                GLFW.glfwMakeContextCurrent(0);
            }
            GLFW.glfwDestroyWindow(handle); handle = 0;
        }
        // Fabric/Minecraft may already own GLFW's global state. Never terminate it here.
        if (sharedLibrary != null && sharedLibrary.equals(System.getProperty("org.lwjgl.glfw.libname"))) {
            if (previousLibrary == null) System.clearProperty("org.lwjgl.glfw.libname");
            else System.setProperty("org.lwjgl.glfw.libname", previousLibrary);
            sharedLibrary = null;
        }
    }

    public static void cleanup() { if (instance != null) instance.close(); }
}
