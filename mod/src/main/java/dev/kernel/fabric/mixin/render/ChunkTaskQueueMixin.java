package dev.kernel.fabric.mixin.render;

import dev.kernel.fabric.render.IndexedChunkTaskQueue;
import dev.kernel.fabric.render.KernelChunkTask;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
//? if >=26.2 {
import net.minecraft.client.renderer.chunk.SectionTaskDynamicQueue;
//? } else {
/*import net.minecraft.client.renderer.chunk.CompileTaskDynamicQueue;
*///? }
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

//? if >=26.2 {
@Mixin(SectionTaskDynamicQueue.class)
//? } else {
/*@Mixin(CompileTaskDynamicQueue.class)
*///? }
public abstract class ChunkTaskQueueMixin {
    @Unique private final IndexedChunkTaskQueue kernel$tasks = new IndexedChunkTaskQueue();

    // Author: literal.uu
    // Reason: Index pending tasks for nearest-first selection and prompt cancellation.
    @Overwrite
    //? if >=26.2 {
    public synchronized void add(SectionRenderDispatcher.RenderSection.SectionTask task) {
    //? } else {
    /*public synchronized void add(SectionRenderDispatcher.RenderSection.CompileTask task) {
    *///? }
        kernel$tasks.add((KernelChunkTask) task);
    }

    // Author: literal.uu
    // Reason: Avoid rescanning every native task while preserving distance priorities and recompile fairness.
    @Overwrite
    //? if >=26.2 {
    public synchronized SectionRenderDispatcher.RenderSection.SectionTask poll(Vec3 camera) {
        return (SectionRenderDispatcher.RenderSection.SectionTask) kernel$tasks.poll(camera.x, camera.y, camera.z);
    }
    //? } else {
    /*public synchronized SectionRenderDispatcher.RenderSection.CompileTask poll(Vec3 camera) {
        return (SectionRenderDispatcher.RenderSection.CompileTask) kernel$tasks.poll(camera.x, camera.y, camera.z);
    }
    *///? }

    // Author: literal.uu
    // Reason: Report the synchronized count of pending indexed tasks.
    @Overwrite
    public int size() { return kernel$tasks.size(); }

    // Author: literal.uu
    // Reason: Cancel all queued tasks and detach queue ownership during renderer reset/shutdown.
    @Overwrite
    public synchronized void clear() { kernel$tasks.clear(); }
}
