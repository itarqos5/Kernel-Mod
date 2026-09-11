package dev.kernel.fabric.mixin.shader;

import dev.kernel.fabric.shader.KernelShaders;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Copies the world projection actually uploaded after view bobbing and screen distortion. */
@Mixin(GameRenderer.class)
public abstract class ShaderProjectionMixin {
    //? if >=26.1 {
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 0)
    //? } elif >=1.21.6 {
    /*@ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PerspectiveProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 0)
    *///? } else {
    /*@ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/ProjectionType;)V"), index = 0)
    *///? }
    private Matrix4f kernel$worldProjection(Matrix4f matrix) {
        KernelShaders.captureProjection(matrix);
        return matrix;
    }
}
