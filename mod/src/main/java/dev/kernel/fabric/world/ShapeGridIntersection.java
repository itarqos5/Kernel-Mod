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

/** Borrows current native occupancy only when all three coordinate mappings are identical. */
public final class ShapeGridIntersection {
    private ShapeGridIntersection() {}

    /** Returns -1 to preserve the normal traversal, 0 for disjoint occupancy, or 1 for an intersection. */
    public static int test(IndexMerger x, IndexMerger y, IndexMerger z,
                           DiscreteVoxelShape first, DiscreteVoxelShape second, BooleanOp operation) {
        if (operation != BooleanOp.AND
            || !(first instanceof ShapeStorageAccess a) || first.getClass() != BitSetDiscreteVoxelShape.class
            || !(second instanceof ShapeStorageAccess b) || second.getClass() != BitSetDiscreteVoxelShape.class) return -1;
        int sx = first.getXSize(), sy = first.getYSize(), sz = first.getZSize();
        if (sx != second.getXSize() || sy != second.getYSize() || sz != second.getZSize()
            || !identity(x, sx) || !identity(y, sy) || !identity(z, sz)) return -1;
        long cells = (long) sx * sy;
        if (cells > Integer.MAX_VALUE) return -1;
        cells *= sz;
        if (cells > Integer.MAX_VALUE) return -1;
        BitSet firstBits = a.kernel$storage(), secondBits = b.kernel$storage();
        if (firstBits == null || secondBits == null || firstBits.getClass() != BitSet.class || secondBits.getClass() != BitSet.class
            || firstBits.length() > cells || secondBits.length() > cells) return -1;
        // Preserve the cheap first-cell success case before BitSet's reverse word scan.
        return firstBits.get(0) && secondBits.get(0) || firstBits.intersects(secondBits) ? 1 : 0;
    }

    private static boolean identity(IndexMerger merger, int cells) {
        if (merger == null) return false;
        Class<?> type = merger.getClass();
        if (type != IdenticalMerger.class && type != DiscreteCubeMerger.class) return false;
        var coordinates = merger.getList();
        // Custom lists may make size() observable. Do not add calls to their original traversal.
        if (coordinates == null || coordinates.getClass() != DoubleArrayList.class && coordinates.getClass() != CubePointRange.class) return false;
        if ((long) coordinates.size() != (long) cells + 1) return false;
        return type == IdenticalMerger.class || merger instanceof CubeMergerAccess cube && cube.kernel$identityCoordinates();
    }
}
