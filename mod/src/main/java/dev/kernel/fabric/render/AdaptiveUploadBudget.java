package dev.kernel.fabric.render;

/** Uses previous non-upload CPU work to reserve frame headroom; one native task must still make progress. */
public final class AdaptiveUploadBudget {
    public static final long MIN_NANOS = 250_000L;
    public static final long MAX_NANOS = 2_000_000L;
    private static final long RECOVERY_STEP = 125_000L;
    private double baseline;
    private long budget = MAX_NANOS;
    private boolean initialized;

    public long nanos() { return budget; }
    public void reset() { initialized = false; baseline = 0; budget = MAX_NANOS; }
    public void recordFrame(long workNanos, long uploadsNanos, long targetNanos) {
        if (workNanos <= 0 || uploadsNanos < 0 || targetNanos <= 0 || targetNanos > 1_000_000_000L) return;
        long nonUpload = Math.max(0, workNanos - Math.min(workNanos, uploadsNanos));
        double sample = Math.min(nonUpload, 250_000_000L);
        baseline = !initialized || sample > baseline ? sample : baseline + (sample - baseline) / 16.0;
        initialized = true;
        long reserve = Math.max(500_000L, targetNanos / 8);
        long available = (long) Math.max(0, targetNanos - baseline - reserve);
        long wanted = Math.max(MIN_NANOS, Math.min(MAX_NANOS, available));
        budget = Math.min(wanted, budget + RECOVERY_STEP);
    }
}
