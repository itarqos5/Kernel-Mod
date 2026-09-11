package dev.kernel.fabric.config;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Applies each optional group together, including accessors and task cancellation adapters. */
public final class KernelMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { KernelRendererSettings.active(); }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".world.BiomeManagerMixin")) return dev.kernel.fabric.world.WorldSettings.biomeOffsetsActive();
        if (mixinClassName.endsWith(".world.NoiseInterpolatorMixin")) return dev.kernel.fabric.world.WorldSettings.noiseSlicesActive();
        if (mixinClassName.endsWith(".world.EndIslandDensityMixin")) return dev.kernel.fabric.world.WorldSettings.endIslandHeightsActive();
        if (mixinClassName.endsWith(".world.ShapeJoinMixin")) return dev.kernel.fabric.world.WorldSettings.active(dev.kernel.fabric.world.WorldFeature.SHAPE_TRAVERSAL);
        RendererFeature feature = RendererFeature.forMixin(mixinClassName);
        return feature == null || KernelRendererSettings.enabled(feature);
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
