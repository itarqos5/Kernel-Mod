package dev.kernel.fabric.mixin.world;

import dev.kernel.fabric.world.CubeMergerAccess;
import net.minecraft.world.phys.shapes.DiscreteCubeMerger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(DiscreteCubeMerger.class)
public abstract class CubeMergerMixin implements CubeMergerAccess {
    @Shadow @Final private int firstDiv;
    @Shadow @Final private int secondDiv;
    @Override @Unique public boolean kernel$identityCoordinates() { return firstDiv == 1 && secondDiv == 1; }
}
