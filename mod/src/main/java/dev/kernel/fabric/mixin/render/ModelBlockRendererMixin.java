package dev.kernel.fabric.mixin.render;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;

//? if <26 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kernel.fabric.render.ReentrantThreadLocalPool;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
*///?}

//? if <=1.21.4 {
/*import dev.kernel.fabric.render.BlockQuadUploadScratch;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;
*///?}

//? if >1.21.4 && <26 {
/*import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
*///?}

/**
 * Removes avoidable temporary storage from block-model rendering.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    //? if <26 {
    /*@Shadow
    @Final
    private static Direction[] DIRECTIONS;

    @Unique
    private static final ReentrantThreadLocalPool<RandomSource> KERNEL_MODEL_RANDOM = new ReentrantThreadLocalPool<>(
        () -> RandomSource.create(42L),
        randomSource -> randomSource.setSeed(42L)
    );

    @Shadow
    private static void renderQuadList(
        PoseStack.Pose pose,
        VertexConsumer vertexConsumer,
        float red,
        float green,
        float blue,
        List<BakedQuad> quads,
        int light,
        int overlay
    ) {
        throw new AssertionError();
    }
    *///?}

    //? if <=1.21.4 {
    /*@Shadow
    @Final
    private BlockColors blockColors;

    @Unique
    private static final ReentrantThreadLocalPool<BlockQuadUploadScratch> KERNEL_QUAD_UPLOAD_SCRATCH =
        new ReentrantThreadLocalPool<>(BlockQuadUploadScratch::new, ignored -> {
        });

    // Author: literal.uu
    // Reason: Reuse the fixed-seed random source instead of allocating one for every standalone model render.
    @Overwrite
    public void renderModel(
        PoseStack.Pose pose,
        VertexConsumer vertexConsumer,
        @Nullable BlockState blockState,
        BakedModel bakedModel,
        float red,
        float green,
        float blue,
        int light,
        int overlay
    ) {
        RandomSource randomSource = KERNEL_MODEL_RANDOM.acquire();
        try {
            for (Direction direction : DIRECTIONS) {
                randomSource.setSeed(42L);
                renderQuadList(pose, vertexConsumer, red, green, blue, bakedModel.getQuads(blockState, direction, randomSource), light, overlay);
            }

            randomSource.setSeed(42L);
            renderQuadList(pose, vertexConsumer, red, green, blue, bakedModel.getQuads(blockState, null, randomSource), light, overlay);
        } finally {
            KERNEL_MODEL_RANDOM.release();
        }
    }

    // Author: literal.uu
    // Reason: Reuse brightness and light arrays instead of allocating both for every emitted block quad.
    @Inject(method = "putQuadData", at = @At("HEAD"), cancellable = true)
    private void kernel$putQuadData(
        BlockAndTintGetter blockAndTintGetter,
        BlockState blockState,
        BlockPos blockPos,
        VertexConsumer vertexConsumer,
        PoseStack.Pose pose,
        BakedQuad bakedQuad,
        float firstBrightness,
        float secondBrightness,
        float thirdBrightness,
        float fourthBrightness,
        int firstLight,
        int secondLight,
        int thirdLight,
        int fourthLight,
        int overlay,
        CallbackInfo callbackInfo
    ) {
        float red;
        float green;
        float blue;
        if (bakedQuad.isTinted()) {
            int color = this.blockColors.getColor(blockState, blockAndTintGetter, blockPos, bakedQuad.getTintIndex());
            red = (color >> 16 & 0xFF) / 255.0F;
            green = (color >> 8 & 0xFF) / 255.0F;
            blue = (color & 0xFF) / 255.0F;
        } else {
            red = 1.0F;
            green = 1.0F;
            blue = 1.0F;
        }

        BlockQuadUploadScratch scratch = KERNEL_QUAD_UPLOAD_SCRATCH.acquire();
        try {
            vertexConsumer.putBulkData(
                pose,
                bakedQuad,
                scratch.brightness(firstBrightness, secondBrightness, thirdBrightness, fourthBrightness),
                red,
                green,
                blue,
                1.0F,
                scratch.light(firstLight, secondLight, thirdLight, fourthLight),
                overlay,
                true
            );
        } finally {
            KERNEL_QUAD_UPLOAD_SCRATCH.release();
        }
        callbackInfo.cancel();
    }
    *///?}

    //? if >1.21.4 && <26 {
    /*// Author: literal.uu
    // Reason: Reuse the fixed-seed random source instead of allocating one for every standalone model render.
    @Overwrite
    public static void renderModel(
        PoseStack.Pose pose,
        VertexConsumer vertexConsumer,
        BlockStateModel blockStateModel,
        float red,
        float green,
        float blue,
        int light,
        int overlay
    ) {
        RandomSource randomSource = KERNEL_MODEL_RANDOM.acquire();
        try {
            for (BlockModelPart blockModelPart : blockStateModel.collectParts(randomSource)) {
                for (Direction direction : DIRECTIONS) {
                    renderQuadList(pose, vertexConsumer, red, green, blue, blockModelPart.getQuads(direction), light, overlay);
                }

                renderQuadList(pose, vertexConsumer, red, green, blue, blockModelPart.getQuads(null), light, overlay);
            }
        } finally {
            KERNEL_MODEL_RANDOM.release();
        }
    }
    *///?}
}
