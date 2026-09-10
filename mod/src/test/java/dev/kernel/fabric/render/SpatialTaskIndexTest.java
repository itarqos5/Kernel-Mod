package dev.kernel.fabric.render;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SpatialTaskIndexTest {
    @Test
    void stationaryHeapTransitionsRetainNewJobsCancellationAndCameraMovement() {
        SpatialTaskIndex<BlockPos> index = new SpatialTaskIndex<>();
        List<SpatialTaskIndex.Node<BlockPos>> pending = new ArrayList<>();
        Random random = new Random(17349);
        long sequence = 0;
        for (int i = 0; i < 1200; i++) {
            BlockPos point = new BlockPos(random.nextInt(100), random.nextInt(100), random.nextInt(100));
            pending.add(index.add(point, point.getX(), point.getY(), point.getZ(), sequence++));
        }
        for (int i = 0; i < 10_000; i++) {
            double camera = (i / 16) % 100;
            if (random.nextBoolean()) {
                BlockPos point = new BlockPos(random.nextInt(100), random.nextInt(100), random.nextInt(100));
                pending.add(index.add(point, point.getX(), point.getY(), point.getZ(), sequence++));
            } else if (!pending.isEmpty()) index.remove(pending.remove(random.nextInt(pending.size())));
            assertNearest(index, pending, camera, -1, 50);
        }
    }

    @Test
    void nearestQueriesMatchMinecraftDistancesAcrossThreeDimensionsAndMutations() {
        SpatialTaskIndex<BlockPos> index = new SpatialTaskIndex<>();
        List<SpatialTaskIndex.Node<BlockPos>> pending = new ArrayList<>();
        Random random = new Random(23979);
        long sequence = 0;
        for (int operation = 0; operation < 30_000; operation++) {
            if (pending.isEmpty() || random.nextInt(100) < 56) {
                BlockPos point = new BlockPos(random.nextInt(1000) - 500, random.nextInt(1000) - 500, random.nextInt(1000) - 500);
                pending.add(index.add(point, point.getX(), point.getY(), point.getZ(), sequence++));
            } else {
                int selected = random.nextInt(pending.size());
                index.remove(pending.remove(selected));
            }
            double x = random.nextDouble() * 2000 - 1000, y = random.nextDouble() * 2000 - 1000, z = random.nextDouble() * 2000 - 1000;
            assertNearest(index, pending, x, y, z);
            assertEquals(pending.size(), index.size());
        }
        for (var node : pending) index.remove(node);
        assertNull(index.closest(0, 0, 0));
        assertEquals(0, index.size());
    }

    @Test
    void equalDistancesExtremeOriginsAndExceptionalCamerasPreserveStrictComparisons() {
        SpatialTaskIndex<BlockPos> index = new SpatialTaskIndex<>();
        List<SpatialTaskIndex.Node<BlockPos>> pending = new ArrayList<>();
        long sequence = 0;
        for (int x : new int[] {Integer.MIN_VALUE, -30_000_000, -1, 0, 1, 30_000_000, Integer.MAX_VALUE}) {
            for (int y : new int[] {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}) {
                for (int z : new int[] {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}) {
                    BlockPos point = new BlockPos(x, y, z);
                    pending.add(index.add(point, x, y, z, sequence++));
                    pending.add(index.add(point, x, y, z, sequence++));
                    pending.add(index.add(point, x, y, z, sequence++));
                }
            }
        }
        double[] cameras = {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -Double.MAX_VALUE,
            -1e150, -1e17, -30_000_000.5, -0.5, -0.0, 0, Double.MIN_VALUE, 0.5, 1, 30_000_000.5, 1e17, 1e150, Double.MAX_VALUE};
        for (double x : cameras) for (double y : cameras) for (double z : cameras) assertNearest(index, pending, x, y, z);
        Random random = new Random(8347);
        for (int sample = 0; sample < 10_000; sample++) {
            assertNearest(index, pending, Double.longBitsToDouble(random.nextLong()),
                Double.longBitsToDouble(random.nextLong()), Double.longBitsToDouble(random.nextLong()));
        }
        index.clear();
        assertNull(index.closest(0, 0, 0));
        assertEquals(0, index.size());
        BlockPos replacement = new BlockPos(4, 5, 6);
        assertSame(replacement, index.add(replacement, 4, 5, 6, sequence).value);
        assertSame(replacement, index.closest(0, 0, 0).value);
    }

    @Test
    void monotonicInsertionAndRemovalRemainUsableAtLargeBacklogs() {
        SpatialTaskIndex<Integer> index = new SpatialTaskIndex<>();
        for (int point = 0; point < 100_000; point++) index.add(point, point, 0, 0, point);
        for (int point = 99_999; point >= 0; point--) {
            var nearest = index.closest(100_001, 0, 0);
            assertEquals(point, nearest.value);
            index.remove(nearest);
        }
        assertEquals(0, index.size());
    }

    private static void assertNearest(SpatialTaskIndex<BlockPos> index, List<SpatialTaskIndex.Node<BlockPos>> pending,
                                      double x, double y, double z) {
        SpatialTaskIndex.Node<BlockPos> expected = null;
        double distance = Double.MAX_VALUE;
        for (var node : pending) {
            double candidate = node.value.distToCenterSqr(x, y, z);
            if (candidate < distance || candidate == distance && expected != null && node.sequence < expected.sequence) {
                expected = node; distance = candidate;
            }
        }
        var actual = index.closest(x, y, z);
        assertSame(expected, actual, () -> "camera " + x + ", " + y + ", " + z);
        if (actual != null) assertEquals(Double.doubleToLongBits(distance), Double.doubleToLongBits(actual.distance));
    }
}
