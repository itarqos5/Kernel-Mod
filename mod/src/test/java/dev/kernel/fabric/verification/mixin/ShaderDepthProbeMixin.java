package dev.kernel.fabric.verification.mixin;

import dev.kernel.fabric.shader.ShaderPipeline;
import dev.kernel.fabric.shader.ShaderDepthWorldChecks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShaderPipeline.class, remap = false)
public abstract class ShaderDepthProbeMixin {
    @Inject(method = "captureDepth", at = @At("RETURN"))
    private void kernelProbe$depth(int[] sources, int width, int height, boolean reverse, boolean hand, CallbackInfo callback) throws Exception {
        ShaderDepthWorldChecks.observe((ShaderPipeline) (Object) this, sources, width, height, reverse, hand);
    }
}
