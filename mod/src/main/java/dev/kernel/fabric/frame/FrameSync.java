package dev.kernel.fabric.frame;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import dev.kernel.fabric.config.KernelTranslations;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
//? if >=26.1 {
import com.mojang.blaze3d.systems.RenderSystem;
//? }

/** Native synchronized presentation plus the current monitor's cap. Original options remain owned by Minecraft. */
public final class FrameSync {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("kernel-display.properties");
    private static volatile boolean enabled = load();
    private static final UncappedEstimate ESTIMATE = new UncappedEstimate();
    private static final GpuFrameTimer GPU = new GpuFrameTimer();
    private static int refreshRate;
    private static long nextMonitorPoll, start, blocked, waitStarted, nextLabel;
    private static boolean timing;
    private static int depth;
    private static String pacedLabel = "", estimateLabel = "";
    private FrameSync() {}

    private static boolean load() {
        try { return FrameSyncConfig.load(PATH).enabled(); }
        catch (IOException | RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger("Kernel").warn("Cannot read Frame Sync settings; leaving native presentation unchanged", exception); return false;
        }
    }
    public static boolean enabled() { return enabled; }
    public static boolean vsync(boolean original) { return enabled || original; }
    public static boolean synchronizedLimit(int limit) { return enabled && refreshRate > 0 && limit >= refreshRate; }

    public static void save(boolean value) throws IOException {
        new FrameSyncConfig(value).save(PATH);
        if (enabled == value) return;
        enabled = value; timing = false; ESTIMATE.reset(); GPU.close(); nextMonitorPoll = nextLabel = 0;
        var minecraft = Minecraft.getInstance();
        //? if >=26.2 {
        minecraft.invalidateSurfaceConfiguration();
        //? } else {
        /*minecraft.getWindow().updateVsync(minecraft.options.enableVsync().get());
        *///? }
    }

    public static int limit(int original) {
        if (!enabled) return original;
        var minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return original;
        long now = System.nanoTime();
        if (now >= nextMonitorPoll) {
            nextMonitorPoll = now + 1_000_000_000L;
            int detected = minecraft.getWindow().getRefreshRate();
            refreshRate = detected > 0 && detected <= 2000 ? detected : 0;
        }
        return refreshRate > 0 ? refreshRate : original;
    }

    public static void beginFrame() {
        if (!enabled) return;
        if (timing) { depth++; return; }
        limit(0); // Menu throttling can bypass the native foreground-cap field entirely.
        timing = true; depth = 1; start = System.nanoTime(); blocked = waitStarted = 0;
        //? if >=26.2 {
        boolean openGl = "OpenGL".equals(RenderSystem.getDevice().getDeviceInfo().backendName());
        //? } elif >=26.1 {
        /*boolean openGl = "OpenGL".equals(RenderSystem.getDevice().getBackendName());
        *///? } else {
        /*boolean openGl = true;
        *///? }
        GPU.begin(openGl, ESTIMATE::record);
    }
    public static void beginWait() {
        if (!timing) return;
        GPU.endWork(); waitStarted = System.nanoTime();
    }
    public static void endWait() {
        if (timing && waitStarted != 0) { blocked += System.nanoTime() - waitStarted; waitStarted = 0; }
    }
    public static void endFrame() {
        if (!timing) return;
        if (--depth > 0) return;
        endWait(); long work = Math.max(1, System.nanoTime() - start - blocked); timing = false;
        if (!GPU.finish(work)) ESTIMATE.record(work, 0);
        long now = System.nanoTime();
        if (now >= nextLabel) {
            nextLabel = now + 250_000_000L;
            pacedLabel = "FPS-fs: " + Minecraft.getInstance().getFps();
            int estimate = ESTIMATE.fps();
            estimateLabel = estimate == 0 ? KernelTranslations.text("kernel.frame.measuring").getString()
                : "FPS: ~" + estimate + " (" + KernelTranslations.text(ESTIMATE.includesGpu() ? "kernel.frame.estimate" : "kernel.frame.cpu_estimate").getString() + ")";
        }
    }
    public static void close() { timing = false; GPU.close(); }
    public static String pacedLabel() { return pacedLabel; }
    public static String estimateLabel() { return estimateLabel; }
    public static List<String> debugLines(List<String> original) {
        if (!enabled) return original;
        var result = new ArrayList<String>(original.size() + 3);
        result.add(pacedLabel); result.add(estimateLabel); result.add(""); result.addAll(original); return result;
    }
}
