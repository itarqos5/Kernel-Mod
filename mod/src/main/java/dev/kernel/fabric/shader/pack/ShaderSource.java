package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.util.regex.Pattern;

/** Small legacy fullscreen adapter. World vertex programs require a different renderer and are rejected. */
public final class ShaderSource {
    private static final Pattern VERSION = Pattern.compile("(?m)^[ \\t]*#version[ \\t]+([0-9]+)(?:[ \\t]+(?:core|compatibility))?[ \\t]*$");
    public static final String DEFAULT_VERTEX = """
        #version 330 core
        out vec2 texcoord;
        void main() {
            vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            texcoord = p;
            gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
        }
        """;
    private ShaderSource() {}

    public static String translate(String source, boolean vertex) throws IOException {
        if (Pattern.compile("\\b(?:gl_FragDepth|discard|MC_[A-Z0-9_]+)\\b").matcher(source).find())
            throw new IOException("This shader requires unsupported depth/discard behavior or Minecraft shader macros");
        var matcher = VERSION.matcher(source);
        int version = 120;
        if (matcher.find()) { version = Integer.parseInt(matcher.group(1)); source = matcher.replaceFirst(""); }
        if (Pattern.compile("(?m)^\\s*#\\s*extension\\b").matcher(source).find()) {
            throw new IOException("Shader extensions are not supported by the current fullscreen adapter");
        }
        String header = "#version " + Math.max(330, version) + " core\n#define KERNEL 1\n";
        boolean legacyTexture = hasTextureSampler(source);
        if (legacyTexture) {
            if (Pattern.compile("\\bkernel_texture2D\\b").matcher(ShaderLexical.maskComments(source, null)).find())
                throw new IOException("Shader identifier conflicts with the legacy texture2D adapter");
            // Resolve the built-in before the pack declares its sampler named 'texture'.
            header += "vec4 kernel_texture2D(sampler2D s, vec2 p) { return texture(s, p); }\n";
            if (!vertex) header += "vec4 kernel_texture2D(sampler2D s, vec2 p, float bias) { return texture(s, p, bias); }\n";
        }
        source = token(source, "varying", vertex ? "out" : "in");
        source = token(source, "texture2D", legacyTexture ? "kernel_texture2D" : "texture");
        source = token(source, "texture2DLod", "textureLod");
        source = token(source, "texture2DGradARB", "textureGrad");
        if (vertex) {
            header += """
                #define kernel_Vertex vec4(vec2((gl_VertexID << 1) & 2, gl_VertexID & 2), 0.0, 1.0)
                #define kernel_Ortho mat4(2,0,0,0, 0,2,0,0, 0,0,1,0, -1,-1,0,1)
                """;
            source = source.replaceAll("\\bftransform\\s*\\(\\s*\\)", "(kernel_Ortho * kernel_Vertex)");
            source = source.replaceAll("\\bgl_TextureMatrix\\s*\\[\\s*0\\s*\\]", "mat4(1.0)");
            source = token(source, "gl_Vertex", "kernel_Vertex");
            source = token(source, "gl_MultiTexCoord0", "kernel_Vertex");
            source = token(source, "gl_ModelViewProjectionMatrix", "kernel_Ortho");
            source = token(source, "gl_ModelViewMatrix", "mat4(1.0)");
            source = token(source, "gl_ProjectionMatrix", "kernel_Ortho");
            source = token(source, "gl_Color", "vec4(1.0)");
        } else {
            var outputs = Pattern.compile("\\bgl_FragData\\s*\\[([^]]*)\\]").matcher(source);
            int used = Pattern.compile("\\bgl_FragColor\\b").matcher(source).find() ? 1 : 0;
            var replaced = new StringBuilder();
            while (outputs.find()) {
                String index = outputs.group(1).trim();
                if (!index.matches("[0-7]")) throw new IOException("Fragment outputs require literal indices from 0 to 7");
                int slot = Integer.parseInt(index); used |= 1 << slot;
                outputs.appendReplacement(replaced, "kernel_fragColor" + slot);
            }
            outputs.appendTail(replaced);
            source = token(replaced.toString(), "gl_FragColor", "kernel_fragColor0");
            for (int slot = 0; slot < 8; slot++) if ((used & (1 << slot)) != 0)
                header += "layout(location = " + slot + ") out vec4 kernel_fragColor" + slot + ";\n";
        }
        return header + "#line 1 0\n" + source;
    }
    private static boolean hasTextureSampler(String source) {
        String code = ShaderLexical.maskComments(source, null);
        var sampler = Pattern.compile("\\buniform\\s+(?:(?:lowp|mediump|highp)\\s+)?sampler2D\\s+").matcher(code);
        var name = Pattern.compile("\\btexture\\b");
        int offset = 0;
        while (sampler.find(offset)) {
            int end = sampler.end();
            while (end < code.length() && code.charAt(end) != ';' && code.charAt(end) != '{' && code.charAt(end) != '}') end++;
            if (end == code.length()) return false; // The driver diagnoses this unterminated declaration.
            if (code.charAt(end) == ';' && name.matcher(code.substring(sampler.end(), end)).find()) return true;
            offset = end + 1;
        }
        return false;
    }
    private static String token(String source, String from, String to) { return source.replaceAll("\\b" + from + "\\b", to); }
}
