package dev.kernel.fabric.mixin.render;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;

//? if <=1.21.4 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kernel.fabric.render.BlockQuadUploadScratch;
import dev.kernel.fabric.render.ReentrantThreadLocalPool;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * Removes temporary lighting arrays from legacy block-model quad uploads.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    //? if <=1.21.4 {
    /*@Shadow
    @Final
    private BlockColors blockColors;

    @Unique
    private static final ReentrantThreadLocalPool<BlockQuadUploadScratch> KERNEL_QUAD_UPLOAD_SCRATCH =
        new ReentrantThreadLocalPool<>(BlockQuadUploadScratch::new, ignored -> {
        });

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
}
