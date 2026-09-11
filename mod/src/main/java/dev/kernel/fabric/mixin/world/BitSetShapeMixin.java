package dev.kernel.fabric.mixin.world;

import dev.kernel.fabric.world.ShapeStorageAccess;
import java.util.BitSet;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BitSetDiscreteVoxelShape.class)
public abstract class BitSetShapeMixin implements ShapeStorageAccess {
    @Shadow @Final private BitSet storage;
    @Override @Unique public BitSet kernel$storage() { return storage; }
}
