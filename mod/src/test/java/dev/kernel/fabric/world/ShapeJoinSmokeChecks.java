package dev.kernel.fabric.world;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.world.phys.shapes.*;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;

public final class ShapeJoinSmokeChecks {
    private static volatile boolean sink;
    public static void run(boolean expected) throws Throwable {
        boolean applied = Arrays.stream(Shapes.class.getDeclaredMethods()).anyMatch(method -> method.getName().contains("kernel$reuseTraversal"));
        if (applied != expected || WorldSettings.active(WorldFeature.SHAPE_TRAVERSAL) != expected)
            throw new AssertionError("Shape traversal activation mismatch");
        ShapeJoinTestSupport.differential(648772, 3000, true);
        ShapeGridSmokeChecks.run(expected);
        ShapeMappedSmokeChecks.run(expected);
        if (expected && Boolean.getBoolean("kernel.shapeGridBenchmark")) ShapeGridBenchmark.run();
        customMergersKeepCallbacks();
        var merger = ShapeJoinTestSupport.identity(16);
        DiscreteVoxelShape empty = new BitSetDiscreteVoxelShape(16, 16, 16);
        BooleanOp op = BooleanOp.AND;
        var live = ShapeJoinTestSupport.LIVE;
        for (int i = 0; i < 10000; i++) sink = (boolean) live.invokeExact(merger, merger, merger, empty, empty, op);
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        for (int i = 0; i < 5000; i++) sink = (boolean) live.invokeExact(merger, merger, merger, empty, empty, op);
        long bytes = (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before) / 5000;
        if (expected && bytes > 128) throw new AssertionError("Callbacks still allocate per cell: " + bytes);
        System.out.println("Kernel shape traversal: enabled=" + expected + ", " + bytes
            + " bytes/query; native coordinate/operation parity and custom callback ownership passed.");
    }

    private static void customMergersKeepCallbacks() throws Throwable {
        var retained = new ArrayList<IndexMerger.IndexConsumer>();
        IndexMerger custom = new IdenticalMerger(DoubleArrayList.wrap(new double[2])) {
            @Override public boolean forMergedIndexes(IndexConsumer consumer) {
                retained.add(consumer);
                return super.forMergedIndexes(consumer);
            }
        };
        if (ShapeJoinTraversal.ownsCallbacks(custom)) throw new AssertionError("Custom callback ownership was claimed");
        IndexMerger nativeMerger = ShapeJoinTestSupport.identity(1);
        DiscreteVoxelShape empty = new BitSetDiscreteVoxelShape(1, 1, 1);
        BooleanOp op = BooleanOp.FALSE;
        sink = (boolean) ShapeJoinTestSupport.LIVE.invokeExact(custom, nativeMerger, nativeMerger, empty, empty, op);
        sink = (boolean) ShapeJoinTestSupport.LIVE.invokeExact(custom, nativeMerger, nativeMerger, empty, empty, op);
        if (retained.size() != 2 || retained.get(0) == retained.get(1) || !retained.get(0).merge(0, 0, 0))
            throw new AssertionError("Custom merger's escaped callbacks lost their independent inputs");
    }
}
