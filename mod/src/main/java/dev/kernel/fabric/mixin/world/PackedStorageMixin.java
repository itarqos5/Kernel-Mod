package dev.kernel.fabric.mixin.world;

import dev.kernel.fabric.world.PackedStorageDecoder;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Intercepts palette conversion only, leaving unrelated native storage calls untouched. */
@Mixin(PalettedContainer.class)
public abstract class PackedStorageMixin {
    //? if >=1.21.9 {
    @Redirect(method = "reencodeContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/BitStorage;unpack([I)V"))
    private static void kernel$unpackBlocks(BitStorage storage, int[] output) {
        PackedStorageDecoder.unpack(storage, output);
    }
    //? } else {
    /*@Redirect(method = "pack", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/BitStorage;unpack([I)V"))
    private void kernel$unpackBlocks(BitStorage storage, int[] output) {
        PackedStorageDecoder.unpack(storage, output);
    }
    @Redirect(method = "unpack", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/SimpleBitStorage;unpack([I)V"))
    private static void kernel$unpackLoadedBlocks(SimpleBitStorage storage, int[] output) {
        PackedStorageDecoder.unpack(storage, output);
    }
    *///? }
}
