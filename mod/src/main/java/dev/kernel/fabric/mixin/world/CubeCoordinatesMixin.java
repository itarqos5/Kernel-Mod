package dev.kernel.fabric.mixin.world;

import dev.kernel.fabric.world.CubeCoordinateCache;
import net.minecraft.world.phys.shapes.CubePointRange;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CubeVoxelShape.class, priority = 900)
public abstract class CubeCoordinatesMixin {
    // Preserve the native per-call dimension query. Yield to standard-priority constructor owners.
    // The late order lets a skipped constructor's post-check see the winning redirect already applied.
    @Redirect(method = "getCoords", at = @At(value = "NEW", target = "(I)Lnet/minecraft/world/phys/shapes/CubePointRange;"),
        require = 0, expect = 0, allow = 1, order = 11000)
    private CubePointRange kernel$reuseCoordinates(int parts) { return CubeCoordinateCache.create(parts); }
}
