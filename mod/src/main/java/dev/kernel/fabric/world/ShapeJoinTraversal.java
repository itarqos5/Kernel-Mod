package dev.kernel.fabric.world;

import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.IndexMerger;
import net.minecraft.world.phys.shapes.DiscreteCubeMerger;
import net.minecraft.world.phys.shapes.IdenticalMerger;
import net.minecraft.world.phys.shapes.IndirectMerger;
import net.minecraft.world.phys.shapes.NonOverlappingMerger;

/** Reuses the three traversal callbacks; coordinates and occupancy remain Minecraft's responsibility. */
public final class ShapeJoinTraversal {
    private static final ThreadLocal<Pool> POOL = ThreadLocal.withInitial(Pool::new);

    private ShapeJoinTraversal() {}

    public static boolean ownsCallbacks(IndexMerger merger) {
        if (merger == null) return false;
        Class<?> type = merger.getClass();
        return type == DiscreteCubeMerger.class || type == IdenticalMerger.class
            || type == IndirectMerger.class || type == NonOverlappingMerger.class;
    }

    public static boolean test(IndexMerger x, IndexMerger y, IndexMerger z,
                               DiscreteVoxelShape first, DiscreteVoxelShape second, BooleanOp operation) {
        Pool pool = POOL.get();
        Cursor cursor = pool.acquire();
        cursor.y = y;
        cursor.z = z;
        cursor.first = first;
        cursor.second = second;
        cursor.operation = operation;
        try {
            return !x.forMergedIndexes(cursor.xConsumer);
        } finally {
            cursor.clear();
            pool.depth--;
        }
    }

    private static final class Pool {
        private final Cursor[] retained = new Cursor[4];
        private int depth;

        Cursor acquire() {
            int slot = depth;
            Cursor result;
            if (slot >= retained.length) result = new Cursor();
            else {
                result = retained[slot];
                if (result == null) retained[slot] = result = new Cursor();
            }
            depth++;
            return result;
        }
    }

    private static final class Cursor {
        private IndexMerger y, z;
        private DiscreteVoxelShape first, second;
        private BooleanOp operation;
        private int x1, x2, y1, y2;
        private final IndexMerger.IndexConsumer zConsumer = (a, b, result) ->
            !operation.apply(first.isFullWide(x1, y1, a), second.isFullWide(x2, y2, b));
        private final IndexMerger.IndexConsumer yConsumer = (a, b, result) -> {
            y1 = a;
            y2 = b;
            return z.forMergedIndexes(zConsumer);
        };
        private final IndexMerger.IndexConsumer xConsumer = (a, b, result) -> {
            x1 = a;
            x2 = b;
            return y.forMergedIndexes(yConsumer);
        };

        private void clear() {
            y = null;
            z = null;
            first = null;
            second = null;
            operation = null;
        }
    }
}
