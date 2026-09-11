package dev.kernel.fabric.world;

import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.IndexMerger;
import java.util.BitSet;

/** Reusable iteration state; each returned shape still owns its new native storage. */
public final class ShapeConstruction {
    private static final ThreadLocal<Pool> POOL = ThreadLocal.withInitial(Pool::new);
    private ShapeConstruction() {}

    /** Call only with native non-retaining mergers, as checked by the Mixin adapter. */
    public static BitSetDiscreteVoxelShape join(DiscreteVoxelShape first, DiscreteVoxelShape second,
                                              IndexMerger x, IndexMerger y, IndexMerger z, BooleanOp operation) {
        // Preserve the native size-query order and constructor failure behavior.
        var result = new BitSetDiscreteVoxelShape(x.size() - 1, y.size() - 1, z.size() - 1);
        Pool pool = POOL.get();
        Cursor cursor = pool.acquire();
        try {
            cursor.first = first; cursor.second = second; cursor.y = y; cursor.z = z; cursor.operation = operation;
            cursor.storage = ((ShapeConstructionAccess) (Object) result).kernel$constructionStorage();
            cursor.sizeY = result.getYSize(); cursor.sizeZ = result.getZSize();
            cursor.minX = cursor.minY = cursor.minZ = Integer.MAX_VALUE;
            cursor.maxX = cursor.maxY = cursor.maxZ = Integer.MIN_VALUE;
            x.forMergedIndexes(cursor.xConsumer);
            // An empty native join uses these sentinel bounds, not the empty constructor's bounds.
            ((ShapeConstructionAccess) (Object) result).kernel$constructionBounds(
                cursor.minX, cursor.minY, cursor.minZ, cursor.maxX + 1, cursor.maxY + 1, cursor.maxZ + 1);
            return result;
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
            Cursor cursor;
            if (slot >= retained.length) cursor = new Cursor();
            else {
                cursor = retained[slot];
                if (cursor == null) retained[slot] = cursor = new Cursor();
            }
            depth++;
            return cursor;
        }
    }

    private static final class Cursor {
        private DiscreteVoxelShape first, second;
        private IndexMerger y, z;
        private BooleanOp operation;
        private BitSet storage;
        private int x1, x2, y1, y2, outputX, outputY, sizeY, sizeZ;
        private int minX, minY, minZ, maxX, maxY, maxZ;
        private boolean sliceFilled, rowFilled;
        private final IndexMerger.IndexConsumer zConsumer = (a, b, output) -> {
            if (operation.apply(first.isFullWide(x1, y1, a), second.isFullWide(x2, y2, b))) {
                storage.set((outputX * sizeY + outputY) * sizeZ + output);
                minZ = Math.min(minZ, output); maxZ = Math.max(maxZ, output);
                rowFilled = true;
            }
            return true;
        };
        private final IndexMerger.IndexConsumer yConsumer = (a, b, output) -> {
            y1 = a; y2 = b; outputY = output; rowFilled = false;
            z.forMergedIndexes(zConsumer);
            if (rowFilled) {
                minY = Math.min(minY, output); maxY = Math.max(maxY, output);
                sliceFilled = true;
            }
            return true;
        };
        private final IndexMerger.IndexConsumer xConsumer = (a, b, output) -> {
            x1 = a; x2 = b; outputX = output; sliceFilled = false;
            y.forMergedIndexes(yConsumer);
            if (sliceFilled) { minX = Math.min(minX, output); maxX = Math.max(maxX, output); }
            return true;
        };
        void clear() {
            first = second = null; y = z = null; operation = null; storage = null;
        }
    }
}
