package dev.kernel.fabric.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.kernel.fabric.world.ShapeConstruction;
import dev.kernel.fabric.world.ShapeConstructionAccess;
import dev.kernel.fabric.world.ShapeJoinTraversal;
import java.util.BitSet;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.IndexMerger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BitSetDiscreteVoxelShape.class)
public abstract class ShapeConstructionMixin implements ShapeConstructionAccess {
    @Shadow @Final private BitSet storage;
    @Shadow private int xMin;
    @Shadow private int yMin;
    @Shadow private int zMin;
    @Shadow private int xMax;
    @Shadow private int yMax;
    @Shadow private int zMax;

    @Override @Unique public BitSet kernel$constructionStorage() { return storage; }
    @Override @Unique public void kernel$constructionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        xMin = minX; yMin = minY; zMin = minZ; xMax = maxX; yMax = maxY; zMax = maxZ;
    }

    @WrapMethod(method = "join")
    private static BitSetDiscreteVoxelShape kernel$reuseConstruction(DiscreteVoxelShape first, DiscreteVoxelShape second,
        IndexMerger x, IndexMerger y, IndexMerger z, BooleanOp operation, Operation<BitSetDiscreteVoxelShape> original) {
        if (!ShapeJoinTraversal.ownsCallbacks(x) || !ShapeJoinTraversal.ownsCallbacks(y) || !ShapeJoinTraversal.ownsCallbacks(z))
            return original.call(first, second, x, y, z, operation);
        return ShapeConstruction.join(first, second, x, y, z, operation);
    }
}
