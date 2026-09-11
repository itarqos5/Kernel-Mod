package dev.kernel.fabric.verification;

import dev.kernel.fabric.shader.ShaderPipeline;
import dev.kernel.fabric.shader.pack.PreparedShaderPack;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;

/** Original fixtures exercise logical buffer routing and state on an actual GL implementation. */
final class ShaderMultipleTargetsChecks {
    static void run() throws Exception {
        int[] oldTextures = new int[16], oldSamplers = new int[16], sentinels = new int[16], samplers = new int[16];
        boolean[] oldBlend = new boolean[8];
        boolean[][] oldMasks = new boolean[8][4];
        int active = GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE);
        int unpack = GL33C.glGetInteger(GL33C.GL_PIXEL_UNPACK_BUFFER_BINDING), pack = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] stores = {GL33C.GL_UNPACK_ROW_LENGTH, GL33C.GL_UNPACK_SKIP_ROWS, GL33C.GL_UNPACK_SKIP_PIXELS,
            GL33C.GL_PACK_ROW_LENGTH, GL33C.GL_PACK_SKIP_ROWS, GL33C.GL_PACK_SKIP_PIXELS};
        int[] oldStores = new int[stores.length];
        for (int i = 0; i < stores.length; i++) { oldStores[i] = GL33C.glGetInteger(stores[i]); GL33C.glPixelStorei(stores[i], 0); }
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0); GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
        int source = GL33C.glGenTextures();
        try {
            for (int unit = 0; unit < sentinels.length; unit++) {
                GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + unit);
                oldTextures[unit] = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
                oldSamplers[unit] = GL33C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
                sentinels[unit] = GL33C.glGenTextures(); samplers[unit] = GL33C.glGenSamplers();
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, sentinels[unit]); GL33C.glBindSampler(unit, samplers[unit]);
            }
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                var mask = stack.malloc(4);
                for (int slot = 0; slot < 8; slot++) {
                    oldBlend[slot] = GL33C.glIsEnabledi(GL33C.GL_BLEND, slot);
                    GL33C.glGetBooleani_v(GL33C.GL_COLOR_WRITEMASK, slot, mask);
                    for (int channel = 0; channel < 4; channel++) oldMasks[slot][channel] = mask.get(channel) != 0;
                    GL33C.glEnablei(GL33C.GL_BLEND, slot);
                    GL33C.glColorMaski(slot, false, slot % 2 == 0, false, false);
                }
            }
            GL33C.glActiveTexture(GL33C.GL_TEXTURE3);
            for (int style : new int[]{0, 1, 2}) for (boolean finalPass : new boolean[]{false, true}) {
                try (var pipeline = new ShaderPipeline(routed(style, finalPass))) {
                    assertState(sentinels, samplers);
                    for (int width : new int[]{8, 13}) for (int frame = 0; frame < 2; frame++) {
                        int height = 5;
                        java.nio.ByteBuffer input = MemoryUtil.memAlloc(width * height * 4), output = MemoryUtil.memAlloc(width * height * 4);
                        try {
                            for (int i = 0; i < input.capacity(); i++) input.put(i, (byte) (i * 17 + frame * 31 + 3));
                            upload(source, sentinels[0], width, height, input);
                            pipeline.render(source, width, height); assertState(sentinels, samplers);
                            download(source, sentinels[0], output);
                            for (int i = 0; i < output.capacity(); i++) {
                                int value = input.get(i) & 255;
                                int expected = switch (i % 4) {
                                    case 0 -> 255 - value; case 1 -> finalPass ? 255 - value : value;
                                    case 2 -> 128; default -> 255;
                                };
                                if (Math.abs(expected - (output.get(i) & 255)) > 1) throw new AssertionError("MRT routing pixel " + i + ": " + expected + " != " + (output.get(i) & 255));
                            }
                        } finally { MemoryUtil.memFree(input); MemoryUtil.memFree(output); }
                    }
                }
            }
            try (var pipeline = new ShaderPipeline(clearing())) {
                for (int frame = 0; frame < 3; frame++) {
                    java.nio.ByteBuffer input = MemoryUtil.memCalloc(8 * 5 * 4), output = MemoryUtil.memAlloc(8 * 5 * 4);
                    try {
                        upload(source, sentinels[0], 8, 5, input);
                        pipeline.render(source, 8, 5); assertState(sentinels, samplers);
                        download(source, sentinels[0], output);
                        for (int i = 0; i < output.capacity(); i++) {
                            int expected = switch (i % 4) { case 0, 3 -> 255; case 1 -> 64; default -> 0; };
                            if (Math.abs(expected - (output.get(i) & 255)) > 1) throw new AssertionError("Default clearing or buffer-15 feedback differs at frame " + frame);
                        }
                    } finally { MemoryUtil.memFree(input); MemoryUtil.memFree(output); }
                }
                try { pipeline.render(source, 100000, 100000); throw new AssertionError("Unbounded GPU allocation accepted"); }
                catch (java.io.IOException expected) { if (!expected.getMessage().contains("budget")) throw expected; }
                assertState(sentinels, samplers);
            }
            try (var pipeline = new ShaderPipeline(allBuffers())) {
                java.nio.ByteBuffer input = MemoryUtil.memCalloc(8 * 5 * 4), output = MemoryUtil.memAlloc(8 * 5 * 4);
                try {
                    upload(source, sentinels[0], 8, 5, input);
                    pipeline.render(source, 8, 5); assertState(sentinels, samplers);
                    download(source, sentinels[0], output);
                    for (int index = 0; index < output.capacity(); index++) {
                        int expected = index % 4 == 3 ? 255 : 135;
                        if (Math.abs(expected - (output.get(index) & 255)) > 1) throw new AssertionError("Eight-output/sixteen-sampler routing differs");
                    }
                } finally { MemoryUtil.memFree(input); MemoryUtil.memFree(output); }
            }
            if (GL33C.glGetError() != GL33C.GL_NO_ERROR) throw new AssertionError("MRT checks generated a GL error");
        } finally {
            GL33C.glDeleteTextures(source);
            for (int unit = 0; unit < sentinels.length; unit++) {
                GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + unit);
                GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, oldTextures[unit]); GL33C.glBindSampler(unit, oldSamplers[unit]);
                GL33C.glDeleteTextures(sentinels[unit]); GL33C.glDeleteSamplers(samplers[unit]);
            }
            GL33C.glActiveTexture(active);
            for (int slot = 0; slot < 8; slot++) {
                if (oldBlend[slot]) GL33C.glEnablei(GL33C.GL_BLEND, slot); else GL33C.glDisablei(GL33C.GL_BLEND, slot);
                boolean[] mask = oldMasks[slot]; GL33C.glColorMaski(slot, mask[0], mask[1], mask[2], mask[3]);
            }
            for (int i = 0; i < stores.length; i++) GL33C.glPixelStorei(stores[i], oldStores[i]);
            GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, unpack); GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, pack);
        }
        System.out.println("Kernel MRT checks passed: legacy/modern routing, aliases, preserved buffers, feedback, default clears, eight outputs, sixteen samplers, resize, bounded allocation and indexed GL state.");
    }

    private static PreparedShaderPack routed(int style, boolean finalPass) throws Exception {
        var files = new LinkedHashMap<String, String>();
        boolean modern = style != 0;
        String outputs = style == 2 ? "out vec4 outColor0; out vec4 outColor1;" : modern ? "layout(location=0) out vec4 a; layout(location=1) out vec4 b;" : "";
        String first = style == 2 ? "outColor0" : modern ? "a" : "gl_FragData[0]", second = style == 2 ? "outColor1" : modern ? "b" : "gl_FragData[1]";
        files.put("composite.fsh", "#version " + (modern ? "330 core" : "120") + "\n/* RENDERTARGETS: 3,7 */\n"
            + (modern ? "in" : "varying") + " vec2 texcoord; uniform sampler2D colortex0; " + outputs
            + "void main(){vec4 c=texture2D(colortex0,texcoord); " + first + "=vec4(1.0-c.rgb,c.a); " + second + "=c;}");
        files.put("composite1.fsh", "#version 330 core\n/* DRAWBUFFERS:0 */\nin vec2 texcoord; uniform sampler2D composite; uniform sampler2D gaux4; out vec4 outColor0;"
            + "void main(){outColor0=vec4(texture(composite,texcoord).r,texture(gaux4,texcoord).g,0.5,1);}");
        files.put("composite2.fsh", "#version 120\n/* RENDERTARGETS:7 */\nvarying vec2 texcoord; uniform sampler2D colortex7;"
            + "void main(){gl_FragData[0]=vec4(1.0-texture2D(colortex7,texcoord).rgb,1);}");
        if (finalPass) files.put("final.fsh", "#version 120\n/* RENDERTARGETS:15 */\nvarying vec2 texcoord; uniform sampler2D colortex0; uniform sampler2D colortex7;"
            + "void main(){vec4 c=texture2D(colortex0,texcoord);gl_FragColor=vec4(c.r,texture2D(colortex7,texcoord).g,c.b,1);}");
        return pack(files);
    }
    private static PreparedShaderPack clearing() throws Exception {
        return pack(Map.of("composite.fsh", "#version 120\n/* RENDERTARGETS:15 */\nvarying vec2 texcoord; uniform sampler2D colortex15;"
            + "void main(){gl_FragColor=texture2D(colortex15,texcoord)+vec4(0.25);}",
            "final.fsh", "#version 120\nvarying vec2 texcoord; uniform sampler2D colortex1; uniform sampler2D colortex15;"
            + "void main(){gl_FragColor=vec4(texture2D(colortex1,texcoord).r,texture2D(colortex15,texcoord).g,0,1);}"));
    }
    private static PreparedShaderPack allBuffers() throws Exception {
        var files = new LinkedHashMap<String, String>();
        for (int parity = 0; parity < 2; parity++) {
            var indices = new StringJoiner(",");
            var body = new StringBuilder("void main(){");
            for (int slot = 0; slot < 8; slot++) {
                int buffer = slot * 2 + parity;
                indices.add(Integer.toString(buffer));
                body.append("gl_FragData[").append(slot).append("]=vec4(vec3(").append(buffer + 1).append(".0/16.0),1);");
            }
            files.put(parity == 0 ? "composite.fsh" : "composite1.fsh", "#version 120\n/* RENDERTARGETS:" + indices + " */\n" + body + "}");
        }
        var declarations = new StringBuilder("#version 120\nvarying vec2 texcoord;\n");
        var body = new StringBuilder("void main(){vec3 sum=vec3(0);\n");
        for (int buffer = 0; buffer < 16; buffer++) {
            declarations.append("uniform sampler2D colortex").append(buffer).append(";\n");
            body.append("sum+=texture2D(colortex").append(buffer).append(",texcoord).rgb/16.0;\n");
        }
        files.put("final.fsh", declarations.toString() + body + "gl_FragColor=vec4(sum,1);}");
        return pack(files);
    }
    private static PreparedShaderPack pack(Map<String, String> files) throws Exception {
        Path path = Files.createTempFile("kernel-mrt-test-", ".zip");
        try {
            try (var out = new ZipOutputStream(Files.newOutputStream(path))) {
                for (var entry : files.entrySet()) {
                    out.putNextEntry(new ZipEntry("shaders/" + entry.getKey())); out.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); out.closeEntry();
                }
            }
            return PreparedShaderPack.read(path);
        } finally { Files.deleteIfExists(path); }
    }
    private static void upload(int source, int binding, int width, int height, java.nio.ByteBuffer input) {
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, source);
        GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, width, height, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, input);
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, binding); GL33C.glActiveTexture(GL33C.GL_TEXTURE3);
    }
    private static void download(int source, int binding, java.nio.ByteBuffer output) {
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0); GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, source);
        GL33C.glGetTexImage(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, output);
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, binding); GL33C.glActiveTexture(GL33C.GL_TEXTURE3);
    }
    private static void assertState(int[] textures, int[] samplers) {
        if (GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE) != GL33C.GL_TEXTURE3) throw new AssertionError("Active texture changed");
        for (int unit = 0; unit < textures.length; unit++) {
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0 + unit);
            if (GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D) != textures[unit] || GL33C.glGetInteger(GL33C.GL_SAMPLER_BINDING) != samplers[unit])
                throw new AssertionError("Texture/sampler binding changed on unit " + unit);
        }
        GL33C.glActiveTexture(GL33C.GL_TEXTURE3);
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            for (int slot = 0; slot < 8; slot++) {
                GL33C.glGetBooleani_v(GL33C.GL_COLOR_WRITEMASK, slot, mask);
                if (!GL33C.glIsEnabledi(GL33C.GL_BLEND, slot) || mask.get(0) != 0 || (mask.get(1) != 0) != (slot % 2 == 0) || mask.get(2) != 0 || mask.get(3) != 0)
                    throw new AssertionError("Indexed blend/color-mask changed at output " + slot);
            }
        }
    }
}
