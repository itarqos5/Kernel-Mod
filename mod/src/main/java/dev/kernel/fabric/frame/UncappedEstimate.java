package dev.kernel.fabric.frame;

/** A bounded work-time estimate, never a claim that unrendered frames were measured. Render-thread confined. */
public final class UncappedEstimate {
    private static final int CAPACITY = 64;
    private final long[] work = new long[CAPACITY];
    private final boolean[] gpu = new boolean[CAPACITY];
    private int cursor, count, gpuCount;
    private long sum;

    public void record(long cpuWorkNanos, long gpuWorkNanos) {
        if (cpuWorkNanos <= 0 || cpuWorkNanos > 10_000_000_000L) return;
        boolean hasGpu = gpuWorkNanos > 0 && gpuWorkNanos <= 10_000_000_000L;
        long duration = hasGpu ? Math.max(cpuWorkNanos, gpuWorkNanos) : cpuWorkNanos;
        sum -= work[cursor];
        if (gpu[cursor]) gpuCount--;
        work[cursor] = duration; gpu[cursor] = hasGpu;
        sum += duration; if (hasGpu) gpuCount++;
        cursor = (cursor + 1) % CAPACITY; count = Math.min(count + 1, CAPACITY);
    }

    public int fps() { return count < 3 || sum <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.round(1_000_000_000.0 * count / sum)); }
    public boolean includesGpu() { return count > 0 && gpuCount * 2 >= count; }
    public void reset() {
        java.util.Arrays.fill(work, 0); java.util.Arrays.fill(gpu, false);
        cursor = count = gpuCount = 0; sum = 0;
    }
}
