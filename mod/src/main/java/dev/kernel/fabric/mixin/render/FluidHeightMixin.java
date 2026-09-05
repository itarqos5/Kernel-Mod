package dev.kernel.fabric.mixin.render;

import org.spongepowered.asm.mixin.Mixin;

//? if >=26 {
import net.minecraft.client.renderer.block.FluidRenderer;
//?} else {
/*import dev.kernel.fabric.render.FluidHeightMath;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
*///?}

/**
 * Removes the temporary accumulator allocated for each legacy fluid-corner height calculation.
 */
//? if >=26 {
@Mixin(FluidRenderer.class)
//?} else {
/*@Mixin(LiquidBlockRenderer.class)
*///?}
public abstract class FluidHeightMixin {
    //? if <26 {
    /*@Shadow
    private float getHeight(BlockAndTintGetter blockAndTintGetter, Fluid fluid, BlockPos blockPos) {
        throw new AssertionError();
    }

    // Author: literal.uu
    // Reason: Accumulate weighted corner heights in scalar locals instead of allocating a two-float array.
    @Overwrite
    private float calculateAverageHeight(
        BlockAndTintGetter blockAndTintGetter,
        Fluid fluid,
        float center,
        float firstAdjacent,
        float secondAdjacent,
        BlockPos diagonalPos
    ) {
        if (secondAdjacent >= 1.0F || firstAdjacent >= 1.0F) {
            return 1.0F;
        }

        boolean includeDiagonal = secondAdjacent > 0.0F || firstAdjacent > 0.0F;
        float diagonal = includeDiagonal ? this.getHeight(blockAndTintGetter, fluid, diagonalPos) : -1.0F;
        return FluidHeightMath.weightedAverage(center, firstAdjacent, secondAdjacent, diagonal, includeDiagonal);
    }
    *///?}
}
