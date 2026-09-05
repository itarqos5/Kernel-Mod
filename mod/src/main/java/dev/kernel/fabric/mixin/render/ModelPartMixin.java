package dev.kernel.fabric.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kernel.fabric.render.ModelRenderScratch;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Removes transient quaternion allocation from animated model-part transforms.
 */
@Mixin(ModelPart.class)
public abstract class ModelPartMixin {
    @Shadow
    public float x;

    @Shadow
    public float y;

    @Shadow
    public float z;

    @Shadow
    public float xRot;

    @Shadow
    public float yRot;

    @Shadow
    public float zRot;

    @Shadow
    public float xScale;

    @Shadow
    public float yScale;

    @Shadow
    public float zScale;

    // Author: literal.uu
    // Reason: Reuse a per-thread quaternion instead of allocating one for every rotated model part.
    @Overwrite
    public void translateAndRotate(PoseStack poseStack) {
        poseStack.translate(this.x / 16.0F, this.y / 16.0F, this.z / 16.0F);
        if (this.xRot != 0.0F || this.yRot != 0.0F || this.zRot != 0.0F) {
            poseStack.mulPose(ModelRenderScratch.get().rotationZYX(this.zRot, this.yRot, this.xRot));
        }

        if (this.xScale != 1.0F || this.yScale != 1.0F || this.zScale != 1.0F) {
            poseStack.scale(this.xScale, this.yScale, this.zScale);
        }
    }
}
