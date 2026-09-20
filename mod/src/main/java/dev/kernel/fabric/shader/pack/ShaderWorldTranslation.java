package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Rewrites one Iris world program so it compiles in Minecraft's own core shader environment.
 *
 * <p>This is the world counterpart to the fullscreen adapter in {@link ShaderSource}. Where that one
 * replaces the vertex position with a screen-filling triangle, this one binds the pack's legacy names to
 * the attributes, matrices and samplers the surrounding Minecraft program already provides, which
 * {@link ShaderWorldEnvironment} reads from that program rather than assuming.
 *
 * <p>The translation is deliberately narrow and fails rather than approximates. A program needing an
 * input Minecraft does not supply on this version, or more than one colour output, is rejected, and the
 * caller keeps Minecraft's own rendering for that draw. A pack is never rendered from a substitute Kernel
 * had to invent.
 */
public final class ShaderWorldTranslation {
    /** Writes past the first colour buffer, which a Minecraft pipeline has nowhere to put. */
    private static final Pattern MULTI_OUTPUT = Pattern.compile("\\bgl_FragData\\s*\\[\\s*[1-7]\\s*\\]");
    /** Stages Kernel does not run, so their samplers would read images that were never rendered. */
    private static final Pattern UNSUPPORTED_SAMPLER = Pattern.compile("\\b(?:shadowtex[01]|shadowcolor[01]|shadow|watershadow|depthtex[12]|colortex(?:[1-9]|1[0-5]))\\b");

    private ShaderWorldTranslation() {}

    /**
     * Returns the substituted program source.
     *
     * @param packSource the pack's own expanded program, with its options already applied
     * @param environment the environment read from the Minecraft program being replaced
     * @param vertex whether this is the vertex stage
     * @throws IOException when the program needs something this substitution cannot supply
     */
    public static String translate(String packSource, ShaderWorldEnvironment environment, boolean vertex) throws IOException {
        if (environment == null) throw new IOException("Kernel cannot describe this version's shader environment");
        String code = ShaderLexical.maskComments(packSource, null);
        var missing = ShaderWorldAttributes.unsupportedIn(code);
        if (!missing.isEmpty())
            throw new IOException("This program reads vertex attributes Kernel does not write: " + ShaderWorldAttributes.names(missing));
        if (UNSUPPORTED_SAMPLER.matcher(code).find())
            throw new IOException("This program samples a stage Kernel does not render yet");
        if (!vertex && MULTI_OUTPUT.matcher(code).find())
            throw new IOException("This program writes several colour buffers, which needs a Kernel-owned pipeline");
        if (Pattern.compile("(?m)^\\s*#\\s*extension\\b").matcher(code).find())
            throw new IOException("Shader extensions are not supported by the world adapter");

        var header = new StringBuilder(environment.prelude());
        header.append("#define KERNEL 1\n");
        String source = packSource;
        // Strip the pack's own version directive; the surrounding environment owns it.
        source = source.replaceAll("(?m)^[ \\t]*#version[ \\t]+[0-9]+(?:[ \\t]+\\w+)?[ \\t]*$", "");
        source = token(source, "varying", vertex ? "out" : "in");
        // A pack declares the extended attributes in the legacy spelling, which the versions Kernel
        // substitutes on no longer accept. Only in the vertex stage: nothing else has attributes.
        if (vertex) source = token(source, "attribute", "in");
        // Rename the pack's samplers before texture2D becomes the builtin texture(). A pack sampler is
        // commonly called "texture", and renaming it afterwards would rewrite the builtin calls too.
        source = sampler(source, code, environment, "texture", "Sampler0");
        source = sampler(source, code, environment, "gtexture", "Sampler0");
        source = sampler(source, code, environment, "gcolor", "Sampler0");
        source = sampler(source, code, environment, "lightmap", "Sampler2");
        source = token(source, "texture2D", "texture");
        source = token(source, "texture2DLod", "textureLod");

        if (vertex) {
            String position = "vec4(" + environment.position() + ", 1.0)";
            header.append("#define kernel_Vertex ").append(position).append('\n');
            source = source.replaceAll("\\bftransform\\s*\\(\\s*\\)", "(ProjMat * ModelViewMat * kernel_Vertex)");
            source = token(source, "gl_Vertex", "kernel_Vertex");
            source = token(source, "gl_ModelViewProjectionMatrix", "(ProjMat * ModelViewMat)");
            source = token(source, "gl_ModelViewMatrix", "ModelViewMat");
            source = token(source, "gl_ProjectionMatrix", "ProjMat");
            source = token(source, "gl_NormalMatrix", "mat3(ModelViewMat)");
            source = source.replaceAll("\\bgl_TextureMatrix\\s*\\[\\s*[0-9]+\\s*\\]", "mat4(1.0)");
            source = bind(source, code, "gl_Color", environment.hasAttribute("Color") ? "Color" : "vec4(1.0)");
            source = bind(source, code, "gl_MultiTexCoord0", environment.hasAttribute("UV0") ? "vec4(UV0, 0.0, 1.0)" : "vec4(0.0, 0.0, 0.0, 1.0)");
            source = bind(source, code, "gl_MultiTexCoord1", lightmap(environment));
            source = bind(source, code, "gl_MultiTexCoord2", lightmap(environment));
            // A pack that shades by normal cannot be faked when Minecraft stopped supplying one.
            if (Pattern.compile("\\bgl_Normal\\b").matcher(code).find()) {
                if (!environment.hasAttribute("Normal"))
                    throw new IOException("This program needs vertex normals, which this version does not supply");
                source = token(source, "gl_Normal", "Normal");
            }
        } else {
            header.append("out vec4 ").append(environment.fragmentOutput()).append(";\n");
            source = source.replaceAll("\\bgl_FragData\\s*\\[\\s*0\\s*\\]", environment.fragmentOutput());
            source = token(source, "gl_FragColor", environment.fragmentOutput());
        }
        return header + "#line 1 0\n" + source;
    }

    /** Minecraft packs both light coordinates into one integer attribute. */
    private static String lightmap(ShaderWorldEnvironment environment) {
        return environment.hasAttribute("UV2") ? "vec4(vec2(UV2) / 256.0, 0.0, 1.0)" : "vec4(1.0, 1.0, 0.0, 1.0)";
    }

    private static String bind(String source, String code, String name, String replacement) {
        return Pattern.compile("\\b" + name + "\\b").matcher(code).find() ? token(source, name, replacement) : source;
    }

    /**
     * Renames a pack sampler to the Minecraft binding, removing the pack's own declaration of it.
     *
     * <p>The declaration has to go: the surrounding environment already declares the binding, and a
     * second declaration of the same name does not compile.
     */
    private static String sampler(String source, String code, ShaderWorldEnvironment environment, String packName, String binding) {
        var declared = Pattern.compile("(?m)^[ \\t]*uniform[ \\t]+sampler2D[ \\t]+" + packName + "[ \\t]*;[ \\t]*$");
        if (!declared.matcher(code).find()) return source;
        if (!environment.uniforms().containsKey(binding)) return source;
        return token(declared.matcher(source).replaceAll(""), packName, binding);
    }

    /** Replaces one whole identifier. Only identifier-shaped names may be passed here. */
    private static String token(String source, String from, String to) {
        return source.replaceAll("\\b" + from + "\\b", java.util.regex.Matcher.quoteReplacement(to));
    }
}
