package dev.kernel.fabric.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Provides the state copy operations needed by the legacy pose-entry pool.
 */
@Mixin(PoseStack.Pose.class)
public interface PoseStackPoseAccessor {
    //? if <=1.21.4 {
    /*@Accessor("trustedNormals")
    boolean kernel$trustedNormals();

    @Accessor("trustedNormals")
    void kernel$setTrustedNormals(boolean trustedNormals);

    @Invoker("<init>")
    static PoseStack.Pose kernel$copy(PoseStack.Pose source) {
        throw new AssertionError();
    }
    *///?}
}
