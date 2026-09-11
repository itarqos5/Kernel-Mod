package dev.kernel.fabric.world;

import net.minecraft.world.phys.shapes.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Measures native callback traversal alone; coordinate merging and the Mixin adapter are excluded. */
public final class ShapeJoinBenchmark {
    private static volatile boolean sink;
    private static final int QUERIES = 8192;
    public static void main(String[] args) throws Throwable {
        var kernel = MethodHandles.lookup().findStatic(ShapeJoinTraversal.class, "test",
            MethodType.methodType(boolean.class, ShapeJoinTestSupport.PARAMETERS));
        ShapeJoinTestSupport.differential(673399, 1000, false);
        for (int size : new int[]{1, 8, 16}) {
            IndexMerger merger = ShapeJoinTestSupport.identity(size);
            DiscreteVoxelShape a = new BitSetDiscreteVoxelShape(size, size, size);
            DiscreteVoxelShape b = new BitSetDiscreteVoxelShape(size, size, size);
            for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) for (int z = 0; z < size; z++)
                (((x + y + z) & 1) == 0 ? a : b).fill(x, y, z);
            if (size == 1) b.fill(0, 0, 0);
            for (int i = 0; i < 4; i++) {
                sample(ShapeJoinTestSupport.NATIVE, merger, a, b);
                sample(kernel, merger, a, b);
            }
            long[] nativeTimes = new long[7], kernelTimes = new long[7];
            for (int i = 0; i < 7; i++) {
                if ((i & 1) == 0) {
                    nativeTimes[i] = sample(ShapeJoinTestSupport.NATIVE, merger, a, b);
                    kernelTimes[i] = sample(kernel, merger, a, b);
                } else {
                    kernelTimes[i] = sample(kernel, merger, a, b);
                    nativeTimes[i] = sample(ShapeJoinTestSupport.NATIVE, merger, a, b);
                }
            }
            Arrays.sort(nativeTimes); Arrays.sort(kernelTimes);
            System.out.printf("Shape traversal %dx%dx%d: native %.2f ns/query, Kernel %.2f ns/query; allocated native %d, Kernel %d bytes/query%n",
                size, size, size, nativeTimes[3] / (double) QUERIES, kernelTimes[3] / (double) QUERIES,
                allocation(ShapeJoinTestSupport.NATIVE, merger, a, b), allocation(kernel, merger, a, b));
        }
    }

    private static long sample(MethodHandle method, IndexMerger merger, DiscreteVoxelShape a, DiscreteVoxelShape b) throws Throwable {
        BooleanOp op = BooleanOp.AND;
        boolean result = false;
        long start = System.nanoTime();
        for (int i = 0; i < QUERIES; i++) result ^= (boolean) method.invokeExact(merger, merger, merger, a, b, op);
        long elapsed = System.nanoTime() - start;
        sink = result;
        return elapsed;
    }

    private static long allocation(MethodHandle method, IndexMerger merger, DiscreteVoxelShape a, DiscreteVoxelShape b) throws Throwable {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        sample(method, merger, a, b);
        return (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before) / QUERIES;
    }
}
