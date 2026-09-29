package dev.kernel.fabric.shader;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
//? if >=1.21.5 && <1.21.6 {
/*import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
*///? }

/**
 * Draws the world a second time from the shadow light, into a depth map a pack can sample.
 *
 * <p>The terrain is already meshed, culled and listed for this frame, so the shadow pass reuses all of
 * it rather than building a second renderer: it calls Minecraft's own section-layer draw with the light
 * as the camera. Two things are swapped underneath that call while it runs, and nothing else changes —
 * the pipeline, so the pack's {@code shadow} program is what compiles, and the render target, so the
 * depth lands in Kernel's map instead of the window.
 *
 * <p>The pack's shadow program reaches the GPU through the same substitution hook as its camera
 * programs. One core shader resolves to one source, but the compiled module is cached per
 * {@code ShaderDefines}, and the defines are injected into that source after the provider returns it.
 * So the source carries both programs behind {@code #ifdef} and the pipeline here adds the flag that
 * selects the shadow one. See {@code docs/SHADER_WORLD_STAGE.md}.
 *
 * <p>Limited to 1.21.5 for now, which is the target the world stage is verified on. The draw call this
 * reuses is private there and absent from 1.21.6, where the terrain is submitted through
 * {@code ChunkSectionsToRender} instead, so the later targets need their own adapter.
 */
public final class KernelShadowPass {
    /** The preprocessor flag that selects a pack's shadow program out of the combined source. */
    public static final String DEFINE = "KERNEL_SHADOW";

    private KernelShadowPass() {}

