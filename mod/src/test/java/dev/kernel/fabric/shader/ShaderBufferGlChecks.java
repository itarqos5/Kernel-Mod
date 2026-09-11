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
                mipmaps(source);
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
            try (var targets = new ShaderColorTargets(5, parser.build(), 4)) {
                targets.begin(source, 8, 5);
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, targets.texture(2));
                if (GL33C.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_INTERNAL_FORMAT) != format.internal())
                    throw new AssertionError("Wrong allocated format: " + format);
                int bits = GL33C.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_TEXTURE_RED_SIZE);
                if (bits != 8 * format.bytes() / format.channels()) throw new AssertionError("Wrong precision: " + format);
                for (float[] clear : new float[][]{{.1234567f,.2345678f,.3456789f,.456789f},{2.123456f,-.375f,2f,.625f}}) {
                    targets.outputs(new int[]{2}); GL33C.glClearBufferfv(GL33C.GL_COLOR, 0, clear); targets.flip(new int[]{2});
                    targets.mipmaps(4);
                    float[] values = pixels(targets.texture(2), 2, 1, 2);
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
    private static void mipmaps(int source) throws Exception {
        var parser = new ShaderBufferDirectives();
        if (parser.read("const bool gaux4MipmapEnabled = true;", "composite.fsh", true) != 128
            || parser.read("const bool gaux4MipmapEnabled = false;", "final.fsh", true) != 0)
            throw new AssertionError("Mipmap requests leaked between passes");
        if (parser.build().allocationBytes(1, 1, 8, 5) != 408
            || parser.build().allocationBytes(1, 0, 8, 5) != 320
            || parser.build().allocationBytes(1, 1, 1, 9) != 128
            || parser.build().allocationBytes(0xffff, 0xffff, Integer.MAX_VALUE, Integer.MAX_VALUE) != Long.MAX_VALUE)
            throw new AssertionError("Mipmap memory accounting differs");
        for (int width : new int[]{8, 16}) {
            var prepared = pack(Map.of("final.fsh", "#version 120\nconst bool colortex0MipmapEnabled = true;\n"
                + "uniform sampler2D colortex0; varying vec2 texcoord; void main(){gl_FragColor=texture2DLod(colortex0,texcoord,4.0);}"));
            try (var pipeline = new ShaderPipeline(prepared)) {
                checkerboard(source, width, 8); renderChecked(pipeline, source, width, 8);
                assertSolid(source, width, 8, new float[]{.5f,.5f,.5f,1}, 0, "source mipmaps");
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, source);
                if (GL33C.glGetTexLevelParameteri(GL33C.GL_TEXTURE_2D, 1, GL33C.GL_TEXTURE_WIDTH) != 0)
                    throw new AssertionError("Native world texture acquired Kernel mipmaps");
            }
        }
        // Regenerate the current side immediately before each requesting pass, after previous writes.
        var files = new LinkedHashMap<String, String>();
        files.put("composite.fsh", "#version 120\n/* RENDERTARGETS:7 */\nvoid main(){gl_FragColor=vec4(vec3(mod(gl_FragCoord.x+gl_FragCoord.y,2.0)),1);}");
        files.put("composite1.fsh", "#version 120\n/* RENDERTARGETS:7 */\nconst bool colortex7MipmapEnabled=true;\n"
            + "uniform sampler2D gaux4; varying vec2 texcoord;void main(){gl_FragColor=texture2DLod(gaux4,texcoord,3.0)*vec4(.5,.5,.5,1);}");
        files.put("final.fsh", "#version 120\nconst bool gaux4MipmapEnabled=true;\n"
            + "uniform sampler2D colortex7; varying vec2 texcoord;void main(){gl_FragColor=texture2DLod(colortex7,texcoord,3.0);}");
        try (var pipeline = new ShaderPipeline(pack(files))) {
            for (int width : new int[]{8, 16}) for (int frame = 0; frame < 2; frame++) {
                upload(source, width, 8); renderChecked(pipeline, source, width, 8);
                assertSolid(source, width, 8, new float[]{.25f,.25f,.25f,1}, 0, "regenerated mipmaps");
            }
        }
        upload(source, 8, 5);
        try (var targets = new ShaderColorTargets(1, ShaderBufferSettings.defaults(), 1)) {
            // The base pair fits; the complete pair of chains exceeds the budget.
            try { targets.begin(source, 8000, 8000); throw new AssertionError("Mipmap allocation exceeded budget"); }
            catch (java.io.IOException expected) { if (!expected.getMessage().contains("budget")) throw expected; }
        }
        try (var targets = new ShaderColorTargets(1, ShaderBufferSettings.defaults(), 0)) {
            targets.begin(source, 8, 5);
            int previous = targets.texture(0), limit = GL33C.glGetInteger(GL33C.GL_MAX_TEXTURE_SIZE);
            try { targets.begin(source, limit + 1, 1); throw new AssertionError("Oversized texture accepted"); }
            catch (java.io.IOException expected) { if (!expected.getMessage().contains("texture limit")) throw expected; }
            if (!GL33C.glIsTexture(previous)) throw new AssertionError("Dimension rejection destroyed usable buffers");
        }
        System.out.println("Kernel shader mipmap checks passed: per-pass requests, memory accounting, source ownership, downsampling, feedback, regeneration and GL state.");
    }
    private static void renderChecked(ShaderPipeline pipeline, int source, int width, int height) throws Exception {
        try (var state = new ShaderGlState(1, 1)) {
            int sentinel = GL33C.glGenSamplers();
            try {
                GL33C.glBindSampler(0, sentinel); GL33C.glActiveTexture(GL33C.GL_TEXTURE3);
                GL33C.glColorMaski(0, false, true, false, false); GL33C.glEnablei(GL33C.GL_BLEND, 0);
                pipeline.render(source, width, height);
                if (GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE) != GL33C.GL_TEXTURE3) throw new AssertionError("Mipmap active texture leak");
                GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
                if (GL33C.glGetInteger(GL33C.GL_SAMPLER_BINDING) != sentinel
                    || GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D) != source)
                    throw new AssertionError("Mipmap texture/sampler state leak");
                try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                    var mask = stack.malloc(4); GL33C.glGetBooleani_v(GL33C.GL_COLOR_WRITEMASK, 0, mask);
                    if (mask.get(0) != 0 || mask.get(1) == 0 || mask.get(2) != 0 || mask.get(3) != 0 || !GL33C.glIsEnabledi(GL33C.GL_BLEND, 0))
                        throw new AssertionError("Mipmap blend/mask state leak");
                }
            } finally { GL33C.glDeleteSamplers(sentinel); }
        }
    }
    private static void checkerboard(int texture, int width, int height) {
        var data = MemoryUtil.memAlloc(width * height * 4);
        try {
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (int channel = 0; channel < 4; channel++)
                data.put((y * width + x) * 4 + channel, (byte) (channel == 3 || ((x + y) & 1) != 0 ? 255 : 0));
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
            GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, width, height, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, data);
        } finally { MemoryUtil.memFree(data); }
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
        return pixels(texture, width, height, 0);
    }
    private static float[] pixels(int texture, int width, int height, int level) {
        var data = MemoryUtil.memAllocFloat(width * height * 4);
        try {
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture);
            GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, level, GL33C.GL_RGBA, GL33C.GL_FLOAT, data);
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
