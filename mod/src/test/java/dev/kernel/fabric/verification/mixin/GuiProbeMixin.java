package dev.kernel.fabric.verification.mixin;

import dev.kernel.fabric.verification.GuiProbe;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loaded only by the separate development probe mod, never by a release Kernel JAR. */
@Mixin(Minecraft.class)
public abstract class GuiProbeMixin {
    @Inject(method = "onGameLoadFinished", at = @At("RETURN"))
    private void kernelProbe$ready(CallbackInfo callback) { GuiProbe.ready(); }

    //? if >=26.2 {
    @Inject(method = "renderFrame", at = @At("RETURN"))
    //? } else {
    /*@Inject(method = "runTick", at = @At("RETURN"))
    *///? }
    private void kernelProbe$frame(CallbackInfo callback) { GuiProbe.frame(Minecraft.getInstance()); }
}
