package dev.kernel.fabric.mixin.world;

import dev.kernel.fabric.world.IndirectMergerAccess;
import net.minecraft.world.phys.shapes.IndirectMerger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(IndirectMerger.class)
public abstract class IndirectMergerMixin implements IndirectMergerAccess {
    @Shadow @Final private int[] firstIndices;
    @Shadow @Final private int[] secondIndices;

    @Override @Unique
    public long kernel$firstIndexPair() { return (firstIndices[0] & 0xffffffffL) | ((long) secondIndices[0] << 32); }
}
