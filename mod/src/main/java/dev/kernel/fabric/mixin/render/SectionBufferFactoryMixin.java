package dev.kernel.fabric.mixin.render;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
//? if <=1.21.4 {
/*import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.kernel.fabric.render.DeferredSectionVertexBuffer;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
*///? }

@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class SectionBufferFactoryMixin {
    //? if <=1.21.4 {
    /*// The sole VertexBuffer constructor in this class lives in its layer-factory lambda.
    @Redirect(method = "*", at = @At(value = "NEW", target = "com/mojang/blaze3d/vertex/VertexBuffer"), require = 1, allow = 1)
    private static VertexBuffer kernel$deferLayer(BufferUsage usage) {
        return new DeferredSectionVertexBuffer(usage);
    }
    *///? }
}
