package dev.kernel.fabric.verification.mixin;

import dev.kernel.fabric.verification.ShaderProbe;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ShaderWorldProbeMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"), order = 2000)
    private void kernelProbe$shaderWorld(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo callback) { ShaderProbe.verifyWorldFrame(deltaTracker); }
}
