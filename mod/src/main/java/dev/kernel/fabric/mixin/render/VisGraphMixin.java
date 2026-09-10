package dev.kernel.fabric.mixin.render;

import dev.kernel.fabric.render.SectionVisibilityAdapter;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.BitSet;

@Mixin(VisGraph.class)
public abstract class VisGraphMixin {
    @Shadow @Final private BitSet bitSet;
    @Shadow private int empty;

    // Author: literal.uu
    // Reason: Resolve section occlusion with reusable scanline traversal instead of per-component cell queues.
    @Overwrite
    public VisibilitySet resolve() {
        return SectionVisibilityAdapter.resolve(this.bitSet, this.empty);
    }
}
