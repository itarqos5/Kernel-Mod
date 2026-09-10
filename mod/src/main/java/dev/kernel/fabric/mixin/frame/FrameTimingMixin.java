package dev.kernel.fabric.mixin.frame;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.kernel.fabric.frame.FrameSync;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.2 {
import com.mojang.blaze3d.systems.GpuSurface;
//? } else {
/*import com.mojang.blaze3d.TracyFrameCapture;
*///? }
//? if <26.1 {
/*import com.mojang.blaze3d.platform.Window;
*///? }

@Mixin(Minecraft.class)
public abstract class FrameTimingMixin {
    //? if >=26.1 {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    //? } else {
    /*@Inject(method = "runTick", at = @At("HEAD"))
    *///? }
    private void kernel$begin(CallbackInfo callback) { FrameSync.beginFrame(); }
    //? if >=26.1 {
    @Inject(method = "renderFrame", at = @At("RETURN"))
    //? } else {
    /*@Inject(method = "runTick", at = @At("RETURN"))
    *///? }
    private void kernel$end(CallbackInfo callback) { FrameSync.endFrame(); }
    @Inject(method = "close", at = @At("HEAD"))
    private void kernel$close(CallbackInfo callback) { FrameSync.close(); }

    //? if >=26.2 {
    @WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurface;present()V"))
    private void kernel$present(GpuSurface surface, Operation<Void> original) {
        FrameSync.beginWait();
        try { original.call(surface); } finally { FrameSync.endWait(); }
    }
    //? } elif >=26.1 {
    /*@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;flipFrame(Lcom/mojang/blaze3d/TracyFrameCapture;)V"))
    private void kernel$present(TracyFrameCapture capture, Operation<Void> original) {
        FrameSync.beginWait();
        try { original.call(capture); } finally { FrameSync.endWait(); }
    }
    *///? } else {
    /*@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay(Lcom/mojang/blaze3d/TracyFrameCapture;)V"))
    private void kernel$present(Window window, TracyFrameCapture capture, Operation<Void> original) {
        FrameSync.beginWait();
        try { original.call(window, capture); } finally { FrameSync.endWait(); }
    }
    *///? }

    //? if >=26.1 {
    @WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/FramerateLimiter;limitDisplayFPS(I)V"))
    //? } else {
    /*@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
    *///? }
    private void kernel$limiter(int limit, Operation<Void> original) {
        // Synchronized presentation already paces to this monitor. Sleeping again misses vblanks.
        // Lower native menu/idle limits still use Minecraft's limiter.
        if (FrameSync.synchronizedLimit(limit)) return;
        FrameSync.beginWait();
        try { original.call(limit); } finally { FrameSync.endWait(); }
    }
}
