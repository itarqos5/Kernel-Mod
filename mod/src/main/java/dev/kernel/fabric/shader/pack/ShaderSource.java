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
        var outputs = Pattern.compile("gl_FragData\\s*\\[([^]]*)\\]").matcher(source);
        while (outputs.find()) if (!outputs.group(1).trim().equals("0"))
            throw new IOException("This shader writes unsupported color outputs");
        String header = "#version " + Math.max(330, version) + " core\n#define KERNEL 1\n";
        source = token(source, "varying", vertex ? "out" : "in");
        source = token(source, "texture2D", "texture");
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
        } else if (Pattern.compile("\\bgl_FragColor\\b|\\bgl_FragData\\s*\\[").matcher(source).find()) {
            header += "layout(location = 0) out vec4 kernel_fragColor;\n";
            source = source.replaceAll("\\bgl_FragData\\s*\\[\\s*0\\s*\\]", "kernel_fragColor");
            source = token(source, "gl_FragColor", "kernel_fragColor");
        }
        return header + "#line 1 0\n" + source;
    }
    private static String token(String source, String from, String to) { return source.replaceAll("\\b" + from + "\\b", to); }
}
