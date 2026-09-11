package dev.kernel.fabric.verification.mixin;

import dev.kernel.fabric.verification.ChunkUploadProbe;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ChunkWorkProbeMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"), order = 3000)
    private void kernelProbe$controlledWork(CallbackInfo callback) { ChunkUploadProbe.addControlledWork(); }
}