    //? if >=1.21.5 && <1.21.6 {
    /*/^* True while the shadow pass owns the pipeline and the render target. ^/
    private static boolean active;
    private static TextureTarget target;
    private static int resolution;
    private static float distance = ShaderShadowMatrices.DEFAULT_DISTANCE;
    private static boolean enabled;
    /^* Keyed by the vanilla pipeline itself, because that is what the draw hands over to be replaced. ^/
    private static final Map<RenderPipeline, RenderPipeline> PIPELINES = new IdentityHashMap<>();
    private static Matrix4f view = new Matrix4f(), projection = new Matrix4f();

    /^* Adopts a newly compiled pack, or drops the pass when shaders are switched off. ^/
    public static void adopt(int shadowResolution, float shadowDistance, boolean packCasts) {
        enabled = packCasts;
        distance = shadowDistance;
        PIPELINES.clear();
        if (resolution != shadowResolution) { resolution = shadowResolution; release(); }
        if (!packCasts) release();
    }

    /^* True when this frame should render a shadow map before the terrain is drawn. ^/
    public static boolean casting() { return enabled && resolution > 0; }

    /^* The depth texture a pack samples as {@code shadowtex0}, or 0 when nothing has been rendered. ^/
    public static int depthTexture() {
        //? if >=1.21.5 {
        return target != null && target.getDepthTexture() instanceof com.mojang.blaze3d.opengl.GlTexture texture ? texture.glId() : 0;
        //? } else {
        /^return 0;
        ^///? }
    }

    public static Matrix4f modelView() { return new Matrix4f(view); }
    public static Matrix4f projectionMatrix() { return new Matrix4f(projection); }

    /^*
     * Renders the shadow map, immediately before Minecraft draws the solid terrain.
     *
     * <p>Runs at that point because the visible-section list is complete by then and is the very list
     * the shadow pass wants; rendering earlier would shadow from a stale set.
     ^/
    public static void render(Minecraft minecraft, float celestialAngle, double cameraX, double cameraY, double cameraZ) {
        if (active || !casting() || minecraft.levelRenderer == null) return;
        active = true;
        try {
            if (target == null) target = new TextureTarget("Kernel shadow map", resolution, resolution, true);
            view = ShaderShadowMatrices.modelView(celestialAngle);
            projection = ShaderShadowMatrices.projection(distance);
            RenderSystem.getDevice().createCommandEncoder()
                .clearColorAndDepthTextures(target.getColorTexture(), 0xFFFFFFFF, target.getDepthTexture(), 1.0);
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(projection, ProjectionType.ORTHOGRAPHIC);
            var stack = RenderSystem.getModelViewStack();
            stack.pushMatrix();
            try {
                stack.set(view);
                var renderer = (dev.kernel.fabric.mixin.shader.SectionLayerInvoker) minecraft.levelRenderer;
                // Solid and both cutout layers cast; translucent geometry does not, which is also why
                // the pass does not need the back-to-front ordering the translucent layer relies on.
                for (RenderType layer : List.of(RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout()))
                    renderer.kernel$renderSectionLayer(layer, cameraX, cameraY, cameraZ, view, projection);
            } finally {
                stack.popMatrix();
                RenderSystem.restoreProjectionMatrix();
            }
        } finally {
            active = false;
        }
    }

    /^* The pipeline a terrain draw should use, which is the shadow variant while the pass is running. ^/
    public static RenderPipeline pipeline(RenderPipeline original) {
        if (!active || original == null) return original;
        return PIPELINES.computeIfAbsent(original, KernelShadowPass::shadowVariant);
    }

    /^* The target a terrain draw should write to, which is the shadow map while the pass is running. ^/
    public static RenderTarget target(RenderTarget original) { return active && target != null ? target : original; }

    /^*
     * The same pipeline with the shadow flag added and colour writes off.
     *
     * <p>Everything else is copied rather than chosen, because the shadow pass draws the same meshes
     * with the same vertex format and the same samplers; only where it writes and which branch of the
     * program compiles are different.
     ^/
    private static RenderPipeline shadowVariant(RenderPipeline original) {
        var builder = RenderPipeline.builder()
            .withLocation(ResourceLocation.fromNamespaceAndPath("kernel", "shadow/" + original.getLocation().getPath()))
            .withVertexShader(original.getVertexShader())
            .withFragmentShader(original.getFragmentShader())
            .withVertexFormat(original.getVertexFormat(), original.getVertexFormatMode())
            .withDepthTestFunction(original.getDepthTestFunction())
            .withCull(original.isCull())
            .withColorWrite(false)
            .withDepthWrite(true)
            .withShaderDefine(DEFINE);
        var defines = original.getShaderDefines();
        // The builder takes a define's value as an int or a float, never as text, so a value that is
        // neither is one this pass cannot reproduce. Carrying the wrong value would compile a program
        // that differs from the one the camera draws with, so the whole variant is abandoned instead.
        for (var define : defines.values().entrySet()) {
            String value = define.getValue();
            try { builder.withShaderDefine(define.getKey(), Integer.parseInt(value)); }
            catch (NumberFormatException notInteger) {
                try { builder.withShaderDefine(define.getKey(), Float.parseFloat(value)); }
                catch (NumberFormatException notNumeric) {
                    throw new IllegalStateException("Cannot reproduce shader define " + define.getKey() + "=" + value);
                }
            }
        }
        for (String flag : defines.flags()) builder.withShaderDefine(flag);
        for (String sampler : original.getSamplers()) builder.withSampler(sampler);
        for (var uniform : original.getUniforms()) builder.withUniform(uniform.name(), uniform.type());
        original.getBlendFunction().ifPresentOrElse(builder::withBlend, builder::withoutBlend);
        return builder.build();
    }

    /^* Releases the shadow map. Only the render thread may call this. ^/
    public static void release() {
        if (target != null) { target.destroyBuffers(); target = null; }
    }
    *///? } else {
    public static void adopt(int shadowResolution, float shadowDistance, boolean packCasts) { }
    public static boolean casting() { return false; }
    public static int depthTexture() { return 0; }
    public static Matrix4f modelView() { return new Matrix4f(); }
    public static Matrix4f projectionMatrix() { return new Matrix4f(); }
    public static void render(Minecraft minecraft, float celestialAngle, double cameraX, double cameraY, double cameraZ) { }
    public static void release() { }
    //? }
}
