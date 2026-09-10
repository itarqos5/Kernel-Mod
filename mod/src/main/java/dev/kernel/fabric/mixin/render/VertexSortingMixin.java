package dev.kernel.fabric.mixin.render;

import com.mojang.blaze3d.vertex.VertexSorting;
import dev.kernel.fabric.render.KernelVertexSorting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VertexSorting.class)
public interface VertexSortingMixin {
    @Inject(
        method = "byDistance(Lcom/mojang/blaze3d/vertex/VertexSorting$DistanceFunction;)Lcom/mojang/blaze3d/vertex/VertexSorting;",
        at = @At("RETURN"), cancellable = true
    )
    private static void kernel$stableSorting(VertexSorting.DistanceFunction function, CallbackInfoReturnable<VertexSorting> callback) {
        callback.setReturnValue(new KernelVertexSorting(function, callback.getReturnValue()));
    }
}
