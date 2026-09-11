package dev.kernel.fabric.verification.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Test-only assertion at the actual biome source boundary, including all world-generation workers. */
@Mixin(value = BiomeManager.class, priority = 1100)
public abstract class BiomeProbeMixin {
    @Shadow @Final private long biomeZoomSeed;
    @Shadow private static double getFiddledDistance(long seed, int x, int y, int z, double dx, double dy, double dz) { throw new AssertionError(); }
    @Unique private static final boolean kernelProbe$compare = Boolean.getBoolean("kernel.guiProbe.worldGeneration");

    @WrapOperation(method = "getBiome", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/BiomeManager$NoiseBiomeSource;getNoiseBiome(III)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> kernelProbe$source(BiomeManager.NoiseBiomeSource source, int actualX, int actualY, int actualZ,
                                           Operation<Holder<Biome>> original, @Local(argsOnly = true) BlockPos position) {
        if (kernelProbe$compare) {
            int bx = position.getX() - 2, by = position.getY() - 2, bz = position.getZ() - 2;
            int x = bx >> 2, y = by >> 2, z = bz >> 2;
            double fx = (bx & 3) / 4.0, fy = (by & 3) / 4.0, fz = (bz & 3) / 4.0;
            int best = 0; double minimum = Double.POSITIVE_INFINITY;
            for (int corner = 0; corner < 8; corner++) {
                int ox = (corner & 4) == 0 ? 0 : 1, oy = (corner & 2) == 0 ? 0 : 1, oz = (corner & 1) == 0 ? 0 : 1;
                double distance = getFiddledDistance(biomeZoomSeed, x + ox, y + oy, z + oz, fx - ox, fy - oy, fz - oz);
                if (minimum > distance) { minimum = distance; best = corner; }
            }
            if (actualX != x + ((best & 4) == 0 ? 0 : 1) || actualY != y + ((best & 2) == 0 ? 0 : 1)
                || actualZ != z + ((best & 1) == 0 ? 0 : 1)) throw new AssertionError("World generation chose a different biome than vanilla at " + position);
        }
        return original.call(source, actualX, actualY, actualZ);
    }
}
