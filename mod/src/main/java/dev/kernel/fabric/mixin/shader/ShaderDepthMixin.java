package dev.kernel.fabric.mixin.shader;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import dev.kernel.fabric.shader.KernelShaders;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class ShaderDepthMixin {
    @Shadow @Final private LevelTargetBundle targets;
    @Unique private boolean kernel$cloudDepth;
    //? if >=26.2 {
    @Inject(method = "render", at = @At("HEAD"))
    //? } else {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    *///? }
    private void kernel$beginDepth(CallbackInfo callback) { kernel$cloudDepth = false; }
    @Inject(method = "addCloudsPass", at = @At("HEAD"))
    private void kernel$cloudDepth(CallbackInfo callback) { kernel$cloudDepth = true; }
    //? if >=26.2 {
    @Inject(method = "addAlwaysOnTopPass", at = @At("HEAD"))
    //? } else {
    /*@Inject(method = "addLateDebugPass", at = @At("HEAD"))
    *///? }
    private void kernel$captureDepth(CallbackInfo callback, @Local(argsOnly = true) FrameGraphBuilder graph) {
        KernelShaders.scheduleDepth(graph, targets, kernel$cloudDepth);
    }
}
