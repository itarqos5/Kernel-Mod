package dev.kernel.fabric.mixin.world;

import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(NoiseChunk.NoiseInterpolator.class)
public abstract class NoiseInterpolatorMixin {
    /**
     * @author literal.uu
     * @reason Allocate each zero-filled interpolation row once, without replacing identical fresh rows.
     */
    @Overwrite
    private double[][] allocateSlice(int cellCountY, int cellCountZ) {
        return new double[cellCountZ + 1][cellCountY + 1];
    }
}
