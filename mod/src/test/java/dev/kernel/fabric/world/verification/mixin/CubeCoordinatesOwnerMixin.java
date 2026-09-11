package dev.kernel.fabric.world.verification.mixin;

import net.minecraft.world.phys.shapes.CubePointRange;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Ordinary-priority synthetic owner with the native constructor's behavior. */
@Mixin(CubeVoxelShape.class)
public abstract class CubeCoordinatesOwnerMixin {
    @Redirect(method = "getCoords", at = @At(value = "NEW", target = "net/minecraft/world/phys/shapes/CubePointRange"), require = 1, allow = 1)
    private CubePointRange kernelProbe$ownCoordinates(int parts) {
        dev.kernel.fabric.world.ShapeCoordinatesSmokeChecks.competingCalls.incrementAndGet();
        return new CubePointRange(parts);
    }
}
