package dev.kernel.fabric.mixin.render;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;

//? if <26 {
/*import dev.kernel.fabric.render.ChunkGpuUploadScheduler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Queue;
*///?}

//? if >1.21.5 && <26 {
/*import net.minecraft.client.renderer.chunk.SectionMesh;
*///?}

/**
 * Time-slices legacy chunk uploads without replacing Minecraft's driver-facing buffer implementation.
 */
@Mixin(SectionRenderDispatcher.class)
public abstract class SectionRenderDispatcherMixin {
    //? if <26 {
    /*@Shadow
    @Final
    private Queue<Runnable> toUpload;

    @Shadow
    private volatile boolean closed;
    *///?}

    //? if >1.21.5 && <26 {
    /*@Shadow
    @Final
    private Queue<SectionMesh> toClose;
    *///?}

    //? if <=1.21.5 {
    /*// Author: literal.uu
    // Reason: Bound render-thread chunk upload work per pass while preserving a full shutdown drain.
    @Overwrite
    public void uploadAllPendingUploads() {
        ChunkGpuUploadScheduler.drain(this.toUpload, this.closed);
    }
    *///?}

    //? if >1.21.5 && <26 {
    /*// Author: literal.uu
    // Reason: Bound render-thread chunk upload work per pass while retaining vanilla's deferred mesh cleanup.
    @Overwrite
    public void uploadAllPendingUploads() {
        ChunkGpuUploadScheduler.drain(this.toUpload, this.closed);

        SectionMesh sectionMesh;
        while ((sectionMesh = this.toClose.poll()) != null) {
            sectionMesh.close();
        }
    }
    *///?}
}
