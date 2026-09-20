package dev.kernel.fabric.mixin.shader;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
//? if >1.21.4 && <=1.21.10 {
/*import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.kernel.fabric.render.KernelTerrainAttributes;
import dev.kernel.fabric.render.KernelVertexFormats;
import dev.kernel.fabric.shader.pack.ShaderWorldAttributes;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///? }

/**
 * Fills the vertex attributes a shader pack asked for, as each terrain vertex is written.
 *
 * <p>Minecraft's buffer writes exactly the elements its format declares and refuses to finish a vertex
 * that left one unwritten, so an extended format has to be filled here or the section build fails. That
 * refusal is why this is safe to add: a mistake shows up as a loud failure rather than as uninitialised
 * memory reaching the GPU.
 *
 * <p>Whether there is anything to fill is decided once, when the buffer is built, by asking its format
 * for Kernel's elements. A buffer whose format has none keeps a null record and every vertex costs one
 * null check, which is what every vertex in the game costs when no pack is loaded. It also means the
 * decision is per buffer rather than per vertex, so a pack being adopted midway through cannot leave one
 * section half written in one format and half in another.
 *
 * <p>The thread's current block is held directly rather than looked up per vertex. A section's buffers
 * are built and filled by the one worker thread that owns them, so the reference stays that thread's.
 *
 * <p>Carried only on the targets Kernel extends the vertex format for; elsewhere no element is ever
 * registered and there would be nothing to fill.
 */
@Mixin(BufferBuilder.class)
public abstract class TerrainVertexMixin {
    //? if >1.21.4 && <=1.21.10 {
    /*@Shadow private long vertexPointer;
    @Shadow private int elementsToFill;
    @Shadow @Final private int[] offsetsByElement;

    @Unique private KernelTerrainAttributes.Current kernel$block;
    @Unique private int kernel$entityOffset = -1, kernel$entityMask;
    @Unique private int kernel$midBlockOffset = -1, kernel$midBlockMask;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void kernel$findExtendedElements(ByteBufferBuilder buffer, VertexFormat.Mode mode, VertexFormat format, CallbackInfo callback) {
        VertexFormatElement entity = KernelVertexFormats.element(ShaderWorldAttributes.MC_ENTITY);
        if (entity != null && format.contains(entity)) {
            kernel$entityOffset = this.offsetsByElement[entity.id()];
            kernel$entityMask = entity.mask();
        }
        VertexFormatElement midBlock = KernelVertexFormats.element(ShaderWorldAttributes.AT_MID_BLOCK);
        if (midBlock != null && format.contains(midBlock)) {
            kernel$midBlockOffset = this.offsetsByElement[midBlock.id()];
            kernel$midBlockMask = midBlock.mask();
        }
        if (kernel$entityOffset >= 0 || kernel$midBlockOffset >= 0) kernel$block = KernelTerrainAttributes.current();
    }

    @Inject(method = "addVertex(FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("RETURN"))
    private void kernel$fillExtendedElements(float x, float y, float z, CallbackInfoReturnable<VertexConsumer> callback) {
        KernelTerrainAttributes.Current block = this.kernel$block;
        if (block == null) return;
        long vertex = this.vertexPointer;
        if (kernel$entityOffset >= 0) {
            // The identity in x; y is declared so a pack's vec2 reads naturally, and stays zero until
            // Kernel has something true to put there.
            MemoryUtil.memPutShort(vertex + kernel$entityOffset, (short) block.identity);
            MemoryUtil.memPutShort(vertex + kernel$entityOffset + 2L, (short) 0);
            this.elementsToFill &= ~kernel$entityMask;
        }
        if (kernel$midBlockOffset >= 0) {
            MemoryUtil.memPutByte(vertex + kernel$midBlockOffset, KernelTerrainAttributes.offset(block.centreX - x));
            MemoryUtil.memPutByte(vertex + kernel$midBlockOffset + 1L, KernelTerrainAttributes.offset(block.centreY - y));
            MemoryUtil.memPutByte(vertex + kernel$midBlockOffset + 2L, KernelTerrainAttributes.offset(block.centreZ - z));
            this.elementsToFill &= ~kernel$midBlockMask;
        }
    }
    *///? }
}
