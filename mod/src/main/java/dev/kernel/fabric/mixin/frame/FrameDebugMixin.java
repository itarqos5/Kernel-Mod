package dev.kernel.fabric.mixin.frame;

import dev.kernel.fabric.frame.FrameSync;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.List;
//? if >=1.21.9 {
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//? } else {
/*import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///? }

@Mixin(DebugScreenOverlay.class)
public abstract class FrameDebugMixin {
    //? if >=1.21.9 {
    //? if >=26.1 {
    @ModifyVariable(method = "extractLines", at = @At("HEAD"), argsOnly = true)
    //? } else {
    /*@ModifyVariable(method = "renderLines", at = @At("HEAD"), argsOnly = true)
    *///? }
    private List<String> kernel$lines(List<String> original, @Local(argsOnly = true) boolean alignLeft) {
        return alignLeft ? FrameSync.debugLines(original) : original;
    }
    //? } else {
    /*@Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true)
    private void kernel$lines(CallbackInfoReturnable<List<String>> callback) {
        callback.setReturnValue(FrameSync.debugLines(callback.getReturnValue()));
    }
    *///? }
}
