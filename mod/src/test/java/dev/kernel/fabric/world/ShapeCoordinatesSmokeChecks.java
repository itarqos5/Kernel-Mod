package dev.kernel.fabric.world;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.shapes.*;

public final class ShapeCoordinatesSmokeChecks {
    public static final AtomicInteger competingCalls = new AtomicInteger();
    private static volatile Object sink;
    public static void run(boolean configured, boolean competing) throws Throwable {
        check(WorldSettings.active(WorldFeature.SHAPE_COORDINATES) == configured, "Coordinate setting ownership");
        boolean enabled = configured && !competing;
        for (int parts = 1; parts <= 129; parts++) verify(parts, enabled);
        for (int parts : new int[]{0, -1, Integer.MIN_VALUE, 65536, Integer.MAX_VALUE}) boundary(parts);
        var changing = new Grid(1, 2, 3);
        var shape = ShapeCoordinatesTestSupport.create(changing, false);
        for (int parts = 1; parts <= 129; parts++) {
            changing.x = parts;
            int previous = changing.calls;
            check(shape.getCoords(Axis.X).size() == parts + 1 && changing.calls == previous + 1, "Native dimension query was cached or repeated");
        }
        expect(NullPointerException.class, () -> shape.getCoords(null));
        changing.failure = new IllegalStateException("dimension failure");
        try { shape.getCoords(Axis.X); throw new AssertionError("Dimension failure swallowed"); }
        catch (IllegalStateException failure) { check(failure == changing.failure, "Dimension failure replaced"); }
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            var results = new ArrayList<Future<?>>();
            for (int thread = 0; thread < 4; thread++) results.add(workers.submit(() -> {
                try { for (int parts = 1; parts <= 64; parts++) verify(parts, enabled); }
                catch (Throwable failure) { throw new CompletionException(failure); }
            }));
            for (var result : results) result.get();
        } finally { workers.shutdownNow(); }
        check((competingCalls.get() > 0) == competing, "Competing coordinate constructor ownership");
        allocation(enabled);
        System.out.println("Kernel shape coordinates: enabled=" + configured + ", competing=" + competing
            + "; native values/collections/immutability, dimension calls, bounds, failures and concurrency passed");
        if (Boolean.getBoolean("kernel.shapeCoordinatesBenchmark")) ShapeCoordinatesBenchmark.run();
    }
    private static void verify(int parts, boolean enabled) throws Throwable {
        Grid grid = new Grid(parts, parts % 64 + 1, (parts * 37 & 63) + 1);
        VoxelShape live = ShapeCoordinatesTestSupport.create(grid, false);
        VoxelShape reference = ShapeCoordinatesTestSupport.create(grid, true);
        for (Axis axis : Axis.values()) {
            int before = grid.calls;
            DoubleList actual = live.getCoords(axis), expected = reference.getCoords(axis);
            check(grid.calls == before + 2, "Unexpected dimension invocation count");
            int dimension = switch (axis) { case X -> grid.x; case Y -> grid.y; case Z -> grid.z; };
            check(actual.getClass() == CubePointRange.class && expected.getClass() == CubePointRange.class, "Coordinate implementation changed");
            check((actual == live.getCoords(axis)) == (enabled && dimension <= 64), "Wrong cached identity or retention bound");
            VoxelShape other = ShapeCoordinatesTestSupport.create(new Grid(grid.x, grid.y, grid.z), false);
            check((actual == other.getCoords(axis)) == (enabled && dimension <= 64), "Coordinate sharing depends on a world shape");
            for (int index : new int[]{Integer.MIN_VALUE, -1, 0, 1, dimension/2, dimension, dimension+1, Integer.MAX_VALUE})
                check(Double.doubleToRawLongBits(actual.getDouble(index)) == Double.doubleToRawLongBits(expected.getDouble(index)), "Native coordinate value/index behavior");
            check(actual.size() == expected.size() && actual.equals(expected) && actual.hashCode() == expected.hashCode()
                && Arrays.equals(actual.toDoubleArray(), expected.toDoubleArray()), "Native collection values");
            var one = actual.iterator(); var two = actual.iterator();
            one.nextDouble(); one.nextDouble(); check(two.nextDouble() == 0, "Shared iterator state");
            var sub = actual.subList(0, 2);
            immutable(() -> actual.set(0, 8)); immutable(() -> actual.add(8)); immutable(actual::clear);
            immutable(() -> actual.removeDouble(0)); immutable(() -> sub.set(0, 8)); immutable(one::remove);
            immutable(() -> actual.replaceAll((java.util.function.DoubleUnaryOperator) value -> value + 1));
            check(actual.equals(expected), "Mutation changed shared values");
        }
    }
    private static void boundary(int parts) throws Throwable {
        Grid grid = new Grid(parts, 1, 1);
        VoxelShape live = ShapeCoordinatesTestSupport.create(grid, false), reference = ShapeCoordinatesTestSupport.create(grid, true);
        if (parts <= 0) {
            expect(IllegalArgumentException.class, () -> live.getCoords(Axis.X));
            expect(IllegalArgumentException.class, () -> reference.getCoords(Axis.X));
        } else {
            DoubleList actual = live.getCoords(Axis.X), expected = reference.getCoords(Axis.X);
            check(actual != live.getCoords(Axis.X) && actual.size() == expected.size(), "Large grid retention/overflow");
            check(actual.getDouble(Integer.MIN_VALUE) == expected.getDouble(Integer.MIN_VALUE)
                && actual.getDouble(Integer.MAX_VALUE) == expected.getDouble(Integer.MAX_VALUE), "Large grid coordinates");
        }
    }
    private static void allocation(boolean enabled) throws Throwable {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        for (int size : new int[]{1, 16, 64, 65}) {
            var grid = new Grid(size, size, size);
            VoxelShape live = ShapeCoordinatesTestSupport.create(grid, false), reference = ShapeCoordinatesTestSupport.create(grid, true);
            for (int i = 0; i < 20000; i++) { sink = live.getCoords(Axis.X); sink = reference.getCoords(Axis.X); }
            long thread = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 8192; i++) sink = reference.getCoords(Axis.X);
            long original = bean.getThreadAllocatedBytes(thread) - before;
            before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 8192; i++) sink = live.getCoords(Axis.X);
            long actual = bean.getThreadAllocatedBytes(thread) - before;
            check(original == 16L * 8192 && actual == (enabled && size <= 64 ? 0 : original), "Native/live coordinate allocation: " + original + "/" + actual);
        }
        check(new CubePointRange(1) != new CubePointRange(1), "Direct native construction was replaced");
    }
    static final class Grid extends DiscreteVoxelShape {
        int x, y, z, calls; RuntimeException failure;
        Grid(int x, int y, int z) { super(1, 1, 1); this.x=x; this.y=y; this.z=z; }
        @Override public int getSize(Axis axis) { calls++; if (failure != null) throw failure; return switch(axis) { case X -> x; case Y -> y; case Z -> z; }; }
        @Override public boolean isFull(int x, int y, int z) { return false; }
        @Override public void fill(int x, int y, int z) { throw new UnsupportedOperationException(); }
        @Override public int firstFull(Axis axis) { return 0; }
        @Override public int lastFull(Axis axis) { return 0; }
    }
    private static void immutable(Runnable action) { expect(UnsupportedOperationException.class, action); }
    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); } catch (Throwable failure) { if (type.isInstance(failure)) return; throw failure; }
        throw new AssertionError("Missing expected " + type.getSimpleName());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
