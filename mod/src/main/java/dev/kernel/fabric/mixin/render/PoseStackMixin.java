package dev.kernel.fabric.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.MatrixUtil;
import dev.kernel.fabric.render.ModelRenderScratch;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Removes legacy pose-stack entry churn and temporary normal-matrix allocation.
 */
@Mixin(PoseStack.class)
public abstract class PoseStackMixin {
    //? if <=1.21.4 {
    /*@Shadow
    @Final
    private Deque<PoseStack.Pose> poseStack;

    @Unique
    private ArrayDeque<PoseStack.Pose> kernel$freePoses;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void kernel$createFreePosePool(CallbackInfo callbackInfo) {
        this.kernel$freePoses = new ArrayDeque<>();
    }

    // Author: literal.uu
    // Reason: Retain popped entries so only a pose stack's maximum depth allocates matrices.
    @Overwrite
    public void pushPose() {
        PoseStack.Pose source = this.poseStack.getLast();
        PoseStack.Pose target = this.kernel$freePoses.pollLast();

        if (target == null) {
            target = PoseStackPoseAccessor.kernel$copy(source);
        } else {
            target.pose().set(source.pose());
            target.normal().set(source.normal());
            ((PoseStackPoseAccessor)(Object)target).kernel$setTrustedNormals(
                ((PoseStackPoseAccessor)(Object)source).kernel$trustedNormals()
            );
        }

        this.poseStack.addLast(target);
    }

    // Author: literal.uu
    // Reason: Return removed pose entries to Kernel's per-stack reuse pool.
    @Overwrite
    public void popPose() {
        this.kernel$freePoses.addLast(this.poseStack.removeLast());
    }

    // Author: literal.uu
    // Reason: Reuse a temporary normal matrix when applying an orthonormal pose matrix.
    @Overwrite
    public void mulPose(Matrix4f matrix) {
        PoseStack.Pose pose = this.poseStack.getLast();
        pose.pose().mul(matrix);
        if (!MatrixUtil.isPureTranslation(matrix)) {
            if (MatrixUtil.isOrthonormal(matrix)) {
                pose.normal().mul(ModelRenderScratch.get().normalMatrix(matrix));
            } else {
                pose.normal().set(pose.pose()).invert().transpose();
                ((PoseStackPoseAccessor)(Object)pose).kernel$setTrustedNormals(false);
            }
        }
    }
    *///?}
}
