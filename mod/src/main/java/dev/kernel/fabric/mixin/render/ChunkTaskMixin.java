package dev.kernel.fabric.mixin.render;

import dev.kernel.fabric.render.IndexedChunkTaskQueue;
import dev.kernel.fabric.render.KernelChunkTask;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.atomic.AtomicBoolean;

//? if >=26.2 {
@Mixin(SectionRenderDispatcher.RenderSection.SectionTask.class)
//? } else {
/*@Mixin(SectionRenderDispatcher.RenderSection.CompileTask.class)
*///? }
public abstract class ChunkTaskMixin implements KernelChunkTask {
    @Shadow @Final protected AtomicBoolean isCancelled;
    @Shadow public abstract boolean isRecompile();
    @Shadow public abstract void cancel();
    //? if <=1.21.4 {
    /*@Shadow public abstract BlockPos getOrigin();
    *///? } else {
    @Shadow public abstract BlockPos getRenderOrigin();
    //? }
    @Unique private volatile IndexedChunkTaskQueue.Removal kernel$queueRemoval;

    @Override public boolean kernel$isCancelled() { return isCancelled.get(); }
    @Override public boolean kernel$isRecompile() { return isRecompile(); }
    @Override public void kernel$cancel() { cancel(); }

    @Override
    public double kernel$distanceTo(double x, double y, double z) {
        return kernel$origin().distToCenterSqr(x, y, z);
    }

    @Override public int kernel$originX() { return kernel$origin().getX(); }
    @Override public int kernel$originY() { return kernel$origin().getY(); }
    @Override public int kernel$originZ() { return kernel$origin().getZ(); }

    @Unique
    private BlockPos kernel$origin() {
        //? if <=1.21.4 {
        /*return getOrigin();
        *///? } else {
        return getRenderOrigin();
        //? }
    }

    @Override
    public void kernel$setQueueRemoval(IndexedChunkTaskQueue.Removal removal) {
        kernel$queueRemoval = removal;
    }

    @Override
    public void kernel$clearQueueRemoval(IndexedChunkTaskQueue.Removal removal) {
        if (kernel$queueRemoval == removal) kernel$queueRemoval = null;
    }

    @Override
    public void kernel$notifyCancelled() {
        IndexedChunkTaskQueue.Removal removal = kernel$queueRemoval;
        if (removal != null) removal.remove();
    }
}
