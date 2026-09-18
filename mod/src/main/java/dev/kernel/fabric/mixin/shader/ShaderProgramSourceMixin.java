package dev.kernel.fabric.mixin.shader;

import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.5 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.shaders.ShaderType;
import dev.kernel.fabric.shader.KernelWorldShaders;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//? } else {
/*import net.minecraft.resources.ResourceLocation;
*///? }
//? }

/**
 * Returns a shader pack's world program where Minecraft would have compiled its own core shader.
 *
 * <p>This is the whole world-stage substitution: Minecraft resolves a core shader to source before
 * compiling the pipeline that uses it, so replacing the source replaces the draw while the vertex format,
 * colour targets and uniform environment stay Minecraft's.
 *
 * <p>The target is the compilation cache rather than {@code ShaderManager.getShader}. Pipeline
 * compilation is handed a source supplier bound to {@code CompilationCache::getShaderSource}, and
 * {@code getShader} is only a public convenience that calls the same cache; hooking it changes what
 * callers read but not what the graphics device compiles.
 *
 * <p>Deciding a program needs Minecraft's source for both of its stages, and asking for the other one
 * re-enters this method. The guard makes that nested call return the original, so a translation is never
 * built from an already translated program.
 *
 * <p>1.21.4 resolves core shaders through a different program system and has no {@code ShaderType}, so
 * this mixin carries no injector there and Minecraft keeps drawing the world itself.
 */
@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$CompilationCache")
public abstract class ShaderProgramSourceMixin {
    //? if >=1.21.5 {
    //? if >=1.21.11 {
    @Shadow public abstract String getShaderSource(Identifier identifier, ShaderType type);
    //? } else {
    /*@Shadow public abstract String getShaderSource(ResourceLocation identifier, ShaderType type);
    *///? }

    @Unique
    private static final ThreadLocal<Boolean> kernel$resolving = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @ModifyReturnValue(method = "getShaderSource", at = @At("RETURN"))
    //? if >=1.21.11 {
    private String kernel$substituteWorldProgram(String original, Identifier identifier, ShaderType type) {
    //? } else {
    /*private String kernel$substituteWorldProgram(String original, ResourceLocation identifier, ShaderType type) {
    *///? }
        if (kernel$resolving.get() || !KernelWorldShaders.active()) return original;
        if (!identifier.getNamespace().equals("minecraft")) return original;
        kernel$resolving.set(Boolean.TRUE);
        try {
            return KernelWorldShaders.substitute(identifier.getPath(), type == ShaderType.VERTEX, original,
                stage -> getShaderSource(identifier, stage ? ShaderType.VERTEX : ShaderType.FRAGMENT));
        } catch (RuntimeException failure) {
            return original;
        } finally {
            kernel$resolving.set(Boolean.FALSE);
        }
    }
    //? }
}
