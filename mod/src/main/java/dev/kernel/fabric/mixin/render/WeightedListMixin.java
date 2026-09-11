package dev.kernel.fabric.mixin.render;

import org.spongepowered.asm.mixin.Mixin;
//? if <=1.21.4 {
/*import dev.kernel.fabric.render.WeightedListAccess;
import net.minecraft.util.random.WeightedRandomList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
@Mixin(WeightedRandomList.class)
*///? } else {
@Mixin(net.minecraft.client.renderer.block.ModelBlockRenderer.class)
//? }
public abstract class WeightedListMixin
    //? if <=1.21.4 {
    /*implements WeightedListAccess
    *///? }
{
    //? if <=1.21.4 {
    /*@Shadow @Final private int totalWeight;
    @Shadow @Final private com.google.common.collect.ImmutableList<?> items;
    @Override public int kernel$totalWeight() { return totalWeight; }
    @Override public java.util.List<?> kernel$entries() { return items; }
    *///? }
}
