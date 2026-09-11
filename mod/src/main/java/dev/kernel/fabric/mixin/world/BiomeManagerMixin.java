package dev.kernel.fabric.mixin.world;

import dev.kernel.fabric.world.BiomeJitterCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BiomeManager.class)
public abstract class BiomeManagerMixin {
    @Shadow @Final private long biomeZoomSeed;
    @Shadow @Final private BiomeManager.NoiseBiomeSource noiseBiomeSource;
    @Shadow private static double getFiddle(long value) { throw new AssertionError(); }
    @Unique private static final ThreadLocal<BiomeJitterCache> kernel$offsets = ThreadLocal.withInitial(
        () -> new BiomeJitterCache(LinearCongruentialGenerator::next, BiomeManagerMixin::getFiddle));

    /**
     * @author literal.uu
     * @reason Reuse immutable corner offsets while preserving native selection and biome-source lookup.
     */
    @Overwrite
    public Holder<Biome> getBiome(BlockPos position) {
        int x = position.getX(), y = position.getY(), z = position.getZ();
        int corner = kernel$offsets.get().nearest(biomeZoomSeed, x, y, z);
        return noiseBiomeSource.getNoiseBiome(((x - 2) >> 2) + ((corner & 4) == 0 ? 0 : 1),
            ((y - 2) >> 2) + ((corner & 2) == 0 ? 0 : 1), ((z - 2) >> 2) + ((corner & 1) == 0 ? 0 : 1));
    }
}
