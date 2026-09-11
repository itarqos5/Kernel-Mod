package dev.kernel.fabric.shader;

import dev.kernel.fabric.verification.ShaderProbe;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.CameraType;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

/** Test-only observations of actual world targets, including the native transparency frame graph. */
public final class ShaderDepthWorldChecks {
    private static int mode, worlds, hands, largest, width, height;
    private static boolean worldGeometry, handGeometry;
    private static final int COLUMNS = 13, ROWS = 9;
    private static float[] expected;
    private static ShaderPipeline owner;
    private ShaderDepthWorldChecks() {}
    public static void prepare(Minecraft minecraft) {
        //? if >=26.2 {
        if (minecraft.gui.hud.isHidden()) minecraft.gui.hud.toggle();
        //? } else {
        /*minecraft.options.hideGui = false;
        *///? }
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        transparency(minecraft, false); minecraft.options.cloudStatus().set(CloudStatus.OFF);
    }
    private static void transparency(Minecraft minecraft, boolean enabled) {
        //? if >=1.21.11 {
        minecraft.options.improvedTransparency().set(enabled);
        //? } else {
        /*minecraft.options.graphicsMode().set(enabled ? net.minecraft.client.GraphicsStatus.FABULOUS : net.minecraft.client.GraphicsStatus.FANCY);
        *///? }
    }
    public static boolean advance(Minecraft minecraft) {
        if (mode == 4) return true;
        if (worlds < 2 || hands < 2 || !worldGeometry || (mode < 3 && !handGeometry)) return false;
        if (mode == 3 && handGeometry) throw new AssertionError("Third-person pass unexpectedly wrote first-person depth");
        if (largest != (mode == 0 ? 1 : mode == 1 ? 6 : 5))
            throw new AssertionError("Unexpected depth target count in mode " + mode + ": " + largest);
        System.out.println("Kernel native depth mode " + mode + " passed: " + largest + " targets, "
            + (mode < 3 ? "world/hand geometry" : "third-person world preservation") + " and final sampler pixels");
        mode++; worlds = hands = largest = 0; worldGeometry = handGeometry = false;
        if (mode == 1) { transparency(minecraft, true); minecraft.options.cloudStatus().set(CloudStatus.FANCY); }
        if (mode == 2) minecraft.options.cloudStatus().set(CloudStatus.OFF);
        if (mode == 3) minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        return mode == 4;
    }
    public static void observe(ShaderPipeline pipeline, int[] sources, int w, int h, boolean reverse, boolean hand) throws Exception {
        if (mode == 4 || !ShaderProbe.observingDepth()) return;
        if (!hand) {
            owner = pipeline; width = w; height = h; expected = new float[COLUMNS * ROWS]; Arrays.fill(expected, 1);
            worlds++; largest = Math.max(largest, sources.length);
        } else {
            if (owner != pipeline || width != w || height != h || expected == null) throw new AssertionError("Native hand lost its world capture");
            hands++;
        }
        var field = ShaderPipeline.class.getDeclaredField("depth"); field.setAccessible(true);
        var depth = (ShaderDepthTarget) field.get(pipeline);
        int output = depth.texture(w, h);
        int[] names = {GL33C.GL_PACK_ALIGNMENT, GL33C.GL_PACK_ROW_LENGTH, GL33C.GL_PACK_SKIP_ROWS, GL33C.GL_PACK_SKIP_PIXELS};
        int[] previous = new int[names.length];
        for (int i = 0; i < names.length; i++) previous[i] = GL33C.glGetInteger(names[i]);
        int pbo = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
        int framebuffer = GL33C.glGenFramebuffers();
        try (var state = new ShaderGlState(); var stack = MemoryStack.stackPush()) {
            state.prepare(); GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
            for (int name : names) GL33C.glPixelStorei(name, name == GL33C.GL_PACK_ALIGNMENT ? 1 : 0);
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, framebuffer);
            GL33C.glDrawBuffer(GL33C.GL_NONE); GL33C.glReadBuffer(GL33C.GL_NONE);
            var pixel = stack.mallocFloat(1);
            for (int source : sources) {
                GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_DEPTH_ATTACHMENT, GL33C.GL_TEXTURE_2D, source, 0);
                if (GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER) != GL33C.GL_FRAMEBUFFER_COMPLETE)
                    throw new AssertionError("Native depth probe framebuffer is incomplete");
                for (int index = 0; index < expected.length; index++) {
                    GL33C.glReadPixels(x(index, w), y(index, h), 1, 1, GL33C.GL_DEPTH_COMPONENT, GL33C.GL_FLOAT, pixel);
                    float value = pixel.get(0), forward = reverse ? 1 - value : value;
                    if (hand) {
                        if (value != (reverse ? 0 : 1)) { expected[index] = forward; handGeometry = true; }
                    } else {
                        expected[index] = Math.min(expected[index], forward);
                        if (value != (reverse ? 0 : 1)) worldGeometry = true;
                    }
                }
            }
            GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_DEPTH_ATTACHMENT, GL33C.GL_TEXTURE_2D, 0, 0);
            GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, output, 0);
            GL33C.glReadBuffer(GL33C.GL_COLOR_ATTACHMENT0);
            for (int index = 0; index < expected.length; index++) {
                GL33C.glReadPixels(x(index, w), y(index, h), 1, 1, GL33C.GL_RED, GL33C.GL_FLOAT, pixel);
                if (!Float.isFinite(pixel.get(0)) || Math.abs(pixel.get(0) - expected[index]) > 2e-7f)
                    throw new AssertionError("Native " + (hand ? "hand" : "world") + " depth differs at " + index + ": " + pixel.get(0) + " != " + expected[index]);
            }
        } finally {
            GL33C.glDeleteFramebuffers(framebuffer); GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, pbo);
            for (int i = 0; i < names.length; i++) GL33C.glPixelStorei(names[i], previous[i]);
        }
    }
    public static void verifyOutput(int w, int h) {
        if (mode == 4 || expected == null || width != w || height != h) return;
        try (var stack = MemoryStack.stackPush()) {
            var pixel = stack.malloc(4);
            GL33C.glReadPixels(4, h / 2, 1, 1, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixel);
            float value = expected[ROWS / 2 * COLUMNS + COLUMNS / 2] * 255;
            for (int i = 0; i < 3; i++) if (Math.abs((pixel.get(i) & 255) - value) > 1.1f)
                throw new AssertionError("The world shader sampled different depth than the native capture");
        }
    }
    private static int x(int index, int w) { return Math.min(w - 1, (int) ((index % COLUMNS + .5) * w / COLUMNS)); }
    private static int y(int index, int h) { return Math.min(h - 1, (int) ((index / COLUMNS + .5) * h / ROWS)); }
}
