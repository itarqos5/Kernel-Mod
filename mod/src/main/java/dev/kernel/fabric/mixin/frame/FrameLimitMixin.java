package dev.kernel.fabric.mixin.frame;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.kernel.fabric.frame.FrameSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FramerateLimitTracker.class)
public abstract class FrameLimitMixin {
    // Replace only the user's foreground cap. Native menu, minimized and AFK branches still apply.
    @ModifyExpressionValue(method = "getFramerateLimit", at = @At(value = "FIELD", target = "Lcom/mojang/blaze3d/platform/FramerateLimitTracker;framerateLimit:I"))
    private int kernel$monitorLimit(int original) { return FrameSync.limit(original); }
}
