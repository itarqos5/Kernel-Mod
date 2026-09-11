package dev.kernel.fabric.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
//? if <=1.21.4 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.kernel.fabric.render.DeferredSectionVertexBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
*///? } else {
@Mixin(RenderSystem.class)
//? }
public abstract class DeferredVertexBufferMixin {
    //? if <=1.21.4 {
    /*@Shadow @Final private BufferUsage usage;
    @Shadow @Final @Mutable private GpuBuffer vertexBuffer;
    @Shadow private int arrayObjectId;

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Lcom/mojang/blaze3d/buffers/BufferType;Lcom/mojang/blaze3d/buffers/BufferUsage;I)Lcom/mojang/blaze3d/buffers/GpuBuffer;"))
    private GpuBuffer kernel$deferStorage(BufferType type, BufferUsage usage, int size, Operation<GpuBuffer> original) {
        return (Object) this instanceof DeferredSectionVertexBuffer ? null : original.call(type, usage, size);
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenVertexArrays()I"))
    private int kernel$deferVertexArray() {
        return (Object) this instanceof DeferredSectionVertexBuffer ? 0 : GlStateManager._glGenVertexArrays();
    }

    @Inject(method = "bind", at = @At("HEAD"))
    private void kernel$prepareBinding(CallbackInfo callback) { kernel$materializeArray(); }

    // Stay inside native upload's try-with-resources and after its closed/thread checks.
    @Inject(method = "upload", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER))
    private void kernel$prepareUpload(CallbackInfo callback) {
        if (vertexBuffer == null && (Object) this instanceof DeferredSectionVertexBuffer) {
            kernel$materializeArray();
            vertexBuffer = new GpuBuffer(BufferType.VERTICES, usage, 0);
        }
    }

    @Unique
    private void kernel$materializeArray() {
        if (arrayObjectId != 0 || !((Object) this instanceof DeferredSectionVertexBuffer)) return;
        RenderSystem.assertOnRenderThread();
        arrayObjectId = GlStateManager._glGenVertexArrays();
    }

    @Redirect(method = "close", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/GpuBuffer;close()V", ordinal = 0))
    private void kernel$closeAllocatedStorage(GpuBuffer buffer) { if (buffer != null) buffer.close(); }

    @Redirect(method = "close", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;glDeleteVertexArrays(I)V"))
    private void kernel$closeAllocatedArray(int array) { if (array != 0) RenderSystem.glDeleteVertexArrays(array); }
    *///? }
}
