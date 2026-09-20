package dev.kernel.fabric.mixin.shader;

import org.spongepowered.asm.mixin.Mixin;
//? if >1.21.4 && <=1.21.10 {
/*import dev.kernel.fabric.render.KernelTerrainAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
*///? }

/**
 * Tells Kernel which block is being tessellated, so a shader pack's vertex attributes can name it.
 *
 * <p>Both of the chunk builder's block paths are covered, because a pack's identity map describes fluids
 * as readily as it describes blocks and a vertex left unrecorded would be given the identity zero that
 * means "not one of mine".
 *
 * <p>The record is cleared afterwards rather than left standing. The same thread goes on to build other
 * geometry, and a vertex written outside a block would otherwise inherit whichever block happened to be
 * tessellated last.
 *
 * <p>Carried only on the targets Kernel extends the vertex format for. The later ones reorganised block
 * rendering and have no dispatcher of this shape, and they refuse world programs anyway; a mixin must
 * still name a class that exists to be loaded, so there it is attached to the model renderer and carries
 * no injector.
 */
//? if >=1.21.11 {
@Mixin(net.minecraft.client.renderer.block.ModelBlockRenderer.class)
//? } else {
/*@Mixin(net.minecraft.client.renderer.block.BlockRenderDispatcher.class)
*///? }
public abstract class BlockTessellationMixin {
    //? if >1.21.4 && <=1.21.10 {
    /*@Inject(method = "renderBatched", at = @At("HEAD"))
    private void kernel$beginBlock(BlockState state, BlockPos position, BlockAndTintGetter level, PoseStack poseStack,
                                   VertexConsumer consumer, boolean checkSides, List<?> parts, CallbackInfo callback) {
        KernelTerrainAttributes.begin(state, position);
    }

    @Inject(method = "renderBatched", at = @At("RETURN"))
    private void kernel$endBlock(BlockState state, BlockPos position, BlockAndTintGetter level, PoseStack poseStack,
                                 VertexConsumer consumer, boolean checkSides, List<?> parts, CallbackInfo callback) {
        KernelTerrainAttributes.end();
    }

    @Inject(method = "renderLiquid", at = @At("HEAD"))
    private void kernel$beginLiquid(BlockPos position, BlockAndTintGetter level, VertexConsumer consumer,
                                    BlockState state, FluidState fluid, CallbackInfo callback) {
        KernelTerrainAttributes.begin(state, position);
    }

    @Inject(method = "renderLiquid", at = @At("RETURN"))
    private void kernel$endLiquid(BlockPos position, BlockAndTintGetter level, VertexConsumer consumer,
                                  BlockState state, FluidState fluid, CallbackInfo callback) {
        KernelTerrainAttributes.end();
    }
    *///? }
}
