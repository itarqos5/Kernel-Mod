package dev.kernel.fabric.mixin.startup;

import dev.kernel.fabric.bootstrap.StartupWindowBridge;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//? } else {
/*import net.minecraft.resources.ResourceLocation;
*///? }

@Mixin(FallbackResourceManager.class)
public abstract class ResourceProgressMixin {
    @Inject(method = "getResource", at = @At("HEAD"))
    //? if >=1.21.11 {
    private void kernel$asset(Identifier location, CallbackInfoReturnable<Optional<Resource>> callback) {
    //? } else {
    /*private void kernel$asset(ResourceLocation location, CallbackInfoReturnable<Optional<Resource>> callback) {
    *///? }
        StartupWindowBridge.asset(location);
    }
}
