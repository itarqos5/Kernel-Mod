package dev.kernel.fabric.mixin.render;

import dev.kernel.fabric.render.BlockFaceVisibilityCache;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Avoids allocating a compound key while checking cached block-face visibility.
 */
@Mixin(Block.class)
public abstract class BlockMixin {
    // Author: literal.uu
    // Reason: Preserve the vanilla visibility test while using an allocation-free bounded identity cache.
    @Overwrite
    public static boolean shouldRenderFace(BlockState blockState, BlockState neighborState, Direction direction) {
        VoxelShape neighborShape = neighborState.getFaceOcclusionShape(direction.getOpposite());
        if (neighborShape == Shapes.block()) {
            return false;
        }

        if (blockState.skipRendering(neighborState, direction)) {
            return false;
        }

        if (neighborShape == Shapes.empty()) {
            return true;
        }

        VoxelShape blockShape = blockState.getFaceOcclusionShape(direction);
        if (blockShape == Shapes.empty()) {
            return true;
        }

        BlockFaceVisibilityCache cache = BlockFaceVisibilityCache.get();
        byte cached = cache.find(blockShape, neighborShape);
        if (cached != BlockFaceVisibilityCache.MISS) {
            return cached == BlockFaceVisibilityCache.VISIBLE;
        }

        boolean visible = Shapes.joinIsNotEmpty(blockShape, neighborShape, BooleanOp.ONLY_FIRST);
        cache.put(blockShape, neighborShape, visible);
        return visible;
    }
}
