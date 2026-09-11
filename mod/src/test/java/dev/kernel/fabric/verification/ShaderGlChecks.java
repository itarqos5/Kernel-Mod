package dev.kernel.fabric.verification;

import dev.kernel.fabric.shader.ShaderPipeline;
import dev.kernel.fabric.shader.pack.PreparedShaderPack;
import dev.kernel.fabric.shader.pack.ShaderSource;
import java.util.Arrays;
import java.util.List;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

/** Original tiny fixtures verify actual driver pixels, pass ordering, resize, failure and GL state recovery. */
final class ShaderGlChecks {
    static void run() throws Exception {
        int texture = GL33C.glGenTextures();
        int binding = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
        int unpack = GL33C.glGetInteger(GL33C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int pack = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] stores = {GL33C.GL_UNPACK_ROW_LENGTH, GL33C.GL_UNPACK_SKIP_ROWS, GL33C.GL_UNPACK_SKIP_PIXELS,
            GL33C.GL_PACK_ROW_LENGTH, GL33C.GL_PACK_SKIP_ROWS, GL33C.GL_PACK_SKIP_PIXELS};
        int[] storeValues = new int[stores.length];
        for (int i = 0; i < stores.length; i++) { storeValues[i] = GL33C.glGetInteger(stores[i]); GL33C.glPixelStorei(stores[i], 0); }
        int[] altered = { GL33C.GL_CLIP_DISTANCE0, GL33C.GL_RASTERIZER_DISCARD, GL33C.GL_COLOR_LOGIC_OP, GL33C.GL_SAMPLE_ALPHA_TO_ONE };
        boolean[] oldEnable = new boolean[altered.length];
        for (int i = 0; i < altered.length; i++) { oldEnable[i] = GL33C.glIsEnabled(altered[i]); GL33C.glEnable(altered[i]); }
        boolean clip = GL.getCapabilities().OpenGL45 || GL.getCapabilities().GL_ARB_clip_control;
        int origin = clip ? GL33C.glGetInteger(GL45C.GL_CLIP_ORIGIN) : 0, depth = clip ? GL33C.glGetInteger(GL45C.GL_CLIP_DEPTH_MODE) : 0;
        if (clip) GL45C.glClipControl(GL45C.GL_UPPER_LEFT, GL45C.GL_ZERO_TO_ONE);
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0); GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
        String source = """
            #version 120
            varying vec2 texcoord;
            uniform sampler2D colortex0;
            void main() { vec4 c = texture2D(colortex0, texcoord); gl_FragColor = vec4(1.0 - c.rgb, c.a); }
            """;
        String vertex = """
            #version 120
            varying vec2 texcoord;
            void main() { texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy; gl_Position = ftransform(); }
            """;
        var pass = new PreparedShaderPack.Pass("invert", ShaderSource.translate(vertex, true), ShaderSource.translate(source, false));
        try {
            for (int passes : new int[]{1, 2}) {
                var list = passes == 1 ? List.of(pass) : List.of(pass, pass);
                int[] before = bindings();
                try (var pipeline = new ShaderPipeline(new PreparedShaderPack("test", list))) {
                    assertBindings(before);
                    for (int width : new int[]{8, 13}) {
                        int height = 5;
                        var input = MemoryUtil.memAlloc(width * height * 4);
                        var output = MemoryUtil.memAlloc(input.capacity());
                        try {
                            for (int i = 0; i < input.capacity(); i++) input.put(i, (byte) (i * 17 + 3));
                            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
                            GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, width, height, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, input);
                            GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_NEAREST);
                            GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_NEAREST);
                            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, binding);
                            before = bindings(); pipeline.render(texture, width, height); assertBindings(before);
                            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
                            GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, output);
                            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, binding);
                            for (int i = 0; i < input.capacity(); i++) {
                                int expected = input.get(i) & 255;
                                if (passes == 1 && i % 4 != 3) expected = 255 - expected;
                                if (Math.abs(expected - (output.get(i) & 255)) > 1) throw new AssertionError("Shader pixel differs at " + i + ": " + expected + " != " + (output.get(i) & 255));
                            }
                        } finally { MemoryUtil.memFree(input); MemoryUtil.memFree(output); }
                    }
                }
            }
            int[] before = bindings();
            var invalid = new PreparedShaderPack.Pass("invalid", ShaderSource.DEFAULT_VERTEX, "#version 330 core\ncompile error");
            try (var ignored = new ShaderPipeline(new PreparedShaderPack("bad", List.of(invalid)))) { throw new AssertionError("Invalid shader compiled"); }
            catch (java.io.IOException expected) { }
            assertBindings(before);
            var unsupported = new PreparedShaderPack.Pass("unknown", ShaderSource.DEFAULT_VERTEX,
                "#version 330 core\nuniform sampler2D shadowtex0; in vec2 texcoord; out vec4 color; void main() {color=texture(shadowtex0,texcoord);}");
            try (var ignored = new ShaderPipeline(new PreparedShaderPack("bad", List.of(unsupported)))) { throw new AssertionError("Unsupported uniform was silently accepted"); }
            catch (java.io.IOException expected) { }
            assertBindings(before);
            int error = GL33C.glGetError();
            if (error != GL33C.GL_NO_ERROR) throw new AssertionError("OpenGL shader probe error: " + error);
        } finally {
            GL33C.glDeleteTextures(texture); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, binding);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, unpack); GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, pack);
            for (int i = 0; i < stores.length; i++) GL33C.glPixelStorei(stores[i], storeValues[i]);
            for (int i = 0; i < altered.length; i++) { if (oldEnable[i]) GL33C.glEnable(altered[i]); else GL33C.glDisable(altered[i]); }
            if (clip) GL45C.glClipControl(origin, depth);
        }
        System.out.println("Kernel shader GL checks passed: legacy translation, pixels, multiple passes, resize, failed compile/uniform and state restoration.");
    }
    private static int[] bindings() {
        int[] viewport = new int[4]; GL33C.glGetIntegerv(GL33C.GL_VIEWPORT, viewport);
        int[] states = { GL33C.GL_CURRENT_PROGRAM, GL33C.GL_VERTEX_ARRAY_BINDING, GL33C.GL_DRAW_FRAMEBUFFER_BINDING,
            GL33C.GL_READ_FRAMEBUFFER_BINDING, GL33C.GL_ACTIVE_TEXTURE, GL33C.GL_TEXTURE_BINDING_2D,
            GL33C.GL_SAMPLER_BINDING, GL33C.GL_PIXEL_UNPACK_BUFFER_BINDING };
        int[] capabilities = {GL33C.GL_BLEND, GL33C.GL_DEPTH_TEST, GL33C.GL_CULL_FACE, GL33C.GL_SCISSOR_TEST, GL33C.GL_STENCIL_TEST, GL33C.GL_FRAMEBUFFER_SRGB,
            GL33C.GL_CLIP_DISTANCE0, GL33C.GL_RASTERIZER_DISCARD, GL33C.GL_COLOR_LOGIC_OP, GL33C.GL_SAMPLE_ALPHA_TO_ONE};
        int[] values = new int[states.length + 4 + capabilities.length + 2];
        for (int i = 0; i < states.length; i++) values[i] = GL33C.glGetInteger(states[i]);
        System.arraycopy(viewport, 0, values, states.length, 4);
        for (int i = 0; i < capabilities.length; i++) values[states.length + 4 + i] = GL33C.glIsEnabled(capabilities[i]) ? 1 : 0;
        if (GL.getCapabilities().OpenGL45 || GL.getCapabilities().GL_ARB_clip_control) {
            values[values.length - 2] = GL33C.glGetInteger(GL45C.GL_CLIP_ORIGIN);
            values[values.length - 1] = GL33C.glGetInteger(GL45C.GL_CLIP_DEPTH_MODE);
        }
        return values;
    }
    private static void assertBindings(int[] before) { if (!Arrays.equals(before, bindings())) throw new AssertionError("Shader pipeline leaked native GL state"); }
}
