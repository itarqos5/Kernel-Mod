package dev.kernel.fabric.mixin.render;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.11 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.kernel.fabric.render.ChunkMatrixSnapshot;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.injection.At;
//? }

@Mixin(LevelRenderer.class)
public abstract class ChunkUniformMixin {
    //? if >=1.21.11 {
    @WrapOperation(method = "prepareChunkRenders", at = @At(value = "NEW", target = "(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4f;", remap = false))
    private Matrix4f kernel$shareUnchangedView(Matrix4fc source, Operation<Matrix4f> original,
                                             @Share("chunkView") LocalRef<Matrix4f> snapshot) {
        Matrix4f copy = snapshot.get();
        if (ChunkMatrixSnapshot.matches(copy, source)) return copy;
        copy = original.call(source);
        snapshot.set(ChunkMatrixSnapshot.canRetain(source, copy) ? copy : null);
        return copy;
    }
    //? }
}
