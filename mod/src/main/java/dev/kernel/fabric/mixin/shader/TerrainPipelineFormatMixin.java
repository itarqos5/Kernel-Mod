package dev.kernel.fabric.mixin.shader;

import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.5 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.kernel.fabric.shader.KernelWorldShaders;
import org.spongepowered.asm.mixin.injection.At;
//? }

/**
 * Gives the terrain pipelines the extended vertex format when a shader pack's programs need one.
 *
 * <p>This one method is the whole hook. A render type's format is its pipeline's format, the buffers a
 * section is built into come from the render type, the GLSL attribute names are bound from the format
 * when the program is linked, and the GL attribute pointers are set up from it too. Changing it here
 * therefore changes what is written, what is declared and what is read, together and consistently,
 * rather than in three places that could disagree.
 *
 * <p>Only a pipeline already drawing with Minecraft's block format is affected, which is the terrain
 * pipelines and nothing else, and only while a pack declares an attribute Kernel writes. Otherwise the
 * pipeline's own format is returned unchanged — the same object, not a copy, so Minecraft's own
 * comparisons against it still hold.
 *
 * <p>1.21.4 has no render pipelines at all and resolves its core shaders another way, so there is
 * nothing to hook and terrain is drawn as it always was. A mixin must still name a class that exists to
 * be loaded, so there it is attached to the vertex format and carries no injector.
 */
//? if >=1.21.5 {
@Mixin(RenderPipeline.class)
//? } else {
/*@Mixin(com.mojang.blaze3d.vertex.VertexFormat.class)
*///? }
public abstract class TerrainPipelineFormatMixin {
    //? if >=1.21.5 {
    @ModifyReturnValue(method = "getVertexFormat", at = @At("RETURN"))
    private VertexFormat kernel$extendTerrainFormat(VertexFormat original) {
        return KernelWorldShaders.vertexFormat(original);
    }
    //? }
}
