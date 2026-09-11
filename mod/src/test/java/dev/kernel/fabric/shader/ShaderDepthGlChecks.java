package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryStack;

/** Driver checks for depth ownership, forward/reverse conversion and independent world/hand composition. */
public final class ShaderDepthGlChecks {
    private ShaderDepthGlChecks() {}
    public static void run() throws Exception {
        int[] packNames = {GL33C.GL_PACK_ALIGNMENT, GL33C.GL_PACK_ROW_LENGTH, GL33C.GL_PACK_SKIP_ROWS, GL33C.GL_PACK_SKIP_PIXELS};
        int[] previous = new int[packNames.length];
        int packBuffer = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
        for (int i = 0; i < packNames.length; i++) previous[i] = GL33C.glGetInteger(packNames[i]);
        int[] sources = new int[6];
        int color = 0;
        try (var state = new ShaderGlState(6, 1)) {
            state.prepare();
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
            for (int name : packNames) GL33C.glPixelStorei(name, name == GL33C.GL_PACK_ALIGNMENT ? 1 : 0);
            for (int i = 0; i < 6; i++) sources[i] = GL33C.glGenTextures();
            color = GL33C.glGenTextures();
            int ownedTexture = 0, ownedSampler = 0;
            try (var depth = new ShaderDepthTarget()) {
                if (depth.sampler() == 0 || !GL33C.glIsSampler(depth.sampler()))
                    throw new AssertionError("Depth program resources were deferred until the first world frame");
                for (boolean reverse : new boolean[]{false, true}) for (int width : new int[]{13, 1, 5}) {
                    int height = width == 1 ? 1 : 3, size = width * height;
                    float[][] values = new float[6][size];
                    for (int input = 0; input < 6; input++) {
                        for (int pixel = 0; pixel < size; pixel++) {
                            float forward = pixel % 7 == 0 ? 1 : (input + pixel) % 6 / 8f;
                            values[input][pixel] = reverse ? 1 - forward : forward;
                        }
                        upload(sources[input], width, height, values[input]);
                    }
                    for (int count : new int[]{1, 3, 6}) {
                        float[] expected = new float[size]; Arrays.fill(expected, 1);
                        for (int input = 0; input < count; input++) for (int pixel = 0; pixel < size; pixel++)
                            expected[pixel] = Math.min(expected[pixel], reverse ? 1 - values[input][pixel] : values[input][pixel]);
                        boolean clip = GL.getCapabilities().OpenGL45 || GL.getCapabilities().GL_ARB_clip_control;
                        if (clip) GL45C.glClipControl(GL45C.GL_UPPER_LEFT, GL45C.GL_ZERO_TO_ONE);
                        GL33C.glEnable(GL33C.GL_RASTERIZER_DISCARD); GL33C.glEnable(GL33C.GL_SCISSOR_TEST);
                        GL33C.glEnablei(GL33C.GL_BLEND, 0); GL33C.glColorMaski(0, false, true, false, true);
                        GL33C.glViewport(4, 3, 7, 8);
                        GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + 2);
                        int[] before = bindings();
                        depth.capture(Arrays.copyOf(sources, count), width, height, reverse, false);
                        if (!Arrays.equals(before, bindings())) throw new AssertionError("Depth capture leaked GL state");
                        GL33C.glDisable(GL33C.GL_RASTERIZER_DISCARD); GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
                        GL33C.glDisablei(GL33C.GL_BLEND, 0); GL33C.glColorMaski(0, true, true, true, true);
                        if (clip) GL45C.glClipControl(GL45C.GL_LOWER_LEFT, GL45C.GL_NEGATIVE_ONE_TO_ONE);
                        ownedTexture = depth.texture(width, height); ownedSampler = depth.sampler();
                        assertPixels(ownedTexture, GL33C.GL_RED, expected, "world depth");
                        for (int input = 0; input < count; input++) {
                            assertPixels(sources[input], GL33C.GL_DEPTH_COMPONENT, values[input], "native input changed");
                            if (GL33C.glGetTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_COMPARE_MODE) != GL33C.GL_COMPARE_REF_TO_TEXTURE)
                                throw new AssertionError("Native depth compare mode changed");
                        }
                        float[] hand = new float[size];
                        for (int pixel = 0; pixel < size; pixel++) {
                            float value = pixel % 2 == 0 ? 1 : .875f;
                            hand[pixel] = reverse ? 1 - value : value;
                            if (pixel % 2 != 0) expected[pixel] = .875f; // The first-person image overlays nearer world depths too.
                        }
                        upload(sources[0], width, height, hand);
                        depth.capture(new int[]{sources[0]}, width, height, reverse, true);
                        assertPixels(ownedTexture, GL33C.GL_RED, expected, "hand depth");
                        upload(sources[0], width, height, values[0]);
                        depth.invalidate();
                        expectFailure(() -> depth.texture(width, height), "stale depth");
                        expectFailure(() -> depth.capture(new int[]{sources[0]}, width, height, reverse, true), "hand without world");
                    }
                }
                expectFailure(() -> depth.capture(new int[0], 1, 1, false, false), "empty inputs");
                expectFailure(() -> depth.capture(new int[7], 1, 1, false, false), "excess inputs");
                expectFailure(() -> depth.capture(new int[]{0}, 1, 1, false, false), "missing texture");
                expectFailure(() -> depth.capture(new int[]{sources[0]}, 0, 0, false, false), "zero dimensions");
                expectFailure(() -> depth.capture(new int[]{sources[0]}, 2, 2, false, false), "mismatched dimensions");
            }
            if (GL33C.glIsTexture(ownedTexture) || GL33C.glIsSampler(ownedSampler)) throw new AssertionError("Depth resources leaked");
            var closedDepth = new ShaderDepthTarget(); closedDepth.close();
            expectFailure(() -> closedDepth.capture(new int[]{sources[0]}, 5, 3, false, false), "closed depth capture");
            pipelineChecks(sources[0], color);
            if (GL33C.glGetError() != GL33C.GL_NO_ERROR) throw new AssertionError("Depth checks generated an OpenGL error");
        } finally {
            for (int source : sources) if (source != 0) GL33C.glDeleteTextures(source);
            if (color != 0) GL33C.glDeleteTextures(color);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, packBuffer);
            for (int i = 0; i < packNames.length; i++) GL33C.glPixelStorei(packNames[i], previous[i]);
        }
        System.out.println("Kernel depth shader checks passed: native depth pixels, reverse-Z, transparent sources, hand overlays, aliases, fresh frames, resize, limits and GL ownership");
    }
    private static void pipelineChecks(int source, int color) throws Exception {
        var pass = new PreparedShaderPack.Pass("depth", ShaderSource.DEFAULT_VERTEX, ShaderSource.translate("""
            #version 330 core
            uniform sampler2D depthtex0, gdepthtex;
            out vec4 outColor;
            void main() { ivec2 p=ivec2(gl_FragCoord.xy); outColor=vec4(texelFetch(depthtex0,p,0).r,texelFetch(gdepthtex,p,0).r,MC_HAND_DEPTH,1.0); }
            """, false));
        float[] values = {0, .125f, .5f, .875f, 1};
        upload(source, 5, 1, values);
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, color);
        GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, 5, 1, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, 0L);
        try (var pipeline = new ShaderPipeline(new PreparedShaderPack("depth-alias", List.of(pass)))) {
            if (!pipeline.needsDepth() || pipeline.needsWorldData()) throw new AssertionError("Depth requirements differ");
            expectFailure(() -> pipeline.render(color, 5, 1), "missing world capture");
            pipeline.captureDepth(new int[]{source}, 5, 1, false, false);
            pipeline.render(color, 5, 1);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, color);
            try (var stack = MemoryStack.stackPush()) {
                var pixels = stack.malloc(20);
                GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixels);
                for (int i = 0; i < 5; i++) for (int channel = 0; channel < 4; channel++)
                    if (Math.abs((pixels.get(i * 4 + channel) & 255) - (channel < 2 ? values[i] * 255 : 255)) > 1)
                        throw new AssertionError("Pipeline depth alias pixel differs");
            }
            expectFailure(() -> pipeline.render(color, 5, 1), "reused previous frame");
            pipeline.captureDepth(new int[]{source}, 5, 1, false, false);
            pipeline.resetHistory(); expectFailure(() -> pipeline.render(color, 5, 1), "world transition");
        }
        var buffers = new ArrayList<>(ShaderBufferSettings.defaults().buffers());
        buffers.set(0, new ShaderBufferSettings.Buffer(ShaderColorFormat.RGBA32F, true, true, new ShaderBufferSettings.Color(0,0,0,0)));
        try (var pipeline = new ShaderPipeline(new PreparedShaderPack("depth-budget", List.of(pass), new ShaderBufferSettings(buffers)))) {
            expectFailure(() -> pipeline.captureDepth(new int[]{source}, 4096, 4096, false, false), "combined GPU memory budget");
        }
        for (String invalid : new String[]{"uniform float depthtex0; void main(){outColor=vec4(depthtex0);}",
            "uniform sampler2DShadow depthtex0; void main(){outColor=vec4(texture(depthtex0,vec3(.5)));}",
            "uniform sampler2D depthtex0[2]; void main(){outColor=texture(depthtex0[0],vec2(.5))+texture(depthtex0[1],vec2(.5));}"}) {
            var bad = new PreparedShaderPack.Pass("invalid-depth", ShaderSource.DEFAULT_VERTEX,
                ShaderSource.translate("#version 330 core\nout vec4 outColor;\n" + invalid, false));
            expectFailure(() -> { try (var ignored = new ShaderPipeline(new PreparedShaderPack("invalid-depth", List.of(bad)))) { } }, "depth uniform type");
        }
    }
    private static void upload(int texture, int width, int height, float[] values) {
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
        GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_DEPTH_COMPONENT32F, width, height, 0, GL33C.GL_DEPTH_COMPONENT, GL33C.GL_FLOAT, values);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_COMPARE_MODE, GL33C.GL_COMPARE_REF_TO_TEXTURE);
    }
    private static void assertPixels(int texture, int format, float[] expected, String label) {
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
        float[] actual = new float[expected.length];
        GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, 0, format, GL33C.GL_FLOAT, actual);
        for (int i = 0; i < expected.length; i++) if (!Float.isFinite(actual[i]) || Math.abs(actual[i] - expected[i]) > 1e-7f)
            throw new AssertionError(label + " at " + i + ": " + actual[i] + " != " + expected[i]);
    }
    private static int[] bindings() {
        int[] result = new int[25];
        int active = GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE);
        result[0] = active; result[1] = GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM);
        result[2] = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING);
        result[3] = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
        result[4] = GL33C.glGetInteger(GL33C.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4]; GL33C.glGetIntegerv(GL33C.GL_VIEWPORT, viewport);
        System.arraycopy(viewport, 0, result, 5, 4);
        for (int i = 0; i < 6; i++) {
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + i);
            result[9+i*2] = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
            result[10+i*2] = GL33C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
        }
        result[21] = GL33C.glIsEnabled(GL33C.GL_RASTERIZER_DISCARD) ? 1 : 0;
        result[22] = GL33C.glIsEnabled(GL33C.GL_SCISSOR_TEST) ? 1 : 0;
        if (GL.getCapabilities().OpenGL45 || GL.getCapabilities().GL_ARB_clip_control) {
            result[23] = GL33C.glGetInteger(GL45C.GL_CLIP_ORIGIN); result[24] = GL33C.glGetInteger(GL45C.GL_CLIP_DEPTH_MODE);
        }
        GL33C.glActiveTexture(active); return result;
    }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
    private static void expectFailure(IoAction action, String label) throws IOException {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Accepted " + label);
    }
}
