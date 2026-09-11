package dev.kernel.fabric.shader;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.LevelTargetBundle;

/** Keeps native resources alive until capture, and sequences later main-target clears after it. */
final class ShaderDepthPass {
    private ShaderDepthPass() {}
    static void add(FrameGraphBuilder graph, LevelTargetBundle targets, boolean clouds, Consumer<List<RenderTarget>> capture) {
        var handles = new ArrayList<ResourceHandle<RenderTarget>>(5);
        for (var handle : java.util.Arrays.asList(targets.translucent, targets.itemEntity, targets.particles,
            targets.weather, clouds ? targets.clouds : null)) if (handle != null) handles.add(handle);
        targets.main = add(graph, targets.main, handles, capture);
    }
    static <T> ResourceHandle<T> add(FrameGraphBuilder graph, ResourceHandle<T> main,
        List<ResourceHandle<T>> others, Consumer<List<T>> capture) {
        var pass = graph.addPass("kernel_depth");
        pass.disableCulling();
        var output = pass.readsAndWrites(main);
        var handles = new ArrayList<ResourceHandle<T>>(others.size() + 1);
        handles.add(output);
        for (var handle : others) { pass.reads(handle); handles.add(handle); }
        // Handle snapshots are used only inside this frame's execution; no target escapes to the next frame.
        pass.executes(() -> capture.accept(handles.stream().map(ResourceHandle::get).toList()));
        return output;
    }
}
