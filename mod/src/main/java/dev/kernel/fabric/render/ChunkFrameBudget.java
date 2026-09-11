package dev.kernel.fabric.render;

import dev.kernel.fabric.config.KernelRendererSettings;
import dev.kernel.fabric.config.RendererFeature;
import net.minecraft.client.Minecraft;

/** Keeps legacy chunk upload bursts within headroom estimated from the previous render frames. */
public final class ChunkFrameBudget {
    private static final boolean ACTIVE = KernelRendererSettings.enabled(RendererFeature.CHUNK_UPLOAD);
    private static final boolean ADAPTIVE = Boolean.parseBoolean(System.getProperty("kernel.chunkUpload.adaptive", "true"));
    private static final FrameWorkClock CLOCK = new FrameWorkClock(System::nanoTime);
    private static long nextMonitorPoll;
    private static int refresh = 60;
    private ChunkFrameBudget() {}

    public static void beginFrame() {
        if (!ACTIVE) return;
        var minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        if (now >= nextMonitorPoll) {
            nextMonitorPoll = now + 1_000_000_000L;
            int detected = minecraft.getWindow().getRefreshRate();
            refresh = detected > 0 && detected <= 2000 ? detected : 60;
        }
        int nativeLimit = minecraft.getFramerateLimitTracker().getFramerateLimit();
        int target = nativeLimit > 0 ? Math.min(refresh, nativeLimit) : refresh;
        CLOCK.begin(1_000_000_000L / target);
    }
    public static void endFrame() { if (ACTIVE) CLOCK.end(); }
    public static void beginWait() { if (ACTIVE) CLOCK.beginWait(); }
    public static void endWait() { if (ACTIVE) CLOCK.endWait(); }
    public static void recordUploads(long nanos) { if (ACTIVE) CLOCK.recordUploads(nanos); }
    public static long budgetNanos() { return ACTIVE && ADAPTIVE ? CLOCK.budgetNanos() : AdaptiveUploadBudget.MAX_NANOS; }
    public static long lastWorkNanos() { return CLOCK.lastWorkNanos(); }
    public static long lastUploadNanos() { return CLOCK.lastUploadNanos(); }
    public static long lastBudgetNanos() { return ADAPTIVE ? CLOCK.lastBudgetNanos() : AdaptiveUploadBudget.MAX_NANOS; }
    public static long frames() { return CLOCK.frames(); }
    public static void reset() { CLOCK.reset(); nextMonitorPoll = 0; }
}
