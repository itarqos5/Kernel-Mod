package dev.kernel.fabric.verification;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Installs assertion instrumentation only in the isolated probe that consumes its results. */
public final class GuiProbeMixinPlugin implements IMixinConfigPlugin {
    private final boolean world, shaders, chunks;

    public GuiProbeMixinPlugin() {
        this(Boolean.getBoolean("kernel.guiProbe.worldGeneration"),
            Boolean.getBoolean("kernel.guiProbe.shaders"), Boolean.getBoolean("kernel.guiProbe.chunkBudget"));
    }
    GuiProbeMixinPlugin(boolean world, boolean shaders, boolean chunks) {
        this.world = world; this.shaders = shaders; this.chunks = chunks;
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return switch (mixin.substring(mixin.lastIndexOf('.') + 1)) {
            case "GuiProbeMixin" -> true;
            case "BiomeProbeMixin" -> world;
            case "ShaderWorldProbeMixin", "ShaderDepthProbeMixin", "ShaderProjectionProbeMixin",
                "ShaderCameraProbeMixin", "ChunkUniformProbeMixin" -> shaders;
            case "ChunkWorkProbeMixin" -> chunks;
            default -> throw new IllegalArgumentException("Unknown GUI probe mixin: " + mixin);
        };
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
