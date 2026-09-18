package dev.kernel.fabric.verification;

import java.lang.management.ManagementFactory;
import java.util.function.Predicate;

/**
 * Per-thread allocation measurement for the smoke probes.
 *
 * <p>A thread's allocated-bytes counter is exact, but how much a loop allocates depends on how far JIT
 * compilation has progressed: an interpreted or freshly deoptimised frame can allocate where a compiled
 * one does not. Under a parallel nine-target build the compiler competes for CPU with the other forked
 * verification JVMs, so a fixed warm-up is not always enough, and a probe that passes on an idle machine
 * can fail on a busy one.
 *
 * <p>This helper therefore repeats the whole measurement, warming again before each attempt, and accepts
 * the first result that satisfies the probe's expectation. That absorbs a cold compiler without weakening
 * the claim: an optimisation that genuinely stopped working allocates wrongly on every attempt, so the
 * expectation fails on the last one and the probe reports the measurement it actually saw.
 */
public final class AllocationProbe {
    /** How many times a measurement may be repeated before the probe reports what it measured. */
    public static final int ATTEMPTS = 4;

    @FunctionalInterface public interface Work { void run() throws Throwable; }

    private AllocationProbe() {}

    /**
     * Measures each workload in turn and returns the bytes allocated by each, in the order given.
     *
     * @param warmup work run before every attempt, to give the compiler the same chance each time
     * @param expectation the property the probe is asserting, tested against the totals
     * @return the totals from the first attempt that satisfied the expectation, or from the last attempt
     */
    public static long[] settle(Work warmup, int warmupIterations, int iterations,
                                Predicate<long[]> expectation, Work... measured) throws Exception {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("Allocation accounting unavailable on validation JVM");
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long[] totals = new long[measured.length];
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            for (int i = 0; i < warmupIterations; i++) run(warmup);
            for (int index = 0; index < measured.length; index++) {
                Work work = measured[index];
                long before = bean.getThreadAllocatedBytes(thread);
                for (int i = 0; i < iterations; i++) run(work);
                totals[index] = bean.getThreadAllocatedBytes(thread) - before;
            }
            if (expectation.test(totals)) return totals;
        }
        return totals;
    }

    /** Probe work reaches through MethodHandles, which throw Throwable; callers only handle Exception. */
    private static void run(Work work) throws Exception {
        try { work.run(); }
        catch (Exception | Error direct) { throw direct; }
        catch (Throwable other) { throw new AssertionError("Allocation probe work failed", other); }
    }
}
