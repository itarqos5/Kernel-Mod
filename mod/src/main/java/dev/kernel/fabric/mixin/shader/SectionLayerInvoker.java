package dev.kernel.fabric.mixin.shader;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.5 && <1.21.6 {
/*import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.gen.Invoker;
*///? }

/**
 * Reaches Minecraft's own terrain draw so the shadow pass can reuse it.
 *
 * <p>The draw is private, but it is exactly the work the shadow pass needs: it walks the frame's
 * visible sections, builds one indexed draw per section from meshes that are already on the GPU, and
 * submits them in a single pass. Reimplementing it would mean maintaining a second copy of section
 * iteration for no benefit, so the shadow pass borrows it and swaps the pipeline and the target
 * underneath instead.
 *
 * <p>Only 1.21.5 has this method. From 1.21.6 the terrain is submitted through
 * {@code ChunkSectionsToRender} and needs a different adapter, so the invoker is absent there and the
 * interface is empty rather than referring to a method that does not exist.
 */
@Mixin(LevelRenderer.class)
public interface SectionLayerInvoker {
    //? if >=1.21.5 && <1.21.6 {
    /*@Invoker("renderSectionLayer")
    void kernel$renderSectionLayer(RenderType type, double cameraX, double cameraY, double cameraZ,
                                   Matrix4f frustumMatrix, Matrix4f projectionMatrix);
    *///? }
}
