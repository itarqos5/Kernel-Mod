package dev.kernel.fabric.verification.mixin;

import dev.kernel.fabric.shader.ShaderProjectionWorldChecks;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class ShaderProjectionProbeMixin {
    //? if >=26.2 {
    @Inject(method = "render", at = @At("HEAD"))
    //? } else {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    *///? }
    private void kernelProbe$projection(CallbackInfo callback) throws Exception { ShaderProjectionWorldChecks.observeNative(); }
}
