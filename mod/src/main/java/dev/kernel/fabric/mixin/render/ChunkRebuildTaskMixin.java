package dev.kernel.fabric.mixin.render;

import dev.kernel.fabric.render.KernelChunkTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.2 {
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$CompileTask")
//? } else {
/*@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$RebuildTask")
*///? }
public abstract class ChunkRebuildTaskMixin implements KernelChunkTask {
    @Unique
    @Override
    public boolean kernel$isNativeTask() {
        // Mixin remaps this self type to the concrete target; subclasses remain on the live-priority path.
        return getClass() == ChunkRebuildTaskMixin.class;
    }

    @Inject(method = "cancel", at = @At("TAIL"))
    private void kernel$removeCancelledTask(CallbackInfo callback) {
        kernel$notifyCancelled();
    }
}
