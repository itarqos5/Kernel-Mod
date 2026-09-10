package dev.kernel.fabric.mixin.render;

import dev.kernel.fabric.render.KernelChunkTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$ResortTransparencyTask")
public abstract class ChunkResortTaskMixin implements KernelChunkTask {
    @Unique
    @Override
    public boolean kernel$isNativeTask() {
        return getClass() == ChunkResortTaskMixin.class;
    }

    @Inject(method = "cancel", at = @At("TAIL"))
    private void kernel$removeCancelledTask(CallbackInfo callback) {
        kernel$notifyCancelled();
    }
}
