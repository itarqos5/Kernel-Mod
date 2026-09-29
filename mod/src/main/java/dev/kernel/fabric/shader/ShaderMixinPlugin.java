package dev.kernel.fabric.shader;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Keeps the shader hooks out of a launch that has already given up on shaders.
 *
 * <p>Shaders can be switched off before any of them applies — by a failure recorded earlier in the same
 * launch, or by {@code -Dkernel.shaders=false} — and applying half of a set of hooks whose other half is
 * gone is worse than applying none.
 */
public final class ShaderMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {
        if (!Boolean.parseBoolean(System.getProperty("kernel.shaders", "true")))
            ShaderSupport.disable("Shaders were turned off with -Dkernel.shaders=false", null);
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return ShaderSupport.available(); }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
