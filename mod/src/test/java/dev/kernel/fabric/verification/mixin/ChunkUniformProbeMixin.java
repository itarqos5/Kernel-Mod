package dev.kernel.fabric.verification.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.11 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.kernel.fabric.verification.ChunkUniformProbe;
import net.minecraft.client.renderer.DynamicUniforms;
import org.spongepowered.asm.mixin.injection.At;
//? }

@Mixin(LevelRenderer.class)
public abstract class ChunkUniformProbeMixin {
    //? if >=1.21.11 {
    @WrapOperation(method = "prepareChunkRenders", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeChunkSections([Lnet/minecraft/client/renderer/DynamicUniforms$ChunkSectionInfo;)[Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice[] kernelProbe$uniformSnapshots(DynamicUniforms uniforms, DynamicUniforms.ChunkSectionInfo[] values,
                                                        Operation<GpuBufferSlice[]> original) {
        ChunkUniformProbe.inspect(values);
        return original.call(uniforms, values);
    }
    //? }
}
