package dev.kernel.fabric.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.kernel.fabric.verification.ShaderProbe;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

/** Reads Minecraft's independently uploaded world projection, then checks matrix bits in the shader image. */
public final class ShaderProjectionWorldChecks {
    private static Matrix4f current, previous, last;
    private static int width, height, lastWidth, lastHeight, phase, frames;
    private static boolean changed, distorted, injectedInvalid, forcedSkip;
    private ShaderProjectionWorldChecks() {}
    public static void prepare(Minecraft minecraft) {
        minecraft.options.fov().set(70);
        minecraft.options.bobView().set(false);
        minecraft.options.screenEffectScale().set(1.0);
    }
    public static void observeNative() throws Exception {
        if (!ShaderProbe.observingShaderWorld() || phase == 3) return;
        forcedSkip = false;
        Matrix4f nativeProjection;
        //? if >=1.21.6 {
        var slice = RenderSystem.getProjectionMatrixBuffer();
        var field = com.mojang.blaze3d.opengl.GlBuffer.class.getDeclaredField("handle"); field.setAccessible(true);
        int buffer = field.getInt(slice.buffer()); // Test-only private access; production captures the public upload argument.
        int binding = GL33C.glGetInteger(GL33C.GL_UNIFORM_BUFFER_BINDING);
        try (var stack = MemoryStack.stackPush()) {
            GL33C.glBindBuffer(GL33C.GL_UNIFORM_BUFFER, buffer);
            var values = stack.mallocFloat(16);
            GL33C.glGetBufferSubData(GL33C.GL_UNIFORM_BUFFER, slice.offset(), values);
            nativeProjection = new Matrix4f(values);
        } finally { GL33C.glBindBuffer(GL33C.GL_UNIFORM_BUFFER, binding); }
        //? } else {
        /*nativeProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        *///? }
        //? if >=26.2 {
        boolean reverse = true, zero = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        //? } elif >=26.1 {
        /*boolean reverse = false, zero = RenderSystem.getDevice().isZZeroToOne();
        *///? } else {
        /*boolean reverse = false, zero = false;
        *///? }
        current = new Matrix4f(); ShaderProjectionState.normalize(nativeProjection, reverse, zero, current);
        if (!injectedInvalid && current.isFinite()) {
            // Exercise one invalid Kernel capture without modifying Minecraft's own uploaded matrix.
            injectedInvalid = forcedSkip = true;
            KernelShaders.captureProjection(new Matrix4f().m00(Float.NaN));
        }
        var minecraft = Minecraft.getInstance();
        //? if >=26.2 {
        var target = minecraft.gameRenderer.mainRenderTarget();
        //? } else {
        /*var target = minecraft.getMainRenderTarget();
        *///? }
        width = target.width; height = target.height;
        previous = last != null && lastWidth == width && lastHeight == height ? last : current;
    }
    public static boolean skipInvalidFrame() {
        if (phase == 3 || (!forcedSkip && (current == null || current.isFinite()))) return false;
        if (KernelShaders.active().isEmpty() || KernelShaders.failed())
            throw new AssertionError("A transient invalid projection disabled the shader pack");
        return true;
    }
    /** Main world color target is already bound for reading by the enclosing shader probe. */
    public static void verifyOutput(int w, int h) {
        if (phase == 3) return;
        if (current == null || w != width || h != height) throw new AssertionError("Native world projection was not observed");
        var matrices = new Matrix4f[]{current, current.invert(new Matrix4f()), previous};
        try (var stack = MemoryStack.stackPush()) {
            var pixel = stack.malloc(4);
            for (int row = 0; row < 3; row++) {
                float[] expected = matrices[row].get(new float[16]);
                for (int index = 0; index < 16; index++) {
                    GL33C.glReadPixels(6 + index, row + 3, 1, 1, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixel);
                    int bits = 0;
                    for (int channel = 0; channel < 4; channel++) bits |= (pixel.get(channel) & 255) << (8 * channel);
                    float actual = Float.intBitsToFloat(bits);
                    // Native matrix property flags can select algebraically equivalent float inverse paths.
                    float tolerance = row == 1 ? 1e-6f + Math.abs(expected[index]) * 1e-5f : 1e-6f;
                    if (!Float.isFinite(actual) || Math.abs(actual - expected[index]) > tolerance)
                        throw new AssertionError("Native projection uniform differs in row " + row + ", component " + index + ": " + actual + " != " + expected[index]);
                }
            }
        }
        if (!current.equals(previous)) changed = true;
        if (phase == 2 && Math.abs(current.m01()) + Math.abs(current.m10()) > 1e-5f) distorted = true;
        last = new Matrix4f(current); lastWidth = width; lastHeight = height; frames++;
    }
    public static boolean advance(Minecraft minecraft) {
        if (phase == 3) return true;
        if (phase == 2) distort(minecraft);
        if (frames < 4 || (phase == 1 && !changed) || (phase == 2 && !distorted)) return false;
        phase++; frames = 0; changed = false;
        if (phase == 1) minecraft.options.fov().set(100);
        if (phase == 2) distort(minecraft);
        if (phase == 3) System.out.println("Kernel native projection inputs passed: uploaded world matrix, inverse, previous frame, live FOV, screen distortion and invalid-frame recovery");
        return phase == 3;
    }
    private static void distort(Minecraft minecraft) {
        //? if >=1.21.5 {
        minecraft.player.oPortalEffectIntensity = .7f;
        minecraft.player.portalEffectIntensity = .8f;
        //? } else {
        /*minecraft.player.oSpinningEffectIntensity = .7f;
        minecraft.player.spinningEffectIntensity = .8f;
        *///? }
    }
}
