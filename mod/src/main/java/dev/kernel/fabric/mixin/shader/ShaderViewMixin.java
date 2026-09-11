package dev.kernel.fabric.mixin.shader;

import com.llamalad7.mixinextras.sugar.Local;
import dev.kernel.fabric.shader.KernelShaders;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Copies the exact world view argument and matching camera; native rendering keeps both objects. */
@Mixin(LevelRenderer.class)
public abstract class ShaderViewMixin {
    //? if >=26.1 {
    //? if >=26.2 {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    //? } else {
    /*@ModifyVariable(method = "renderLevel", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    *///? }
    private org.joml.Matrix4fc kernel$worldView(org.joml.Matrix4fc matrix,
        @Local(argsOnly = true) net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        KernelShaders.captureView(matrix, camera.pos);
        return matrix;
    }
    //? } else {
    /*@ModifyVariable(method = "renderLevel", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private org.joml.Matrix4f kernel$worldView(org.joml.Matrix4f matrix,
        @Local(argsOnly = true) net.minecraft.client.Camera camera) {
        //? if >=1.21.11 {
        KernelShaders.captureView(matrix, camera.position());
        //? } else {
        /^KernelShaders.captureView(matrix, camera.getPosition());
        ^///? }
        return matrix;
    }
    *///? }
}
