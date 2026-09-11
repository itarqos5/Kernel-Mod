package dev.kernel.fabric.world;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.phys.shapes.*;

/** Compares bounded mapped intersections with original target-JAR bytecode and the live wrapper. */
public final class ShapeMappedSmokeChecks {
    private static final MethodHandle MERGER = ShapeJoinTestSupport.method(Shapes.class, "createIndexMerger", int.class,
        DoubleList.class, DoubleList.class, boolean.class, boolean.class);

    private ShapeMappedSmokeChecks() {}

    public static void run(boolean enabled) throws Throwable {
        Object merger = new IndirectMerger(new CubePointRange(1), new CubePointRange(1), false, false);
        if ((merger instanceof IndirectMergerAccess) != enabled) throw new AssertionError("Indirect mapping provider activation mismatch");
        int eligible = differential(19894392, 2048, enabled);
        if (enabled && eligible < 1000) throw new AssertionError("Too few native mapped queries exercised: " + eligible);
        boundaries(enabled);
        var failure = new AtomicReference<Throwable>();
        var workers = new Thread[4];
        for (int index = 0; index < workers.length; index++) {
            int seed = 971285 + index;
            workers[index] = new Thread(() -> {
                try { differential(seed, 128, enabled); }
                catch (Throwable thrown) { failure.compareAndSet(null, thrown); }
            });
            workers[index].start();
        }
        for (var worker : workers) worker.join();
        if (failure.get() != null) throw failure.get();
        System.out.println("Kernel mapped shape grids: enabled=" + enabled
            + "; native mappings, boundary/ownership fallbacks, mutation and concurrency passed.");
    }

    private static int differential(int seed, int count, boolean enabled) throws Throwable {
        var random = new Random(seed);
        int eligible = 0;
        for (int iteration = 0; iteration < count; iteration++) {
            int[] as = {1 + random.nextInt(9), 1 + random.nextInt(9), 1 + random.nextInt(9)};
            int[] bs = {1 + random.nextInt(9), 1 + random.nextInt(9), 1 + random.nextInt(9)};
            var a = shape(as, random);
            var b = shape(bs, random);
            var axes = new IndexMerger[3];
            int cost = 1;
            for (int axis = 0; axis < 3; axis++) {
                DoubleList ac = iteration % 2 == 0 ? new CubePointRange(as[axis]) : coordinates(as[axis], random);
                DoubleList bc = iteration % 2 == 0 ? new CubePointRange(bs[axis]) : coordinates(bs[axis], random);
                axes[axis] = (IndexMerger) MERGER.invokeExact(cost, ac, bc, false, false);
                cost *= axes[axis].size() - 1;
            }
            int result = compare(axes[0], axes[1], axes[2], a, b, BooleanOp.AND, enabled);
            if (result >= 0) eligible++;
            a.fill(as[0] - 1, as[1] - 1, as[2] - 1);
            b.fill(bs[0] - 1, bs[1] - 1, bs[2] - 1);
            compare(axes[0], axes[1], axes[2], a, b, BooleanOp.AND, enabled);
            if (enabled) {
                ((ShapeStorageAccess) (Object) a).kernel$storage().clear();
                compare(axes[0], axes[1], axes[2], a, b, BooleanOp.AND, true);
            }
        }
        return eligible;
    }

