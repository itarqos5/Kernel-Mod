package dev.kernel.fabric.frame;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/** Nonblocking timestamp pairs; never nests GL_TIME_ELAPSED queries used by Minecraft or another mod. */
final class GpuFrameTimer {
    @FunctionalInterface interface Result { void accept(long cpuNanos, long gpuNanos); }
    private static final int CAPACITY = 8;
    private final int[] starts = new int[CAPACITY], ends = new int[CAPACITY];
    private final boolean[] pending = new boolean[CAPACITY];
    private final long[] cpu = new long[CAPACITY];
    private boolean initialized, unavailable;
    private int active = -1, finishing = -1;

    void begin(boolean openGl, Result result) {
        if (unavailable || !openGl) return;
        try {
            if (!initialized) {
                var capabilities = GL.getCapabilities();
                if (!capabilities.OpenGL33 && !capabilities.GL_ARB_timer_query) { unavailable = true; return; }
                for (int i = 0; i < CAPACITY; i++) { starts[i] = GL15.glGenQueries(); ends[i] = GL15.glGenQueries(); }
                initialized = true;
            }
            for (int i = 0; i < CAPACITY; i++) {
                if (pending[i] && cpu[i] > 0 && GL15.glGetQueryObjecti(ends[i], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
                    long elapsed = GL33.glGetQueryObjectui64(ends[i], GL15.GL_QUERY_RESULT) - GL33.glGetQueryObjectui64(starts[i], GL15.GL_QUERY_RESULT);
                    result.accept(cpu[i], elapsed); pending[i] = false;
                }
            }
            for (int i = 0; i < CAPACITY; i++) {
                if (!pending[i]) {
                    pending[i] = true; cpu[i] = 0; active = i;
                    GL33.glQueryCounter(starts[i], GL33.GL_TIMESTAMP); return;
                }
            }
        } catch (RuntimeException | LinkageError exception) { disable(exception); }
    }

    void endWork() {
        if (active < 0 || unavailable) return;
        try { GL33.glQueryCounter(ends[active], GL33.GL_TIMESTAMP); finishing = active; active = -1; }
        catch (RuntimeException | LinkageError exception) { disable(exception); }
    }

    boolean finish(long cpuNanos) {
        endWork();
        if (finishing < 0 || unavailable) return false;
        cpu[finishing] = cpuNanos; finishing = -1; return true;
    }

    void close() {
        try {
            for (int i = 0; i < CAPACITY; i++) {
                if (starts[i] != 0) GL15.glDeleteQueries(starts[i]);
                if (ends[i] != 0) GL15.glDeleteQueries(ends[i]);
            }
        } catch (RuntimeException | LinkageError exception) {
            org.slf4j.LoggerFactory.getLogger("Kernel").debug("GPU timing cleanup deferred to context destruction", exception);
        }
        java.util.Arrays.fill(starts, 0); java.util.Arrays.fill(ends, 0);
        initialized = false; active = finishing = -1;
        java.util.Arrays.fill(pending, false);
    }

    private void disable(Throwable exception) {
        close();
        unavailable = true; active = finishing = -1;
        org.slf4j.LoggerFactory.getLogger("Kernel").warn("GPU frame timing unavailable; Frame Sync will show a CPU-only estimate", exception);
    }
}
