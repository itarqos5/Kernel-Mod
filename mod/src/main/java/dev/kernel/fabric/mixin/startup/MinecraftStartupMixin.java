package dev.kernel.fabric.mixin.startup;

import dev.kernel.fabric.bootstrap.StartupCacheControl;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftStartupMixin {
    @Inject(method = "onGameLoadFinished", at = @At("RETURN"), require = 0)
    private void kernel$releaseStartupCaches(CallbackInfo ci) {
        StartupCacheControl.finish();
        dev.kernel.fabric.config.KernelHardwareSettings.applyOnce((Minecraft) (Object) this);
    }
}
