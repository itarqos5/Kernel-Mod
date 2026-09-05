package dev.kernel.fabric.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.MatrixUtil;
import dev.kernel.fabric.render.ModelRenderScratch;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Removes temporary normal-matrix allocation from pooled pose entries.
 */
@Mixin(PoseStack.Pose.class)
public abstract class PoseStackPoseMixin {
    //? if >=1.21.5 {
    @Shadow
    @Final
    private Matrix4f pose;

    @Shadow
    @Final
    private Matrix3f normal;

    @Shadow
    private boolean trustedNormals;

    // Author: literal.uu
    // Reason: Reuse a temporary normal matrix when applying an orthonormal pose matrix.
    @Overwrite
    public void mulPose(Matrix4fc matrix) {
        this.pose.mul(matrix);
        if (!MatrixUtil.isPureTranslation(matrix)) {
            //? if >=26 {
            boolean orthonormal = MatrixUtil.checkPropertyRaw(matrix, Matrix4fc.PROPERTY_ORTHONORMAL);
            //?} else {
            /*boolean orthonormal = MatrixUtil.isOrthonormal(matrix);
            *///?}
            if (orthonormal) {
                this.normal.mul(ModelRenderScratch.get().normalMatrix(matrix));
            } else {
                this.normal.set(this.pose).invert().transpose();
                this.trustedNormals = false;
            }
        }
    }
    //?}
}
