package dev.kernel.fabric.mixin.startup;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import dev.kernel.fabric.bootstrap.StartupWindowBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=26.1 {
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.GpuBackend;
//? }

@Mixin(Window.class)
public abstract class WindowMixin {
    //? if >=26.1 {
    @WrapOperation(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private static long kernel$adopt(int width, int height, CharSequence title, long monitor, long share, Operation<Long> original, @Local(argsOnly = true) GpuBackend backend) {
        long handle = StartupWindowBridge.adopt(width, height, title.toString(), monitor, share, "OpenGL".equals(backend.getName()));
    //? } else {
    /*@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private static long kernel$adopt(int width, int height, CharSequence title, long monitor, long share, Operation<Long> original) {
        long handle = StartupWindowBridge.adopt(width, height, title.toString(), monitor, share, true);
    *///? }
        return handle != 0 ? handle : original.call(width, height, title, monitor, share);
    }
}
