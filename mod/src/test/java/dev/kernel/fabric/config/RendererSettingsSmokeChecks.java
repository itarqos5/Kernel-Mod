package dev.kernel.fabric.config;

import com.mojang.blaze3d.vertex.VertexSorting;
import dev.kernel.fabric.render.KernelChunkTask;
import dev.kernel.fabric.render.KernelVertexSorting;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

/** Checks real Mixin activation and screen linkage, without initializing Minecraft or a graphics device. */
public final class RendererSettingsSmokeChecks {
    public static void run() throws Exception {
        for (RendererFeature feature : RendererFeature.values()) {
            if (KernelRendererSettings.active().enabled(feature)) throw new AssertionError("Setting not loaded: " + feature);
        }
        if (VertexSorting.DISTANCE_TO_ORIGIN instanceof KernelVertexSorting) throw new AssertionError("Disabled quad sorter applied");
        //? if >=26.2 {
        Class<?> taskType = SectionRenderDispatcher.RenderSection.SectionTask.class;
        //? } else {
        /*Class<?> taskType = SectionRenderDispatcher.RenderSection.CompileTask.class;
        *///? }
        if (KernelChunkTask.class.isAssignableFrom(taskType)) throw new AssertionError("Disabled task adapter applied");
        ClassLoader loader = RendererSettingsSmokeChecks.class.getClassLoader();
        Class.forName("net.minecraft.client.gui.screens.options.VideoSettingsScreen", false, loader).getDeclaredMethods();
        Class.forName("dev.kernel.fabric.config.KernelSettingsScreen", false, loader).getDeclaredConstructors();
        if (KernelRendererSettings.restartRequired(KernelRendererSettings.active())) throw new AssertionError("Unchanged settings need restart");
        if (!KernelRendererSettings.restartRequired(RendererConfig.defaults())) throw new AssertionError("Changed settings lost restart warning");
        if (new KernelMixinPlugin().shouldApplyMixin("unused", "dev.kernel.fabric.mixin.render.PoseStackPoseAccessor")) {
            throw new AssertionError("Accessor was not disabled with its owning feature");
        }
        if (!new KernelMixinPlugin().shouldApplyMixin("unused", "dev.kernel.fabric.mixin.startup.MinecraftStartupMixin")) {
            throw new AssertionError("Renderer controls disabled startup cleanup");
        }
        System.out.println("Kernel renderer settings: disabled Mixins and screen linkage smoke test passed.");
    }
}
