package dev.kernel.fabric.mixin.shader;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.5 && <1.21.6 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.kernel.fabric.shader.KernelShadowPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///? }

/**
 * Renders the shadow map, and points the terrain draw at it while that is happening.
 *
 * <p>The shadow pass runs immediately before the solid terrain layer, which is the first moment the
 * frame's visible-section list is complete and the last before anything is drawn with it. Both
 * redirections below are scoped to that one draw method, so nothing else in the renderer is affected,
 * and both return the original outside the pass.
 */
@Mixin(LevelRenderer.class)
public abstract class ShaderShadowMixin {
    //? if >=1.21.5 && <1.21.6 {
    /*@Inject(method = "renderSectionLayer", at = @At("HEAD"))
    private void kernel$renderShadowMap(RenderType type, double cameraX, double cameraY, double cameraZ,
                                        Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo callback) {
        if (type != RenderType.solid() || !KernelShadowPass.casting()) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        KernelShadowPass.render(minecraft, minecraft.level.getTimeOfDay(partialTick), cameraX, cameraY, cameraZ);
    }

    @ModifyExpressionValue(method = "renderSectionLayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;getRenderPipeline()Lcom/mojang/blaze3d/pipeline/RenderPipeline;"))
    private RenderPipeline kernel$shadowPipeline(RenderPipeline original) { return KernelShadowPass.pipeline(original); }

    @ModifyExpressionValue(method = "renderSectionLayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;getRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget kernel$shadowTarget(RenderTarget original) { return KernelShadowPass.target(original); }
    *///? }
}
