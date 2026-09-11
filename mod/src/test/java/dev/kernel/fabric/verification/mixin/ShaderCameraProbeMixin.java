package dev.kernel.fabric.verification.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.kernel.fabric.shader.ShaderCameraWorldChecks;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class ShaderCameraProbeMixin {
    //? if >=26.2 {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;", shift = At.Shift.AFTER))
    //? } else {
    /*@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;mul(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;", shift = At.Shift.AFTER))
    *///? }
    //? if >=26.1 {
    private void kernelProbe$camera(CallbackInfo callback,
        @Local(argsOnly = true) net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        ShaderCameraWorldChecks.observe(camera.pos);
    }
    //? } else {
    /*private void kernelProbe$camera(CallbackInfo callback, @Local(argsOnly = true) net.minecraft.client.Camera camera) {
        //? if >=1.21.11 {
        ShaderCameraWorldChecks.observe(camera.position());
        //? } else {
        /^ShaderCameraWorldChecks.observe(camera.getPosition());
        ^///? }
    }
    *///? }
}
