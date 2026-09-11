package dev.kernel.fabric.render;

import java.util.function.LongSupplier;

/** Render-thread CPU accounting with nested-frame/wait support. Presentation and limiter sleep are excluded. */
public final class FrameWorkClock {
    private final LongSupplier clock;
    private final AdaptiveUploadBudget budget = new AdaptiveUploadBudget();
    private int depth, waitDepth;
    private long started, waitStarted, blocked, uploads, target;
    private long lastWork, lastUploads, lastBudget, frames;

    public FrameWorkClock(LongSupplier clock) { this.clock = clock; }
    public void begin(long targetNanos) {
        if (depth++ != 0) return;
        target = targetNanos; started = clock.getAsLong(); blocked = uploads = 0; waitDepth = 0; lastBudget = budget.nanos();
    }
    public void beginWait() {
        if (depth > 0 && waitDepth++ == 0) waitStarted = clock.getAsLong();
    }
    public void endWait() {
        if (depth > 0 && waitDepth > 0 && --waitDepth == 0) blocked += Math.max(0, clock.getAsLong() - waitStarted);
    }
    public void recordUploads(long duration) { if (depth > 0 && duration > 0) uploads += duration; }
    public void end() {
        if (depth == 0 || --depth > 0) return;
        long now = clock.getAsLong();
        if (waitDepth > 0) blocked += Math.max(0, now - waitStarted);
        waitDepth = 0;
        lastWork = Math.max(1, now - started - blocked); lastUploads = Math.min(lastWork, uploads); frames++;
        budget.recordFrame(lastWork, lastUploads, target);
    }
    public long budgetNanos() { return depth > 0 ? budget.nanos() : AdaptiveUploadBudget.MAX_NANOS; }
    public long lastWorkNanos() { return lastWork; }
    public long lastUploadNanos() { return lastUploads; }
    public long lastBudgetNanos() { return lastBudget; }
    public long frames() { return frames; }
    public void reset() { depth = waitDepth = 0; lastWork = lastUploads = lastBudget = frames = 0; budget.reset(); }
}
