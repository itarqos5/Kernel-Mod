package dev.kernel.fabric.resource.verification.mixin;

import java.io.BufferedReader;
import java.io.Reader;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Synthetic competing owner: preserve the native constructor and prove Kernel can yield. */
@Mixin(Resource.class)
public abstract class ResourceReaderOwnerMixin {
    @Redirect(method = "openAsReader", at = @At(value = "NEW", target = "java/io/BufferedReader"), require = 1, allow = 1)
    private BufferedReader kernelProbe$ownReader(Reader reader) {
        dev.kernel.fabric.resource.ResourceReaderSmokeChecks.ownedReaders.incrementAndGet();
        return new BufferedReader(reader);
    }
}
