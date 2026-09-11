package dev.kernel.fabric.mixin.render;

import org.spongepowered.asm.mixin.Mixin;
//? if <=1.21.4 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.kernel.fabric.render.WeightedListAccess;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
@Mixin(WeightedBakedModel.class)
*///? } else {
@Mixin(net.minecraft.client.renderer.block.ModelBlockRenderer.class)
//? }
public abstract class WeightedModelMixin {
    //? if <=1.21.4 {
    /*@Shadow @Final private SimpleWeightedRandomList<BakedModel> list;

    @WrapMethod(method = "getQuads")
    private List<BakedQuad> kernel$dispatch(BlockState state, Direction face, RandomSource random,
                                           Operation<List<BakedQuad>> original) {
        if (((Object) this).getClass() != WeightedBakedModel.class || list.getClass() != SimpleWeightedRandomList.class
            || !(list instanceof WeightedListAccess access)) return original.call(state, face, random);
        int total = access.kernel$totalWeight();
        if (total < 0) return original.call(state, face, random);
        if (total == 0) return Collections.emptyList();
        int remaining = random.nextInt(total);
        var entries = access.kernel$entries();
        for (int index = 0, size = entries.size(); index < size; index++) {
            var entry = (WeightedEntry) entries.get(index);
            remaining -= entry.getWeight().asInt();
            if (remaining < 0) {
                var model = (BakedModel) ((WeightedEntry.Wrapper<?>) entry).data();
                if (model == null) return Collections.emptyList();
                var quads = model.getQuads(state, face, random);
                return quads == null ? Collections.emptyList() : quads;
            }
        }
        return Collections.emptyList();
    }
    *///? }
}
