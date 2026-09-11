package dev.kernel.fabric.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.kernel.fabric.verification.ShaderProbe;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

/** Observes Minecraft's applied view stack and camera independently of Kernel's capture. */
public final class ShaderCameraWorldChecks {
    private static Matrix4f current, last;
    private static Vec3 position, lastPosition;
    private static int width, height, lastWidth, lastHeight, phase, frames;
    private static boolean started, invalidInjected, forcedSkip;
    private ShaderCameraWorldChecks() {}
    public static void observe(Vec3 nativePosition) {
        if (!ShaderProbe.observingShaderWorld() || phase == 3) return;
        current = new Matrix4f(RenderSystem.getModelViewStack()); position = nativePosition;
        var minecraft = Minecraft.getInstance();
        //? if >=26.2 {
        var target = minecraft.gameRenderer.mainRenderTarget();
        //? } else {
        /*var target = minecraft.getMainRenderTarget();
        *///? }
        width = target.width; height = target.height; forcedSkip = false;
        if (!invalidInjected && current.isFinite()) {
            invalidInjected = forcedSkip = true;
            KernelShaders.captureView(new Matrix4f().m00(Float.NaN), position);
        }
    }
    public static boolean skipInvalidFrame() {
        if (phase == 3 || !forcedSkip) return false;
        if (KernelShaders.active().isEmpty() || KernelShaders.failed()) throw new AssertionError("Transient invalid view disabled the pack");
        return true;
    }
    public static void verifyOutput(int w, int h) {
        if (phase == 3) return;
        if (current == null || width != w || height != h) throw new AssertionError("Native view was not observed");
        boolean history = last != null && lastWidth == width && lastHeight == height;
        Matrix4f before = history ? last : current;
        Vec3 previous = history ? lastPosition : position;
        Matrix4f[] matrices = {current, current.invert(new Matrix4f()), before};
        try (var stack = MemoryStack.stackPush()) {
            var pixel = stack.malloc(4);
            for (int row = 0; row < 3; row++) {
                float[] expected = matrices[row].get(new float[16]);
                for (int i = 0; i < 16; i++) {
                    int bits = read(22 + i, 3 + row, pixel);
                    float actual = Float.intBitsToFloat(bits);
                    if (!Float.isFinite(actual) || Math.abs(actual - expected[i]) > 2e-5)
                        throw new AssertionError("Native model-view differs: row=" + row + ", component=" + i + ", actual=" + actual + ", expected=" + expected[i]);
                }
            }
            double[] now = {position.x, position.y, position.z}, old = {previous.x, previous.y, previous.z};
            for (int i = 0; i < 19; i++) {
                int bits = read(38 + i, 3, pixel);
                int axis = i % 3;
                if (i >= 12 && i < 18) {
                    int expected = (int) Math.floor(i < 15 ? now[axis] : old[axis]);
                    if (bits != expected) throw new AssertionError("Native integer camera bits differ");
                } else {
                    double value = i < 3 ? now[axis] : i < 6 ? old[axis] : i < 9 ? now[axis] - Math.floor(now[axis])
                        : i < 12 ? old[axis] - Math.floor(old[axis]) : position.y;
                    float expected = (float) value;
                    if (i >= 6 && i < 12) expected = Math.min(expected, Math.nextDown(1f));
                    float actual = Float.intBitsToFloat(bits);
                    if (!Float.isFinite(actual) || actual != expected) throw new AssertionError("Native camera component " + i + ": " + actual + " != " + expected);
                }
            }
        }
        last = new Matrix4f(current); lastPosition = position; lastWidth = width; lastHeight = height; frames++;
    }
    private static int read(int x, int y, java.nio.ByteBuffer pixel) {
        GL33C.glReadPixels(x, y, 1, 1, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixel);
        int bits = 0; for (int c = 0; c < 4; c++) bits |= (pixel.get(c) & 255) << (8 * c); return bits;
    }
    public static boolean advance(Minecraft minecraft) {
        if (phase == 3) return true;
        if (!started) { started = true; configure(minecraft); return false; }
        if (frames < 5) return false;
        if (phase != 0 && position.distanceToSqr(minecraft.player.getEyePosition(1)) < 1)
            throw new AssertionError("Detached camera check did not leave the player eye position");
        System.out.println("Kernel native camera mode " + phase + " passed: world view stack, inverse/history and camera position pixels");
        phase++;
        if (phase < 3) configure(minecraft);
        return phase == 3;
    }
    private static void configure(Minecraft minecraft) {
        frames = 0;
        minecraft.options.setCameraType(phase == 0 ? CameraType.FIRST_PERSON : phase == 1 ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
        float yaw = 35 + phase * 65, pitch = 15 + phase * 5;
        minecraft.player.setYRot(yaw); minecraft.player.yRotO = yaw;
        minecraft.player.setXRot(pitch); minecraft.player.xRotO = pitch;
        minecraft.player.setPos(minecraft.player.getX() + .25, minecraft.player.getY() + 2, minecraft.player.getZ() + .5);
    }
}
