package dev.kernel.fabric.render;

import java.util.Objects;
import java.util.Queue;
import java.util.function.LongSupplier;

/**
 * Places a conservative CPU-side budget around Minecraft's render-thread chunk upload tasks.
 *
 * <p>The tasks continue to own all buffer and command-encoder operations. Kernel neither issues raw OpenGL calls
 * nor selects vendor extensions, so uploads remain on Minecraft's supported graphics abstraction for Intel, AMD,
 * NVIDIA, Apple, and software-backed drivers.</p>
 */
public final class ChunkGpuUploadScheduler {
    static final long UPLOAD_BUDGET_NANOS = 2_000_000L;
    static final int MAX_UPLOADS_PER_PASS = 32;

    private ChunkGpuUploadScheduler() {
    }

    /**
     * Drains pending uploads for a normal render pass, or completely during renderer shutdown.
     *
     * @return the number of tasks executed
     */
    public static int drain(Queue<Runnable> uploads, boolean drainCompletely) {
        return drain(uploads, drainCompletely, System::nanoTime, UPLOAD_BUDGET_NANOS, MAX_UPLOADS_PER_PASS);
    }

    static int drain(
        Queue<Runnable> uploads,
        boolean drainCompletely,
        LongSupplier nanoTime,
        long budgetNanos,
        int maxUploads
    ) {
        Objects.requireNonNull(uploads, "uploads");
        Objects.requireNonNull(nanoTime, "nanoTime");
        if (budgetNanos < 0L) {
            throw new IllegalArgumentException("budgetNanos must not be negative");
        }
        if (maxUploads < 1) {
            throw new IllegalArgumentException("maxUploads must be positive");
        }

        if (drainCompletely) {
            return drainAll(uploads);
        }

        long startedAt = nanoTime.getAsLong();
        int completed = 0;
        Runnable upload;
        while (completed < maxUploads && (upload = uploads.poll()) != null) {
            upload.run();
            completed++;

            // Always complete at least one queued upload so a slow driver cannot prevent forward progress.
            if (nanoTime.getAsLong() - startedAt >= budgetNanos) {
                break;
            }
        }
        return completed;
    }

    private static int drainAll(Queue<Runnable> uploads) {
        int completed = 0;
        Runnable upload;
        while ((upload = uploads.poll()) != null) {
            upload.run();
            completed++;
        }
        return completed;
    }
}
