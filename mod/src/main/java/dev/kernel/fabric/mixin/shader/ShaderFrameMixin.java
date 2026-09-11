package dev.kernel.fabric.mixin.shader;

import dev.kernel.fabric.shader.KernelShaders;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ShaderFrameMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void kernel$prepareShaders(CallbackInfo callback) { KernelShaders.beginFrame(); }
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void kernel$renderShaders(CallbackInfo callback) { KernelShaders.renderWorld(); }
    @Inject(method = "close", at = @At("HEAD"))
    private void kernel$closeShaders(CallbackInfo callback) { KernelShaders.close(); }
}
