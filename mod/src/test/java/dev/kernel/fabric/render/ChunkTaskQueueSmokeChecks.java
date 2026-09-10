package dev.kernel.fabric.render;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
//? if >=26.2 {
import net.minecraft.client.renderer.chunk.SectionTaskDynamicQueue;
//? } else {
/*import net.minecraft.client.renderer.chunk.CompileTaskDynamicQueue;
*///? }
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import sun.misc.Unsafe;

/** Native task constructors are real; only the GPU-owning section is allocated without its constructor. */
public final class ChunkTaskQueueSmokeChecks {
    public static void run() throws Exception {
        //? if >=26.2 {
        SectionTaskDynamicQueue queue = new SectionTaskDynamicQueue();
        //? } else {
        /*CompileTaskDynamicQueue queue = new CompileTaskDynamicQueue();
        *///? }
        var fresh = createTask(100, false, false);
        var nearby = createTask(0, true, false);
        var resort = createTask(20, true, true);
        check(((KernelChunkTask) nearby).kernel$isNativeTask(), "Native rebuild task was classified as custom");
        check(((KernelChunkTask) resort).kernel$isNativeTask(), "Native resort task was classified as custom");
        queue.add(fresh); queue.add(nearby); queue.add(resort);
        check(queue.poll(Vec3.ZERO) == nearby, "Nearest recompile task did not win");
        resort.cancel();
        check(queue.size() == 1, "Cancellation did not immediately remove the queued task");
        check(queue.poll(Vec3.ZERO) == fresh, "Initial compile disappeared");
        queue.add(fresh);
        queue.clear();
        check(((KernelChunkTask) fresh).kernel$isCancelled() && queue.size() == 0, "Clear did not cancel and release tasks");
        System.out.println("Kernel chunk task queue: real Fabric/Mixin lifecycle smoke test passed.");
    }

    //? if >=26.2 {
    private static SectionRenderDispatcher.RenderSection.SectionTask createTask(int x, boolean recompile, boolean resort) throws Exception {
    //? } else {
    /*private static SectionRenderDispatcher.RenderSection.CompileTask createTask(int x, boolean recompile, boolean resort) throws Exception {
    *///? }
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        var section = (SectionRenderDispatcher.RenderSection) unsafe.allocateInstance(SectionRenderDispatcher.RenderSection.class);
        //? if <=1.21.4 {
        /*var origin = SectionRenderDispatcher.RenderSection.class.getDeclaredField("origin");
        *///? } else {
        var origin = SectionRenderDispatcher.RenderSection.class.getDeclaredField("renderOrigin");
        //? }
        origin.setAccessible(true);
        origin.set(section, new BlockPos.MutableBlockPos(x, 0, 0));
        //? if >=26.2 {
        String rebuildName = "CompileTask";
        //? } else {
        /*String rebuildName = "RebuildTask";
        *///? }
        Class<?> type = Class.forName(SectionRenderDispatcher.RenderSection.class.getName() + "$" + (resort ? "ResortTransparencyTask" : rebuildName));
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object task = resort ? constructor.newInstance(section, null) : constructor.newInstance(section, null, recompile);
        //? if >=26.2 {
        return (SectionRenderDispatcher.RenderSection.SectionTask) task;
        //? } else {
        /*return (SectionRenderDispatcher.RenderSection.CompileTask) task;
        *///? }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
