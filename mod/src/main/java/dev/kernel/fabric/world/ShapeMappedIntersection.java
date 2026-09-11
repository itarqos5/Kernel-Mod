package dev.kernel.fabric.world;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.util.BitSet;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CubePointRange;
import net.minecraft.world.phys.shapes.DiscreteCubeMerger;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.IdenticalMerger;
import net.minecraft.world.phys.shapes.IndexMerger;
import net.minecraft.world.phys.shapes.IndirectMerger;

/** Reuses native axis mappings for bounded AND queries without retaining shapes or occupancy. */
public final class ShapeMappedIntersection {
    private static final int AXIS_LIMIT = 128;
    private static final ThreadLocal<Pool> POOL = ThreadLocal.withInitial(Pool::new);

    private ShapeMappedIntersection() {}

    /** Returns -1 for the normal traversal, 0 for disjoint occupancy, or 1 for an intersection. */
    public static int test(IndexMerger x, IndexMerger y, IndexMerger z,
                           DiscreteVoxelShape first, DiscreteVoxelShape second, BooleanOp operation) {
        if (operation != BooleanOp.AND
            || !(first instanceof ShapeStorageAccess a) || first.getClass() != BitSetDiscreteVoxelShape.class
            || !(second instanceof ShapeStorageAccess b) || second.getClass() != BitSetDiscreteVoxelShape.class) return -1;
        int nx = cells(x), ny = cells(y), nz = cells(z);
        if (nx < 0 || ny < 0 || nz < 0) return -1;
        if (nx == 0 || ny == 0 || nz == 0) return 0;
        long ayz = (long) first.getYSize() * first.getZSize();
        long byz = (long) second.getYSize() * second.getZSize();
        if (ayz > Integer.MAX_VALUE || byz > Integer.MAX_VALUE) return -1;
        long ac = ayz * first.getXSize(), bc = byz * second.getXSize();
        if (ac > Integer.MAX_VALUE || bc > Integer.MAX_VALUE) return -1;
        BitSet firstBits = a.kernel$storage(), secondBits = b.kernel$storage();
        if (firstBits == null || secondBits == null || firstBits.getClass() != BitSet.class || secondBits.getClass() != BitSet.class
            || firstBits.length() > ac || secondBits.length() > bc) return -1;
        if (firstBits.isEmpty() || secondBits.isEmpty()) return 0;
        // Query the actual first pair before preparing all mappings. Indirect mappings can start
        // away from zero or outside one input; their packed indices retain both signed values.
        long xp = firstPair(x), yp = firstPair(y), zp = firstPair(z);
        int ax = (int) xp, bx = (int) (xp >> 32), ay = (int) yp, by = (int) (yp >> 32), az = (int) zp, bz = (int) (zp >> 32);
        if (ax >= 0 && bx >= 0 && ay >= 0 && by >= 0 && az >= 0 && bz >= 0
            && ax < first.getXSize() && bx < second.getXSize() && ay < first.getYSize() && by < second.getYSize()
            && az < first.getZSize() && bz < second.getZSize()
            && firstBits.get(ax * (int) ayz + ay * first.getZSize() + az)
            && secondBits.get(bx * (int) byz + by * second.getZSize() + bz)) return 1;
        Pool pool = POOL.get();
        Scratch scratch = pool.acquire();
        try {
            if (!scratch.x.read(x, nx, first.getXSize(), second.getXSize(), (int) ayz, (int) byz)
                || !scratch.y.read(y, ny, first.getYSize(), second.getYSize(), first.getZSize(), second.getZSize())
                || !scratch.z.read(z, nz, first.getZSize(), second.getZSize(), 1, 1)) return -1;
            for (int i = 0; i < scratch.x.count; i++) for (int j = 0; j < scratch.y.count; j++) {
                int firstBase = scratch.x.first[i] + scratch.y.first[j];
                int secondBase = scratch.x.second[i] + scratch.y.second[j];
                for (int k = 0; k < scratch.z.count; k++)
                    if (firstBits.get(firstBase + scratch.z.first[k]) && secondBits.get(secondBase + scratch.z.second[k])) return 1;
            }
            return 0;
        } finally {
            pool.depth--;
        }
    }

    private static int cells(IndexMerger merger) {
        if (merger == null) return -1;
        Class<?> type = merger.getClass();
        if (type == IndirectMerger.class) {
            if (!(merger instanceof IndirectMergerAccess)) return -1;
        } else {
            if (type != IdenticalMerger.class && type != DiscreteCubeMerger.class) return -1;
            var coordinates = merger.getList();
            // Do not add observable calls to custom lists. IndirectMerger owns primitive mappings;
            // its getList() would allocate a wrapper, whereas size() reads its stored result length.
            if (coordinates == null || coordinates.getClass() != DoubleArrayList.class && coordinates.getClass() != CubePointRange.class) return -1;
        }
        int count = merger.size() - 1;
        return count >= 0 && count <= AXIS_LIMIT ? count : -1;
    }

    private static long firstPair(IndexMerger merger) {
        return merger instanceof IndirectMergerAccess indirect ? indirect.kernel$firstIndexPair() : 0L;
    }

    private static final class Pool {
        private final Scratch[] retained = new Scratch[4];
        private int depth;

        Scratch acquire() {
            int slot = depth;
            Scratch value;
            if (slot >= retained.length) value = new Scratch();
            else {
                value = retained[slot];
                if (value == null) retained[slot] = value = new Scratch();
            }
            depth++;
            return value;
        }
    }

    private static final class Scratch {
        private final Axis x = new Axis(), y = new Axis(), z = new Axis();
    }

    private static final class Axis implements IndexMerger.IndexConsumer {
        private final int[] first = new int[AXIS_LIMIT], second = new int[AXIS_LIMIT];
        private int count, visited, expected, firstLimit, secondLimit, firstScale, secondScale;

        boolean read(IndexMerger merger, int expected, int firstLimit, int secondLimit, int firstScale, int secondScale) {
            this.expected = expected;
            this.firstLimit = firstLimit;
            this.secondLimit = secondLimit;
            this.firstScale = firstScale;
            this.secondScale = secondScale;
            count = visited = 0;
            return merger.forMergedIndexes(this) && visited == expected;
        }

        @Override
        public boolean merge(int a, int b, int output) {
            if (output != visited || visited >= expected) return false;
            visited++;
            // Outside either input grid, native isFullWide is false and AND cannot intersect.
            if (a >= 0 && b >= 0 && a < firstLimit && b < secondLimit) {
                first[count] = a * firstScale;
                second[count] = b * secondScale;
                count++;
            }
            return true;
        }
    }
}
