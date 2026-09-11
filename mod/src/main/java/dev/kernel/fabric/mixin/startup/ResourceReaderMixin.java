package dev.kernel.fabric.mixin.startup;

import java.io.BufferedReader;
import java.io.Reader;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Resource.class, priority = 900)
public abstract class ResourceReaderMixin {
    // Keep the native stream supplier and UTF-8 decoder. Only the JDK character buffer changes.
    // A competing constructor redirect or overwritten factory may take ownership instead.
    // Apply after standard redirects so a skipped constructor's post-check sees its winning owner.
    @Redirect(method = "openAsReader", at = @At(value = "NEW", target = "(Ljava/io/Reader;)Ljava/io/BufferedReader;"),
        require = 0, expect = 0, allow = 1, order = 11000)
    private BufferedReader kernel$compactReader(Reader reader) {
        return new BufferedReader(reader, 2048);
    }
}
