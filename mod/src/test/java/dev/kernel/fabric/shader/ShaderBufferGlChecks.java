package dev.kernel.fabric.shader;

import dev.kernel.fabric.shader.pack.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;

/** Original native-GL fixtures for declared formats, conversions and temporal image ownership. */
public final class ShaderBufferGlChecks {
    public static void run() throws Exception {
        int pack = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] stores = {GL33C.GL_UNPACK_ROW_LENGTH, GL33C.GL_UNPACK_SKIP_ROWS, GL33C.GL_UNPACK_SKIP_PIXELS,
            GL33C.GL_PACK_ROW_LENGTH, GL33C.GL_PACK_SKIP_ROWS, GL33C.GL_PACK_SKIP_PIXELS};
        int[] oldStores = new int[stores.length];
        for (int i = 0; i < stores.length; i++) { oldStores[i] = GL33C.glGetInteger(stores[i]); GL33C.glPixelStorei(stores[i], 0); }
        GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
        try (var state = new ShaderGlState(2, 1)) {
            state.prepare();
            int source = GL33C.glGenTextures();
            try {
                upload(source, 8, 5);
                precision(source);
                history(source);
                conversion(source);
                legacyDepth(source);
                budget(source);
                if (GL33C.glGetError() != GL33C.GL_NO_ERROR) throw new AssertionError("Shader buffer checks generated a GL error");
            } finally { GL33C.glDeleteTextures(source); }
        } finally {
            GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, pack);
            for (int i = 0; i < stores.length; i++) GL33C.glPixelStorei(stores[i], oldStores[i]);
        }
        System.out.println("Kernel shader buffer checks passed: twelve formats, precision, source conversion, auxiliary history, reset, resize, custom clears, gdepth and byte budgets.");
    }

    private static void precision(int source) throws Exception {
        for (var format : ShaderColorFormat.values()) {
            var parser = new ShaderBufferDirectives();
            parser.read("const int colortex2Format = " + format + ";", "test");
            try (var targets = new ShaderColorTargets(5, parser.build())) {
                targets.begin(source, 8, 5);
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, targets.texture(2));
                if (GL33C.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_INTERNAL_FORMAT) != format.internal())
                    throw new AssertionError("Wrong allocated format: " + format);
                int bits = GL33C.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_RED_SIZE);
                if (bits != 8 * format.bytes() / format.channels()) throw new AssertionError("Wrong precision: " + format);
                for (float[] clear : new float[][]{{.1234567f,.2345678f,.3456789f,.456789f},{2.123456f,-.375f,2f,.625f}}) {
                    targets.outputs(new int[]{2}); GL33C.glClearBufferfv(GL33C.GL_COLOR, 0, clear); targets.flip(new int[]{2});
                    float[] values = pixels(targets.texture(2), 8, 5);
                    for (int index = 0; index < values.length; index++) {
                        int channel = index % 4;
                        float expected = channel < format.channels() ? clear[channel] : channel == 3 ? 1 : 0;
                        if (!format.floating()) expected = Math.max(0, Math.min(1, expected));
                        float tolerance = format.floating() ? bits == 16 ? Math.max(.00025f, Math.abs(expected) * .0006f) : .0000005f
                            : bits == 8 ? .002f : .00002f;
                        near(expected, values[index], tolerance, format + " precision");
                    }
                }
            }
        }
    }
    private static void history(int source) throws Exception {
        for (boolean clear : new boolean[]{false, true}) {
            var prepared = pack(Map.of("composite.fsh", """
                #version 120
                /* RENDERTARGETS: 7 */
                /* const int colortex7Format = RGBA16F; */
                const bool colortex7Clear = %s;
                const vec4 colortex7ClearColor = vec4(.125, .25, .375, .5);
                varying vec2 texcoord; uniform sampler2D colortex7;
                void main(){gl_FragColor=texture2D(colortex7,texcoord)+vec4(.125);}
                """.formatted(clear), "final.fsh", sample("colortex7", "c")));
            try (var pipeline = new ShaderPipeline(prepared)) {
                for (int width : new int[]{8, 13}) {
                    for (int frame = 0; frame < 3; frame++) {
                        upload(source, width, 5); pipeline.render(source, width, 5);
                        assertSolid(source, width, 5, new float[]{.125f,.25f,.375f,.5f}, .125f * (clear ? 1 : frame + 1), "history " + frame);
                    }
                    pipeline.resetHistory(); upload(source, width, 5); pipeline.render(source, width, 5);
                    assertSolid(source, width, 5, new float[]{.125f,.25f,.375f,.5f}, .125f, "reset history");
                }
            }
        }
        upload(source, 8, 5);
    }
    private static void conversion(int source) throws Exception {
        for (var format : ShaderColorFormat.values()) {
            var prepared = pack(Map.of("final.fsh", "/* const int colortex0Format = " + format + "; */\n"
                + sample("colortex0", "c")));
            try (var pipeline = new ShaderPipeline(prepared)) {
                upload(source, 8, 5); pipeline.render(source, 8, 5);
                float[] expected = {.2f, format.channels() >= 2 ? .4f : 0, format.channels() == 4 ? .6f : 0, 1};
                assertSolid(source, 8, 5, expected, 0, "source channel conversion " + format);
            }
        }
    }
    private static void legacyDepth(int source) throws Exception {
        for (String format : new String[]{"", "RGBA8", "RGBA32F"}) {
            String directive = format.isEmpty() ? "" : "/* const int gdepthFormat = " + format + "; */\n";
            var prepared = pack(Map.of("composite.fsh", "#version 120\n/* RENDERTARGETS:1 */\n" + directive
                + "void main(){gl_FragColor=vec4(1.000030517578125);}",
                "final.fsh", sample("gdepth", "vec4((c.r-1.0)*16384.0,0,0,1)")));
            try (var pipeline = new ShaderPipeline(prepared)) {
                upload(source, 8, 5); pipeline.render(source, 8, 5);
                assertSolid(source, 8, 5, new float[]{format.equals("RGBA8") ? 0 : .5f,0,0,1}, 0, "gdepth " + format);
            }
        }
    }
    private static void budget(int source) throws Exception {
        var parser = new ShaderBufferDirectives(); parser.read("const int colortex0Format = RGBA32F;", "test");
        try (var targets = new ShaderColorTargets(1, parser.build())) {
            targets.begin(source, 8, 5);
            int previous = targets.texture(0);
            // Just above 512 MiB for a float32 pair, but far below the old RGBA8 accounting limit.
            try { targets.begin(source, 4097, 4096); throw new AssertionError("Float allocation exceeded budget"); }
            catch (java.io.IOException expected) { if (!expected.getMessage().contains("budget")) throw expected; }
            if (!GL33C.glIsTexture(previous)) throw new AssertionError("Budget rejection destroyed usable buffers");
        }
    }
    private static String sample(String sampler, String expression) {
        return "#version 120\nvarying vec2 texcoord; uniform sampler2D " + sampler
            + "; void main(){vec4 c=texture2D(" + sampler + ",texcoord);gl_FragColor=" + expression + ";}";
    }
    private static PreparedShaderPack pack(Map<String, String> files) throws Exception {
        var path = Files.createTempFile("kernel-shader-buffers-", ".zip");
        try {
            try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
                for (var file : files.entrySet()) {
                    output.putNextEntry(new ZipEntry("shaders/" + file.getKey()));
                    output.write(file.getValue().getBytes(StandardCharsets.UTF_8)); output.closeEntry();
                }
            }
            return PreparedShaderPack.read(path);
        } finally { Files.deleteIfExists(path); }
    }
    private static void upload(int source, int width, int height) {
        var data = MemoryUtil.memAlloc(width * height * 4);
        try {
            for (int index = 0; index < data.capacity(); index++) data.put(index, (byte) new int[]{51,102,153,255}[index % 4]);
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, source);
            GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, width, height, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, data);
        } finally { MemoryUtil.memFree(data); }
    }
    private static float[] pixels(int texture, int width, int height) {
        var data = MemoryUtil.memAllocFloat(width * height * 4);
        try {
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
            GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA, GL33C.GL_FLOAT, data);
            float[] result = new float[data.remaining()]; data.get(result); return result;
        } finally { MemoryUtil.memFree(data); }
    }
    private static void assertSolid(int texture, int width, int height, float[] expected, float extra, String context) {
        float[] values = pixels(texture, width, height);
        for (int index = 0; index < values.length; index++) near(Math.min(1, expected[index % 4] + extra), values[index], .005f, context);
    }
    private static void near(float expected, float actual, float tolerance, String context) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > tolerance)
            throw new AssertionError(context + ": expected " + expected + ", got " + actual);
    }
}
