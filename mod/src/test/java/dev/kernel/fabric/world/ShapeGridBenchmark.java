package dev.kernel.fabric.world;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import net.minecraft.world.phys.shapes.*;

/** Native-vs-live traversal measurements with varying objects to avoid an invariant query loop. */
public final class ShapeGridBenchmark {
    private record Query(IndexMerger x, IndexMerger y, IndexMerger z, DiscreteVoxelShape a, DiscreteVoxelShape b) {}
    private static final int QUERIES = 4096;
    private static volatile boolean sink;
    private ShapeGridBenchmark() {}

    public static void run() throws Throwable {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        for (int size : new int[]{1, 8, 16}) for (String mode : new String[]{"disjoint", "last", "first", "unaligned", "unaligned-first", "indirect", "indirect-first"}) {
            if (size == 1 && mode.startsWith("unaligned")) continue;
            var queries = queries(size, mode);
            long warmUntil = System.nanoTime() + 750_000_000L;
            do { batch(queries, false); batch(queries, true); } while (System.nanoTime() < warmUntil);
            long[] nativeTimes = new long[7], liveTimes = new long[7];
            for (int sample = 0; sample < 7; sample++) {
                if ((sample & 1) == 0) { nativeTimes[sample] = batch(queries, false); liveTimes[sample] = batch(queries, true); }
                else { liveTimes[sample] = batch(queries, true); nativeTimes[sample] = batch(queries, false); }
            }
            Arrays.sort(nativeTimes); Arrays.sort(liveTimes);
            long thread = Thread.currentThread().threadId();
            long before = bean.getThreadAllocatedBytes(thread); batch(queries, false);
            long nativeBytes = (bean.getThreadAllocatedBytes(thread) - before) / QUERIES;
            before = bean.getThreadAllocatedBytes(thread); batch(queries, true);
            long liveBytes = (bean.getThreadAllocatedBytes(thread) - before) / QUERIES;
            System.out.printf("Kernel shape grid benchmark %d^3 %s: native %.2f ns / %d B, live %.2f ns / %d B per query%n",
                size, mode, nativeTimes[3] / (double) QUERIES, nativeBytes, liveTimes[3] / (double) QUERIES, liveBytes);
        }
    }

    private static Query[] queries(int size, String mode) throws Exception {
        var result = new Query[128];
        for (int sample = 0; sample < result.length; sample++) {
            int firstX = mode.startsWith("unaligned") ? size / 2 : size;
            var first = new BitSetDiscreteVoxelShape(firstX, size, size);
            var second = new BitSetDiscreteVoxelShape(size, size, size);
            for (int x = 0; x < firstX; x++) for (int y = 0; y < size; y++) for (int z = 0; z < size; z++)
                if (((y + z + sample) & 1) == 0) first.fill(x, y, z);
            for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) for (int z = 0; z < size; z++)
                if (((y + z + sample) & 1) != 0) second.fill(x, y, z);
            if (mode.equals("first") || mode.endsWith("-first")) { first.fill(0, 0, 0); second.fill(0, 0, 0); }
            if (mode.equals("last")) { first.fill(firstX - 1, size - 1, size - 1); second.fill(size - 1, size - 1, size - 1); }
            IndexMerger xAxis = mode.startsWith("indirect")
                ? new IndirectMerger(new CubePointRange(size), new CubePointRange(size), false, false)
                : ShapeGridSmokeChecks.cube(firstX, size);
            result[sample] = new Query(xAxis, ShapeGridSmokeChecks.cube(size, size),
                new IdenticalMerger(new CubePointRange(size)), first, second);
        }
        return result;
    }

    private static long batch(Query[] queries, boolean live) throws Throwable {
        long start = System.nanoTime(); boolean value = false;
        BooleanOp operation = BooleanOp.AND;
        for (int index = 0; index < QUERIES; index++) {
            var query = queries[(index * 73) & 127];
            value ^= live ? (boolean) ShapeJoinTestSupport.LIVE.invokeExact(query.x, query.y, query.z, query.a, query.b, operation)
                : (boolean) ShapeJoinTestSupport.NATIVE.invokeExact(query.x, query.y, query.z, query.a, query.b, operation);
        }
        sink = value; return System.nanoTime() - start;
    }
}
