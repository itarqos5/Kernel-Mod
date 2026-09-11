package dev.kernel.fabric.mixin.world;

import dev.kernel.fabric.world.ShapeJoinTraversal;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.IndexMerger;
import net.minecraft.world.phys.shapes.Shapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shapes.class)
public abstract class ShapeJoinMixin {
    @Inject(method = "joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/IndexMerger;Lnet/minecraft/world/phys/shapes/IndexMerger;Lnet/minecraft/world/phys/shapes/IndexMerger;Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z", at = @At("HEAD"), cancellable = true)
    private static void kernel$reuseTraversal(IndexMerger x, IndexMerger y, IndexMerger z,
                                             DiscreteVoxelShape first, DiscreteVoxelShape second, BooleanOp operation,
                                             CallbackInfoReturnable<Boolean> result) {
        // Custom mergers may retain callbacks; their original callback ownership must remain intact.
        if (ShapeJoinTraversal.ownsCallbacks(x) && ShapeJoinTraversal.ownsCallbacks(y) && ShapeJoinTraversal.ownsCallbacks(z))
            result.setReturnValue(ShapeJoinTraversal.test(x, y, z, first, second, operation));
    }
}
