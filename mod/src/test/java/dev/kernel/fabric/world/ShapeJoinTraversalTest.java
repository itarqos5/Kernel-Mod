package dev.kernel.fabric.world;

import net.minecraft.world.phys.shapes.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

final class ShapeJoinTraversalTest {
    @Test void matchesNativeMergersAcrossCoordinateAndBooleanCases() throws Throwable {
        ShapeJoinTestSupport.differential(902134, 4000, false);
    }

    @Test void preservesOccupancyOrderBooleanCallsAndEarlyTermination() throws Throwable {
        var events = new ArrayList<String>();
        var first = tracingShape("a", events);
        var second = tracingShape("b", events);
        IndexMerger merger = ShapeJoinTestSupport.identity(3);
        for (int stop : new int[]{0, 1, 4, 13, 27, 100}) {
            int[] calls = {0};
            BooleanOp op = (a, b) -> { events.add("op:" + a + ":" + b); return ++calls[0] == stop; };
            boolean nativeResult = ShapeJoinTestSupport.reference(merger, merger, merger, first, second, op);
            var nativeEvents = List.copyOf(events);
            events.clear(); calls[0] = 0;
            assertEquals(nativeResult, ShapeJoinTraversal.test(merger, merger, merger, first, second, op));
            assertEquals(nativeEvents, events);
            events.clear();
        }
    }

    @Test void nestedCallsExceptionsAndMutationsReleaseAllReferences() throws Exception {
        var merger = ShapeJoinTestSupport.identity(2);
        var shape = new BitSetDiscreteVoxelShape(2, 2, 2);
        assertFalse(ShapeJoinTraversal.test(merger, merger, merger, shape, shape, BooleanOp.AND));
        shape.fill(1, 1, 1);
        assertTrue(ShapeJoinTraversal.test(merger, merger, merger, shape, shape, BooleanOp.AND));
        nested(12, merger, shape);
        RuntimeException failure = new RuntimeException("callback failure");
        assertSame(failure, assertThrows(RuntimeException.class, () -> ShapeJoinTraversal.test(merger, merger, merger, shape, shape,
            (a, b) -> { throw failure; })));
        nested(6, merger, shape);
        var localField = ShapeJoinTraversal.class.getDeclaredField("POOL"); localField.setAccessible(true);
        Object pool = ((ThreadLocal<?>) localField.get(null)).get();
        var depth = pool.getClass().getDeclaredField("depth"); depth.setAccessible(true);
        assertEquals(0, depth.getInt(pool));
        var retained = pool.getClass().getDeclaredField("retained"); retained.setAccessible(true);
        Object[] cursors = (Object[]) retained.get(pool);
        assertEquals(4, cursors.length);
        for (Object cursor : cursors) for (String name : new String[]{"first", "second", "operation", "y", "z"}) {
            var field = cursor.getClass().getDeclaredField(name); field.setAccessible(true);
            assertNull(field.get(cursor), "Retained shape query input: " + name);
        }
    }

    @Test void independentThreadsKeepTheirTraversalState() throws Throwable {
        var failure = new AtomicReference<Throwable>();
        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            int seed = 563701 + i;
            workers[i] = new Thread(() -> {
                try { ShapeJoinTestSupport.differential(seed, 1000, false); }
                catch (Throwable thrown) { failure.compareAndSet(null, thrown); }
            });
            workers[i].start();
        }
        for (Thread worker : workers) worker.join();
        if (failure.get() != null) throw failure.get();
    }

    private static void nested(int depth, IndexMerger merger, DiscreteVoxelShape shape) {
        int[] calls = {0};
        assertTrue(ShapeJoinTraversal.test(merger, merger, merger, shape, shape, (a, b) -> {
            if (++calls[0] == 1 && depth > 0) nested(depth - 1, merger, shape);
            return a && b;
        }));
        assertEquals(8, calls[0]);
    }

    private static DiscreteVoxelShape tracingShape(String id, List<String> events) {
        return new DiscreteVoxelShape(3, 3, 3) {
            @Override public boolean isFull(int x, int y, int z) { return (x + y + z) % 3 == 0; }
            @Override public void fill(int x, int y, int z) { throw new UnsupportedOperationException(); }
            @Override public int firstFull(net.minecraft.core.Direction.Axis axis) { return 0; }
            @Override public int lastFull(net.minecraft.core.Direction.Axis axis) { return 3; }
            @Override public boolean isFullWide(int x, int y, int z) {
                events.add(id + ":" + x + ":" + y + ":" + z);
                return (x + y + z) % 3 == 0;
            }
        };
    }
}
