package dev.kernel.fabric.world;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.lang.invoke.MethodHandle;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.*;

/** Native differential geometry, callback lifetime, reentrancy and allocation checks. */
public final class ShapeConstructionSmokeChecks {
    private static final MethodHandle MERGER = ShapeJoinTestSupport.method(Shapes.class, "createIndexMerger", int.class,
        DoubleList.class, DoubleList.class, boolean.class, boolean.class);
    private static volatile DiscreteVoxelShape sink;
    private ShapeConstructionSmokeChecks() {}
    public static void run(boolean enabled) throws Throwable {
        Object shape = new BitSetDiscreteVoxelShape(1, 1, 1);
        if ((shape instanceof ShapeConstructionAccess) != enabled || WorldSettings.active(WorldFeature.SHAPE_CONSTRUCTION) != enabled)
            throw new AssertionError("Shape construction control differs");
        differential(909118, 1024);
        boundaries(); customAndNested();
        var failure = new AtomicReference<Throwable>();
        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            int seed = i;
            workers[i] = new Thread(() -> {
                try { differential(17279 + seed, 64); customAndNested(); }
                catch (Throwable thrown) { failure.compareAndSet(null, thrown); }
            });
            workers[i].start();
        }
        for (var worker : workers) worker.join();
        if (failure.get() != null) throw failure.get();
        allocation(enabled);
        if (enabled) poolReleased();
        if (Boolean.getBoolean("kernel.shapeConstructionBenchmark")) ShapeConstructionBenchmark.run(enabled);
        System.out.println("Kernel shape construction: enabled=" + enabled + "; native cells/bounds/boxes, callback order, eight-deep reentrancy, exceptions and concurrency passed");
    }
    private static void differential(int seed, int cases) throws Throwable {
        Random random = new Random(seed);
        for (int sample = 0; sample < cases; sample++) {
            int[] as = {1 + random.nextInt(6), 1 + random.nextInt(6), 1 + random.nextInt(6)};
            int[] bs = {1 + random.nextInt(6), 1 + random.nextInt(6), 1 + random.nextInt(6)};
            var a = shape(as, random); var b = shape(bs, random);
            int mask = sample & 15;
            BooleanOp operation = (av, bv) -> (mask & (1 << ((av ? 2 : 0) | (bv ? 1 : 0)))) != 0;
            IndexMerger[] axes = new IndexMerger[3]; int cost = 1;
            for (int axis = 0; axis < 3; axis++) {
                DoubleList ac = sample % 2 == 0 ? new CubePointRange(as[axis]) : coordinates(as[axis], random);
                DoubleList bc = sample % 2 == 0 ? new CubePointRange(bs[axis]) : coordinates(bs[axis], random);
                if (sample % 7 == 0) bc = ac;
                axes[axis] = (IndexMerger) MERGER.invokeExact(cost, ac, bc, operation.apply(true, false), operation.apply(false, true));
                cost *= axes[axis].size() - 1;
            }
            compare(a, b, axes, operation, sample % 16 == 0);
            a.fill(as[0] - 1, as[1] - 1, as[2] - 1); b.fill(0, 0, 0);
            compare(a, b, axes, operation, false);
        }
    }
    private static DoubleList coordinates(int size, Random random) {
        double[] values = new double[size + 1]; double start = random.nextInt(10) - 5;
        for (int i = 0; i <= size; i++) values[i] = start + i * (random.nextBoolean() ? .5 : 1e-8);
        Arrays.sort(values);
        if (random.nextInt(8) == 0) values[0] = Double.NEGATIVE_INFINITY;
        if (random.nextInt(8) == 0) values[size] = Double.POSITIVE_INFINITY;
        return DoubleArrayList.wrap(values);
    }
    private static BitSetDiscreteVoxelShape shape(int[] sizes, Random random) {
        var shape = new BitSetDiscreteVoxelShape(sizes[0], sizes[1], sizes[2]);
        for (int x = 0; x < sizes[0]; x++) for (int y = 0; y < sizes[1]; y++) for (int z = 0; z < sizes[2]; z++)
            if (random.nextInt(4) == 0) shape.fill(x, y, z);
        return shape;
    }
    private static DiscreteVoxelShape compare(DiscreteVoxelShape a, DiscreteVoxelShape b, IndexMerger[] axes, BooleanOp operation, boolean boxes) throws Throwable {
        var expected = ShapeConstructionTestSupport.join(true, a, b, axes[0], axes[1], axes[2], operation);
        var actual = ShapeConstructionTestSupport.join(false, a, b, axes[0], axes[1], axes[2], operation);
        equal(expected, actual, boxes);
        if (actual == a || actual == b || actual.getClass() != BitSetDiscreteVoxelShape.class) throw new AssertionError("Joined output lost native independent ownership");
        return actual;
    }
    private static void equal(DiscreteVoxelShape expected, DiscreteVoxelShape actual, boolean boxes) {
        if (expected.getXSize() != actual.getXSize() || expected.getYSize() != actual.getYSize() || expected.getZSize() != actual.getZSize()
            || expected.isEmpty() != actual.isEmpty()) throw new AssertionError("Native joined dimensions/emptiness differ");
        for (var axis : Direction.Axis.values()) if (expected.firstFull(axis) != actual.firstFull(axis) || expected.lastFull(axis) != actual.lastFull(axis))
            throw new AssertionError("Native joined bounds differ on " + axis);
        for (int x = -1; x <= actual.getXSize(); x++) for (int y = -1; y <= actual.getYSize(); y++) for (int z = -1; z <= actual.getZSize(); z++)
            if (expected.isFullWide(x, y, z) != actual.isFullWide(x, y, z)) throw new AssertionError("Native joined cell differs");
        if (boxes) {
            var expectedBoxes = new ArrayList<String>(); var actualBoxes = new ArrayList<String>();
            expected.forAllBoxes((a, b, c, d, e, f) -> expectedBoxes.add(a + "," + b + "," + c + "," + d + "," + e + "," + f), true);
            actual.forAllBoxes((a, b, c, d, e, f) -> actualBoxes.add(a + "," + b + "," + c + "," + d + "," + e + "," + f), true);
            if (!expectedBoxes.equals(actualBoxes)) throw new AssertionError("Native joined box order differs");
        }
    }
    private static void boundaries() throws Throwable {
        for (int x : new int[]{0, 1, 2}) for (int y : new int[]{0, 1, 3}) for (int z : new int[]{0, 1, 4}) {
            var empty = new BitSetDiscreteVoxelShape(x, y, z);
            IndexMerger[] axes = {ShapeJoinTestSupport.identity(x), ShapeJoinTestSupport.identity(y), ShapeJoinTestSupport.identity(z)};
            var output = compare(empty, empty, axes, BooleanOp.FALSE, true);
            if (output.firstFull(Direction.Axis.X) != Integer.MAX_VALUE || output.lastFull(Direction.Axis.X) != Integer.MIN_VALUE + 1)
                throw new AssertionError("Empty join sentinel bounds were normalized");
        }
        var a = new BitSetDiscreteVoxelShape(2, 2, 2); a.fill(0, 0, 0);
        var axes = new IndexMerger[]{ShapeJoinTestSupport.identity(2), ShapeJoinTestSupport.identity(2), ShapeJoinTestSupport.identity(2)};
        var result = compare(a, a, axes, BooleanOp.OR, true);
        a.fill(1, 1, 1);
        if (result.isFull(1, 1, 1)) throw new AssertionError("Input mutation changed a completed result");
        result.fill(1, 0, 1);
        if (a.isFull(1, 0, 1)) throw new AssertionError("Output mutation changed input storage");
        var emptyAxis = ShapeJoinTestSupport.identity(0);
        var invalidAxis = new IdenticalMerger(DoubleArrayList.wrap(new double[0]));
        for (boolean reference : new boolean[]{true, false}) {
            var nothing = ShapeConstructionTestSupport.join(reference, null, null, emptyAxis, emptyAxis, emptyAxis, null);
            if (!nothing.isEmpty()) throw new AssertionError("Zero-axis join evaluated missing inputs");
            try { ShapeConstructionTestSupport.join(reference, a, a, invalidAxis, axes[1], axes[2], BooleanOp.OR); throw new AssertionError("Negative dimension accepted"); }
            catch (IllegalArgumentException expected) {}
            try { ShapeConstructionTestSupport.join(reference, null, a, axes[0], axes[1], axes[2], BooleanOp.OR); throw new AssertionError("Null input accepted for visited cells"); }
            catch (NullPointerException expected) {}
            try { ShapeConstructionTestSupport.join(reference, a, a, axes[0], axes[1], axes[2], null); throw new AssertionError("Null operation accepted for visited cells"); }
            catch (NullPointerException expected) {}
            try { ShapeConstructionTestSupport.join(reference, a, a, null, axes[1], axes[2], BooleanOp.OR); throw new AssertionError("Null merger accepted"); }
            catch (NullPointerException expected) {}
        }
    }
    private static void customAndNested() throws Throwable {
        var nativeMerger = ShapeJoinTestSupport.identity(2);
        var empty = new BitSetDiscreteVoxelShape(2, 2, 2);
        var expectedTrace = new ArrayList<String>(); var actualTrace = new ArrayList<String>();
        for (boolean reference : new boolean[]{true, false}) {
            var trace = reference ? expectedTrace : actualTrace;
            DiscreteVoxelShape custom = new TraceShape(trace);
            BooleanOp operation = (a, b) -> { trace.add("op:" + a + ":" + b); return a || b; };
            ShapeConstructionTestSupport.join(reference, custom, custom, nativeMerger, nativeMerger, nativeMerger, operation);
        }
        if (!expectedTrace.equals(actualTrace)) throw new AssertionError("Custom shape/operation evaluation order changed");
        var retained = new ArrayList<IndexMerger.IndexConsumer>();
        IndexMerger custom = new IdenticalMerger(DoubleArrayList.wrap(new double[3])) {
            @Override public boolean forMergedIndexes(IndexConsumer consumer) { retained.add(consumer); return super.forMergedIndexes(consumer); }
        };
        for (int i = 0; i < 2; i++) ShapeConstructionTestSupport.join(false, empty, empty, custom, nativeMerger, nativeMerger, BooleanOp.TRUE);
        if (retained.size() != 2 || retained.get(0) == retained.get(1) || !retained.get(0).merge(0, 0, 0))
            throw new AssertionError("Custom merger callback ownership changed");
        for (boolean reference : new boolean[]{true, false}) {
            var result = nested(reference, 8, -1);
            if (!result.isFull(0, 0, 0)) throw new AssertionError("Nested result corrupted");
            try { nested(reference, 8, 4); throw new AssertionError("Nested failure suppressed"); }
            catch (ProbeFailure expected) {}
            if (!nested(reference, 8, -1).isFull(0, 0, 0)) throw new AssertionError("Nested failure poisoned reuse");
        }
    }
    private static DiscreteVoxelShape nested(boolean reference, int depth, int failAt) throws Throwable {
        var merger = ShapeJoinTestSupport.identity(1);
        var empty = new BitSetDiscreteVoxelShape(1, 1, 1);
        BooleanOp operation = (a, b) -> {
            if (depth == failAt) throw new ProbeFailure();
            if (depth == 0) return true;
            try { return nested(reference, depth - 1, failAt).isFull(0, 0, 0); }
            catch (RuntimeException | Error failure) { throw failure; }
            catch (Throwable failure) { throw new AssertionError(failure); }
        };
        return ShapeConstructionTestSupport.join(reference, empty, empty, merger, merger, merger, operation);
    }
    private static final class ProbeFailure extends RuntimeException {}
    private static final class TraceShape extends DiscreteVoxelShape {
        private final List<String> trace;
        TraceShape(List<String> trace) { super(2, 2, 2); this.trace = trace; }
        @Override public boolean isFull(int x, int y, int z) { trace.add(x + "," + y + "," + z); return (x + y + z) % 2 == 0; }
        @Override public void fill(int x, int y, int z) { throw new AssertionError("Input shape mutated"); }
        @Override public int firstFull(Direction.Axis axis) { return 0; }
        @Override public int lastFull(Direction.Axis axis) { return 2; }
    }
    private static void allocation(boolean enabled) throws Throwable {
        var merger = ShapeJoinTestSupport.identity(16); DiscreteVoxelShape empty = new BitSetDiscreteVoxelShape(16, 16, 16);
        for (int i = 0; i < 5000; i++) sink = ShapeConstructionTestSupport.join(false, empty, empty, merger, merger, merger, BooleanOp.FALSE);
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        for (int i = 0; i < 1000; i++) sink = ShapeConstructionTestSupport.join(false, empty, empty, merger, merger, merger, BooleanOp.FALSE);
        long allocated = (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before) / 1000;
        if (enabled && allocated > 640) throw new AssertionError("Joined traversal still allocates per row: " + allocated);
        System.out.println("Kernel shape construction allocation: enabled=" + enabled + ", " + allocated + " bytes/join including owned 16-cube output");
    }
    private static void poolReleased() throws Exception {
        var field = ShapeConstruction.class.getDeclaredField("POOL"); field.setAccessible(true);
        Object pool = ((ThreadLocal<?>) field.get(null)).get();
        var depth = pool.getClass().getDeclaredField("depth"); depth.setAccessible(true);
        if (depth.getInt(pool) != 0) throw new AssertionError("Construction pool depth leaked");
        var retained = pool.getClass().getDeclaredField("retained"); retained.setAccessible(true);
        for (Object cursor : (Object[]) retained.get(pool)) if (cursor != null)
            for (String name : new String[]{"first", "second", "y", "z", "operation", "storage"}) {
                var reference = cursor.getClass().getDeclaredField(name); reference.setAccessible(true);
                if (reference.get(cursor) != null) throw new AssertionError("Construction pool retained " + name);
            }
    }
}
