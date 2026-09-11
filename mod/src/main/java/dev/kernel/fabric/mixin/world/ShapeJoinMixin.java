package dev.kernel.fabric.mixin.world;

import dev.kernel.fabric.world.ShapeJoinTraversal;
import dev.kernel.fabric.world.ShapeGridIntersection;
import dev.kernel.fabric.world.ShapeMappedIntersection;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.IndexMerger;
import net.minecraft.world.phys.shapes.Shapes;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Shapes.class)
public abstract class ShapeJoinMixin {
    @WrapMethod(method = "joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/IndexMerger;Lnet/minecraft/world/phys/shapes/IndexMerger;Lnet/minecraft/world/phys/shapes/IndexMerger;Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z")
    private static boolean kernel$reuseTraversal(IndexMerger x, IndexMerger y, IndexMerger z,
                                             DiscreteVoxelShape first, DiscreteVoxelShape second, BooleanOp operation,
                                             Operation<Boolean> original) {
        // Custom mergers may retain callbacks; their original callback ownership must remain intact.
        if (!ShapeJoinTraversal.ownsCallbacks(x) || !ShapeJoinTraversal.ownsCallbacks(y) || !ShapeJoinTraversal.ownsCallbacks(z))
            return original.call(x, y, z, first, second, operation);
        int intersection = ShapeGridIntersection.test(x, y, z, first, second, operation);
        if (intersection < 0) intersection = ShapeMappedIntersection.test(x, y, z, first, second, operation);
        return intersection >= 0 ? intersection != 0 : ShapeJoinTraversal.test(x, y, z, first, second, operation);
    }
}