    private static void boundaries(boolean enabled) throws Throwable {
        var one = ShapeJoinTestSupport.identity(1);
        for (int count : new int[]{0, 1, 128, 129}) {
            var a = new BitSetDiscreteVoxelShape(count, 1, 1);
            var b = new BitSetDiscreteVoxelShape(count, 1, 1);
            if (count > 0) { a.fill(count - 1, 0, 0); b.fill(count - 1, 0, 0); }
            int result = compare(ShapeJoinTestSupport.identity(count), one, one, a, b, BooleanOp.AND, enabled);
            if ((result >= 0) != (enabled && count <= 128)) throw new AssertionError("Mapped axis limit differs");
            if (count > 0) {
                a.fill(0, 0, 0); b.fill(0, 0, 0);
                compare(new IndirectMerger(new CubePointRange(count), new CubePointRange(count), false, false), one, one, a, b, BooleanOp.AND, enabled);
            }
        }
        var a = new BitSetDiscreteVoxelShape(4, 1, 1);
        var b = new BitSetDiscreteVoxelShape(4, 1, 1);
        a.fill(0, 0, 0); b.fill(0, 0, 0);
        var shifted = new IndirectMerger(DoubleArrayList.wrap(new double[]{0, 1, 2, 3, 4}),
            DoubleArrayList.wrap(new double[]{2, 3, 4, 5, 6}), false, false);
        if (compare(shifted, one, one, a, b, BooleanOp.AND, enabled) == 1)
            throw new AssertionError("Indirect first-cell shortcut ignored shifted coordinates");
        b.fill(3, 0, 0);
        var union = new IndirectMerger(DoubleArrayList.wrap(new double[]{0, 1, 2, 3, 4}),
            DoubleArrayList.wrap(new double[]{-2, -1, 0, 1, 2}), true, true);
        compare(union, one, one, a, b, BooleanOp.AND, enabled);
        // Merger coordinates need not match input dimensions; out-of-range indices must stay false.
        compare(ShapeJoinTestSupport.identity(8), one, one, a, b, BooleanOp.AND, enabled);
        compare(ShapeGridSmokeChecks.cube(2, 4), one, one, a, b, BooleanOp.AND, enabled);
        var identity = ShapeJoinTestSupport.identity(4);
        if (ShapeMappedIntersection.test(identity, one, one, a, b, BooleanOp.OR) != -1)
            throw new AssertionError("Non-AND operation accepted");
        int[] calls = {0};
        var customCoordinates = new DoubleArrayList(new double[5]) {
            @Override public int size() { calls[0]++; return super.size(); }
        };
        var custom = new IdenticalMerger(customCoordinates);
        if (ShapeMappedIntersection.test(custom, one, one, a, b, BooleanOp.AND) != -1 || calls[0] != 0)
            throw new AssertionError("Custom coordinate methods observed by mapped path");
        var subclass = new DiscreteVoxelShape(4, 1, 1) {
            @Override public boolean isFull(int x, int y, int z) { calls[0]++; return a.isFull(x, y, z); }
            @Override public void fill(int x, int y, int z) { a.fill(x, y, z); }
            @Override public int firstFull(net.minecraft.core.Direction.Axis axis) { return a.firstFull(axis); }
            @Override public int lastFull(net.minecraft.core.Direction.Axis axis) { return a.lastFull(axis); }
        };
        if (ShapeMappedIntersection.test(identity, one, one, subclass, b, BooleanOp.AND) != -1)
            throw new AssertionError("Custom shape accepted");
        compare(identity, one, one, subclass, b, BooleanOp.AND, enabled);
        if (enabled) {
            var field = BitSetDiscreteVoxelShape.class.getDeclaredField("storage");
            field.setAccessible(true);
            BitSet bits = ((ShapeStorageAccess) (Object) a).kernel$storage();
            try {
                var customBits = new BitSet() { @Override public boolean get(int bit) { calls[0]++; return super.get(bit); } };
                customBits.or(bits);
                field.set(a, customBits);
                calls[0] = 0;
                if (ShapeMappedIntersection.test(identity, one, one, a, b, BooleanOp.AND) != -1 || calls[0] != 0)
                    throw new AssertionError("Custom occupancy observed by mapped path");
                compare(identity, one, one, a, b, BooleanOp.AND, true);
            } finally { field.set(a, bits); }
            bits.set(8);
            if (ShapeMappedIntersection.test(identity, one, one, a, b, BooleanOp.AND) != -1)
                throw new AssertionError("Out-of-grid bits accepted");
            bits.clear(8);
        }
        var enormous = new BitSetDiscreteVoxelShape(65536, 65536, 1);
        if (ShapeMappedIntersection.test(one, one, one, enormous, enormous, BooleanOp.AND) != -1)
            throw new AssertionError("Overflow-sized storage accepted");
    }

    private static int compare(IndexMerger x, IndexMerger y, IndexMerger z, DiscreteVoxelShape a,
                               DiscreteVoxelShape b, BooleanOp op, boolean enabled) throws Throwable {
        boolean expected = ShapeJoinTestSupport.reference(x, y, z, a, b, op);
        int mapped = ShapeMappedIntersection.test(x, y, z, a, b, op);
        if (!enabled && mapped >= 0 || mapped >= 0 && (mapped != 0) != expected
            || (boolean) ShapeJoinTestSupport.LIVE.invokeExact(x, y, z, a, b, op) != expected)
            throw new AssertionError("Mapped shape intersection differs from native traversal");
        return mapped;
    }

    private static BitSetDiscreteVoxelShape shape(int[] size, Random random) {
        var result = new BitSetDiscreteVoxelShape(size[0], size[1], size[2]);
        for (int x = 0; x < size[0]; x++) for (int y = 0; y < size[1]; y++) for (int z = 0; z < size[2]; z++)
            if (random.nextInt(7) == 0) result.fill(x, y, z);
        return result;
    }

    private static DoubleList coordinates(int cells, Random random) {
        double[] values = new double[cells + 1];
        double base = random.nextInt(5) - 2;
        for (int i = 0; i <= cells; i++) values[i] = base + i * (random.nextBoolean() ? .5 : 1e-8);
        Arrays.sort(values);
        if (random.nextInt(7) == 0) values[0] = Double.NEGATIVE_INFINITY;
        if (random.nextInt(7) == 0) values[cells] = Double.POSITIVE_INFINITY;
        return DoubleArrayList.wrap(values);
    }
}
