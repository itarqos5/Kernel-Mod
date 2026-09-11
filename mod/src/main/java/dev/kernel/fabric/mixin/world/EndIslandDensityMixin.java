package dev.kernel.fabric.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.kernel.fabric.world.EndIslandCache;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$EndIslandDensityFunction")
public abstract class EndIslandDensityMixin {
    @Unique private static final ThreadLocal<EndIslandCache> kernel$islandHeights = ThreadLocal.withInitial(EndIslandCache::new);

    @WrapMethod(method = "getHeightValue")
    private static float kernel$reuseIslandHeight(SimplexNoise noise, int x, int z, Operation<Float> original) {
        // Subclasses may expose changing values or observable calls; retain their native evaluation.
        if (noise == null || noise.getClass() != SimplexNoise.class) return original.call(noise, x, z);
        var cache = kernel$islandHeights.get();
        int slot = cache.find(noise, x, z);
        if (slot >= 0) return cache.value(slot);
        float value = original.call(noise, x, z);
        // Resolve the owner again after the callback, which may be reentrant through another mod.
        cache.store(noise, x, z, value);
        return value;
    }
}
