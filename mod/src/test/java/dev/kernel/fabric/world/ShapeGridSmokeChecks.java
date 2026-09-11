package dev.kernel.fabric.world;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.phys.shapes.*;

/** Differential checks against the target JAR's original traversal under real Fabric transformations. */
public final class ShapeGridSmokeChecks {
    public static void run(boolean enabled) throws Throwable {
        Object shape = new BitSetDiscreteVoxelShape(1, 1, 1);
        if ((shape instanceof ShapeStorageAccess) != enabled
            || (cube(1, 1) instanceof CubeMergerAccess) != enabled) throw new AssertionError("Shape accessor activation mismatch");
        differential(847692, 1536, enabled);
        fallbacks(enabled);
        var failure = new AtomicReference<Throwable>();
        var workers = new Thread[4];
        for (int index = 0; index < workers.length; index++) {
            int seed = 146290 + index;
            workers[index] = new Thread(() -> {
                try { differential(seed, 128, enabled); }
                catch (Throwable thrown) { failure.compareAndSet(null, thrown); }
            });
            workers[index].start();
        }
        for (var worker : workers) worker.join();
        if (failure.get() != null) throw failure.get();
        System.out.println("Kernel shape grids: enabled=" + enabled
            + "; native AND parity, mutation, coordinate/storage/operation fallbacks and concurrency passed.");
    }

    private static void differential(int seed, int count, boolean enabled) throws Throwable {
        var random = new Random(seed);
        for (int iteration = 0; iteration < count; iteration++) {
            int sx = 1 + random.nextInt(17), sy = 1 + random.nextInt(9), sz = 1 + random.nextInt(17);
            var a = new BitSetDiscreteVoxelShape(sx, sy, sz);
            var b = new BitSetDiscreteVoxelShape(sx, sy, sz);
            for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
                if (random.nextInt(31) == 0) a.fill(x, y, z);
                if (random.nextInt(31) == 0) b.fill(x, y, z);
            }
            IndexMerger x = iteration % 2 == 0 ? cube(sx, sx) : ShapeJoinTestSupport.identity(sx);
            IndexMerger y = iteration % 3 == 0 ? cube(sy, sy) : new IdenticalMerger(new CubePointRange(sy));
            IndexMerger z = iteration % 5 == 0 ? cube(sz, sz) : ShapeJoinTestSupport.identity(sz);
            compare(x, y, z, a, b, BooleanOp.AND);
            int result = ShapeGridIntersection.test(x, y, z, a, b, BooleanOp.AND);
            if ((result >= 0) != enabled) throw new AssertionError("Aligned grid activation differs");
            a.fill(sx - 1, sy - 1, sz - 1); b.fill(sx - 1, sy - 1, sz - 1);
            compare(x, y, z, a, b, BooleanOp.AND);
        }
    }

    private static void fallbacks(boolean enabled) throws Throwable {
        var a = new BitSetDiscreteVoxelShape(4, 4, 4);
        var b = new BitSetDiscreteVoxelShape(4, 4, 4);
        var identity = ShapeJoinTestSupport.identity(4);
        a.fill(1, 1, 1); b.fill(2, 1, 1);
        var unequal = cube(2, 4);
        if (ShapeGridIntersection.test(unequal, identity, identity, a, b, BooleanOp.AND) != -1)
            throw new AssertionError("Nonidentity cube mapping accepted");
        compare(unequal, identity, identity, a, b, BooleanOp.AND);
        int[] calls = {0};
        BooleanOp custom = (first, second) -> { calls[0]++; return first && second; };
        if (ShapeGridIntersection.test(identity, identity, identity, a, b, custom) != -1 || calls[0] != 0)
            throw new AssertionError("Custom operation was evaluated by the bitset query");
        ShapeJoinTestSupport.reference(identity, identity, identity, a, b, custom);
        int nativeCalls = calls[0]; calls[0] = 0;
        live(identity, identity, identity, a, b, custom);
        if (calls[0] != nativeCalls) throw new AssertionError("Custom operation call count changed");

        var customCoordinates = new DoubleArrayList(new double[5]) {
            @Override public int size() { calls[0]++; return super.size(); }
        };
        var customMerger = new IdenticalMerger(customCoordinates);
        calls[0] = 0;
        if (ShapeGridIntersection.test(customMerger, identity, identity, a, b, BooleanOp.AND) != -1 || calls[0] != 0)
            throw new AssertionError("Custom coordinate size was queried by the bitset path");
        ShapeJoinTestSupport.reference(customMerger, identity, identity, a, b, BooleanOp.AND);
        nativeCalls = calls[0]; calls[0] = 0;
        live(customMerger, identity, identity, a, b, BooleanOp.AND);
        if (calls[0] != nativeCalls) throw new AssertionError("Custom coordinate access count changed");

        var outside = new BitSetDiscreteVoxelShape(4, 4, 4); outside.fill(4, 4, 4);
        if (ShapeGridIntersection.test(identity, identity, identity, outside, outside, BooleanOp.AND) != -1)
            throw new AssertionError("Out-of-grid storage was used");
        compare(identity, identity, identity, outside, outside, BooleanOp.AND);
        var different = new BitSetDiscreteVoxelShape(4, 4, 5);
        if (ShapeGridIntersection.test(identity, identity, identity, a, different, BooleanOp.AND) != -1)
            throw new AssertionError("Different grid dimensions were used");
        compare(identity, identity, identity, a, different, BooleanOp.AND);
        if (ShapeGridIntersection.test(identity, identity, identity, a, b, BooleanOp.OR) != -1)
            throw new AssertionError("Non-AND operation was used");
        if (enabled && (ShapeGridIntersection.test(identity, identity, identity, a, b, BooleanOp.AND) != 0))
            throw new AssertionError("Disjoint grid comparison differs");
        var empty = new BitSetDiscreteVoxelShape(0, 4, 4);
        compare(ShapeJoinTestSupport.identity(0), identity, identity, empty, empty, BooleanOp.AND);
        var enormous = new BitSetDiscreteVoxelShape(65536, 65536, 1); // Native multiplication wraps its initial BitSet capacity to zero.
        var enormousAxis = new IdenticalMerger(new CubePointRange(65536));
        if (ShapeGridIntersection.test(enormousAxis, enormousAxis, ShapeJoinTestSupport.identity(1), enormous, enormous, BooleanOp.AND) != -1)
            throw new AssertionError("Overflow-sized grid accepted");
    }

    static IndexMerger cube(int first, int second) throws Exception {
        var constructor = DiscreteCubeMerger.class.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(first, second);
    }
    private static void compare(IndexMerger x, IndexMerger y, IndexMerger z,
                                DiscreteVoxelShape a, DiscreteVoxelShape b, BooleanOp op) throws Throwable {
        if (ShapeJoinTestSupport.reference(x, y, z, a, b, op) != live(x, y, z, a, b, op))
            throw new AssertionError("Grid intersection differs from native traversal");
    }
    private static boolean live(IndexMerger x, IndexMerger y, IndexMerger z,
                                DiscreteVoxelShape a, DiscreteVoxelShape b, BooleanOp op) throws Throwable {
        return (boolean) ShapeJoinTestSupport.LIVE.invokeExact(x, y, z, a, b, op);
    }
}
