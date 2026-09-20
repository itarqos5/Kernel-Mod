package dev.kernel.fabric.mixin.shader;

import org.spongepowered.asm.mixin.Mixin;
//? if >1.21.4 && <=1.21.10 {
/*import com.mojang.blaze3d.vertex.VertexFormat;
import dev.kernel.fabric.shader.KernelWorldShaders;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
*///? }

/**
 * Builds chunk sections in the vertex format a shader pack's programs need.
 *
 * <p>The chunk builder names Minecraft's block format outright when it opens a layer's buffer, rather
 * than asking the render type what format it draws with. A section is then drawn with the format it was
 * built in, because the mesh carries its own layout and the attribute pointers are set up from that. So
 * extending what the pipeline declares is not enough on its own: without this, a program is given
 * attribute locations that no buffer supplies and reads the zeroes GL substitutes for them, which is
 * exactly what the GPU probe measured.
 *
 * <p>Only a buffer that was going to use the block format is changed, and only while a pack declares an
 * attribute Kernel writes. Otherwise the same format object is handed straight back.
 *
 * <p>Carried only on the targets Kernel extends the vertex format for. The later ones reorganised chunk
 * compilation and refuse world programs anyway; a mixin must still name a class that exists to be
 * loaded, so there it is attached to the section render dispatcher and carries no injector.
 */
//? if >=1.21.11 {
@Mixin(net.minecraft.client.renderer.chunk.SectionRenderDispatcher.class)
//? } else {
/*@Mixin(net.minecraft.client.renderer.chunk.SectionCompiler.class)
*///? }
public abstract class SectionVertexFormatMixin {
    //? if >1.21.4 && <=1.21.10 {
    /*@ModifyArg(
        method = "getOrBeginLayer",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;<init>"
            + "(Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;"
            + "Lcom/mojang/blaze3d/vertex/VertexFormat;)V"),
        index = 2)
    private VertexFormat kernel$extendSectionFormat(VertexFormat original) {
        return KernelWorldShaders.vertexFormat(original);
    }
    *///? }
}
