package dev.kernel.fabric.mixin.frame;

import dev.kernel.fabric.frame.FrameSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//? if >=26.2 {
import com.mojang.blaze3d.systems.GpuSurface;
@Mixin(GpuSurface.PresentMode.class)
//? } else {
/*import com.mojang.blaze3d.platform.Window;
@Mixin(Window.class)
*///? }
public abstract class FramePresentationMixin {
    //? if >=26.2 {
    @ModifyVariable(method = "getSupportedVsyncMode", at = @At("HEAD"), argsOnly = true)
    private static boolean kernel$synchronize(boolean original) {
    //? } else {
    /*@ModifyVariable(method = "updateVsync", at = @At("HEAD"), argsOnly = true)
    private boolean kernel$synchronize(boolean original) {
    *///? }
        return FrameSync.vsync(original);
    }
}
