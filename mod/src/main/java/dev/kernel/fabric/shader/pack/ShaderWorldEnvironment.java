package dev.kernel.fabric.shader.pack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shader environment one Minecraft core program runs in, read from that program's own source.
 *
 * <p>Minecraft's core shaders change shape across the supported targets: 1.21.5 declares its matrices
 * in the program that uses them, later targets move them behind an import, and newer ones reach shared
 * state through uniform blocks and rebuild vertex positions from chunk-relative coordinates. Rather than
 * carry a table of those differences, Kernel reads the declarations out of the program it is replacing.
 * A version Kernel has never seen is then handled correctly as long as it keeps the same declaration
 * syntax, and a version that changes that syntax is reported as unusable rather than guessed at.
 *
 * <p>Nothing here is copied into a Kernel artifact. The parsed declarations are used to re-emit a
 * prelude for the pack's own program at runtime, from the game's own installed files.
 */
public record ShaderWorldEnvironment(String version, List<String> imports, Map<String, String> attributes,
                                     Map<String, String> uniforms, String position, String fragmentOutput) {
    private static final int MAX_SOURCE = 256 * 1024;
    private static final Pattern VERSION = Pattern.compile("(?m)^[ \\t]*#version[ \\t]+([0-9]+)(?:[ \\t]+\\w+)?[ \\t]*$");
    private static final Pattern IMPORT = Pattern.compile("(?m)^[ \\t]*#moj_import[ \\t]+<([^>]+)>[ \\t]*$");
    private static final Pattern ATTRIBUTE = Pattern.compile("(?m)^[ \\t]*in[ \\t]+([A-Za-z_][A-Za-z0-9_]*)[ \\t]+([A-Za-z_][A-Za-z0-9_]*)[ \\t]*;");
    private static final Pattern UNIFORM = Pattern.compile("(?m)^[ \\t]*uniform[ \\t]+([A-Za-z_][A-Za-z0-9_]*)[ \\t]+([A-Za-z_][A-Za-z0-9_]*)[ \\t]*;");
    private static final Pattern OUTPUT = Pattern.compile("(?m)^[ \\t]*out[ \\t]+vec4[ \\t]+([A-Za-z_][A-Za-z0-9_]*)[ \\t]*;");
    /** Minecraft's own world-space vertex position, whatever it is built from on this version. */
    private static final Pattern POSITION = Pattern.compile("(?m)^[ \\t]*vec3[ \\t]+pos[ \\t]*=[ \\t]*(.+?)[ \\t]*;[ \\t]*$");
    /** The names a substituted world program needs in order to transform a vertex at all. */
    private static final List<String> REQUIRED = List.of("ProjMat", "ModelViewMat");

    public ShaderWorldEnvironment {
        imports = List.copyOf(imports);
        attributes = Map.copyOf(attributes);
        uniforms = Map.copyOf(uniforms);
    }

    /**
     * Reads the environment out of one core program, or returns null when Kernel cannot describe it.
     *
     * <p>A null result means the substituted program would have to be built on assumptions, so the
     * caller keeps Minecraft's own rendering for that draw.
     */
    public static ShaderWorldEnvironment parse(String source, boolean vertex) {
        if (source == null || source.isEmpty() || source.length() > MAX_SOURCE) return null;
        String code = ShaderLexical.maskComments(source, null);
        var version = VERSION.matcher(code);
        if (!version.find()) return null;
        var imports = new ArrayList<String>();
        for (Matcher matcher = IMPORT.matcher(code); matcher.find(); ) imports.add(matcher.group(1));
        var attributes = declarations(ATTRIBUTE, code);
        var uniforms = declarations(UNIFORM, code);
        // Only the vertex stage transforms anything, so only it needs the matrices. They reach the
        // program through an import on most targets, so look in the whole source rather than only at the
        // declarations this program makes for itself.
        if (vertex) for (String required : REQUIRED)
            if (!uniforms.containsKey(required) && !Pattern.compile("\\b" + required + "\\b").matcher(code).find()) return null;
        String fragmentOutput = null;
        if (!vertex) {
            var output = OUTPUT.matcher(code);
            if (!output.find()) return null;
            fragmentOutput = output.group(1);
        }
        String position = "Position";
        if (vertex) {
            if (!attributes.containsKey("Position")) return null;
            var found = POSITION.matcher(code);
            // Reuse Minecraft's own expression, so chunk-relative rebasing stays whatever it is here.
            if (found.find()) position = found.group(1);
            if (position.contains("(") != position.contains(")")) return null;
        }
        return new ShaderWorldEnvironment(version.group(1), imports, attributes, uniforms, position, fragmentOutput);
    }

    private static Map<String, String> declarations(Pattern pattern, String code) {
        var found = new LinkedHashMap<String, String>();
        for (Matcher matcher = pattern.matcher(code); matcher.find(); ) found.putIfAbsent(matcher.group(2), matcher.group(1));
        return found;
    }

    public boolean hasAttribute(String name) { return attributes.containsKey(name); }

    /** Re-emits the version, imports and declarations a substituted program compiles against. */
    public String prelude() {
        var prelude = new StringBuilder("#version ").append(version).append('\n');
        for (String name : imports) prelude.append("#moj_import <").append(name).append(">\n");
        for (var attribute : attributes.entrySet())
            prelude.append("in ").append(attribute.getValue()).append(' ').append(attribute.getKey()).append(";\n");
        for (var uniform : uniforms.entrySet())
            prelude.append("uniform ").append(uniform.getValue()).append(' ').append(uniform.getKey()).append(";\n");
        return prelude.toString();
    }
}
