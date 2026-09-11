package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

/** Reads only literal, unconditional declarations; unsupported configuration is never guessed. */
public final class ShaderBufferDirectives {
    private static final String NAME = "(?:colortex[0-9]+|gcolor|gdepth|gnormal|composite|gaux[0-9]+)";
    private static final Pattern CANDIDATE = Pattern.compile("\\bconst\\s+\\w+\\s+" + NAME + "(?:Format|ClearColor|Clear|MipmapEnabled)\\b");
    private static final Pattern DECLARATION = Pattern.compile("^\\s*const\\s+(\\w+)\\s+(" + NAME + ")(Format|ClearColor|Clear|MipmapEnabled)\\s*=\\s*(.*?)\\s*;\\s*$");
    private static final Pattern CONDITIONAL = Pattern.compile("^\\s*#\\s*(if|ifdef|ifndef|endif)\\b");
    private static final Pattern FLOAT = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[fF]?");
    private static final Pattern VECTOR = Pattern.compile("vec4\\s*\\((.*)\\)");
    private final ShaderColorFormat[] formats = new ShaderColorFormat[16];
    private final Boolean[] clears = new Boolean[16];
    private final ShaderBufferSettings.Color[] colors = new ShaderBufferSettings.Color[16];

    public void read(String source, String program) throws IOException {
        read(source, program, false);
    }

    /** Returns the mipmap requests belonging to this program stage only. */
    public int read(String source, String program, boolean fragment) throws IOException {
        Boolean[] mipmaps = new Boolean[16];
        var comments = new ArrayList<ShaderLexical.Comment>();
        String code = ShaderLexical.maskComments(source, comments);
        char[] declarations = code.toCharArray();
        for (var comment : comments) comment.body().getChars(0, comment.body().length(), declarations, comment.offset() + 2);
        String metadata = new String(declarations);
        int depth = 0;
        for (int offset = 0; offset < source.length();) {
            int end = source.indexOf('\n', offset);
            if (end < 0) end = source.length();
            var conditional = CONDITIONAL.matcher(code.substring(offset, end));
            if (conditional.find()) {
                if (conditional.group(1).equals("endif")) depth = Math.max(0, depth - 1); else depth++;
            }
            String line = metadata.substring(offset, end);
            if (CANDIDATE.matcher(line).find()) {
                var declaration = DECLARATION.matcher(line);
                if (!declaration.matches()) throw failure(program, "Buffer declarations must occupy their own line");
                if (depth != 0) throw failure(program, "Conditional buffer configuration is not supported yet");
                int buffer = buffer(declaration.group(2), program);
                String type = declaration.group(1), kind = declaration.group(3), value = declaration.group(4);
                switch (kind) {
                    case "Format" -> {
                        requireType(type, "int", program);
                        ShaderColorFormat format;
                        try { format = ShaderColorFormat.valueOf(value.equals("RGBA") ? "RGBA8" : value); }
                        catch (IllegalArgumentException unsupported) { throw failure(program, "Unsupported color format: " + value); }
                        if (formats[buffer] != null && formats[buffer] != format) throw failure(program, "Conflicting format for color buffer " + buffer);
                        formats[buffer] = format;
                    }
                    case "Clear" -> {
                        requireType(type, "bool", program);
                        boolean clear = bool(value, program);
                        if (buffer == 0 && !clear) throw failure(program, "Retaining the native world-color buffer requires terrain-stage integration");
                        if (clears[buffer] != null && clears[buffer] != clear) throw failure(program, "Conflicting clear mode for color buffer " + buffer);
                        clears[buffer] = clear;
                    }
                    case "ClearColor" -> {
                        requireType(type, "vec4", program);
                        if (buffer == 0) throw failure(program, "Changing the native world clear color requires terrain-stage integration");
                        var color = color(value, program);
                        if (colors[buffer] != null && !colors[buffer].equals(color)) throw failure(program, "Conflicting clear color for color buffer " + buffer);
                        colors[buffer] = color;
                    }
                    case "MipmapEnabled" -> {
                        requireType(type, "bool", program);
                        boolean enabled = bool(value, program);
                        if (enabled && !fragment) throw failure(program, "Color mipmaps must be requested from a fragment program");
                        if (mipmaps[buffer] != null && mipmaps[buffer] != enabled) throw failure(program, "Conflicting mipmap mode for color buffer " + buffer);
                        mipmaps[buffer] = enabled;
                    }
                    default -> throw new AssertionError(kind);
                }
            }
            offset = end + 1;
        }
        int mask = 0;
        for (int buffer = 0; buffer < 16; buffer++) if (Boolean.TRUE.equals(mipmaps[buffer])) mask |= 1 << buffer;
        return mask;
    }

    public ShaderBufferSettings build() {
        var defaults = ShaderBufferSettings.defaults();
        var result = new ArrayList<ShaderBufferSettings.Buffer>(16);
        for (int index = 0; index < 16; index++) {
            var fallback = defaults.buffers().get(index);
            result.add(new ShaderBufferSettings.Buffer(formats[index] == null ? fallback.format() : formats[index], formats[index] != null,
                clears[index] == null ? fallback.clear() : clears[index], colors[index] == null ? fallback.color() : colors[index]));
        }
        return new ShaderBufferSettings(result);
    }

    private static int buffer(String name, String program) throws IOException {
        int index = switch (name) {
            case "gcolor" -> 0; case "gdepth" -> 1; case "gnormal" -> 2; case "composite" -> 3;
            case "gaux1" -> 4; case "gaux2" -> 5; case "gaux3" -> 6; case "gaux4" -> 7;
            default -> {
                if (!name.matches("colortex(?:[0-9]|1[0-5])")) throw failure(program, "Unknown color buffer: " + name);
                yield Integer.parseInt(name.substring(8));
            }
        };
        return index;
    }
    private static boolean bool(String value, String program) throws IOException {
        if (!value.equals("true") && !value.equals("false")) throw failure(program, "Buffer booleans require literal true or false");
        return value.equals("true");
    }
    private static ShaderBufferSettings.Color color(String value, String program) throws IOException {
        var vector = VECTOR.matcher(value);
        if (!vector.matches()) throw failure(program, "Clear colors require vec4 with four literal numbers");
        String[] components = vector.group(1).split(",", -1);
        if (components.length != 4) throw failure(program, "Clear colors require four components");
        float[] numbers = new float[4];
        for (int index = 0; index < 4; index++) {
            String component = components[index].trim();
            if (!FLOAT.matcher(component).matches()) throw failure(program, "Clear colors require literal numbers");
            try { numbers[index] = Float.parseFloat(component); }
            catch (NumberFormatException invalid) { throw failure(program, "Invalid clear color number"); }
            if (!Float.isFinite(numbers[index])) throw failure(program, "Clear colors must be finite");
        }
        return new ShaderBufferSettings.Color(numbers[0], numbers[1], numbers[2], numbers[3]);
    }
    private static void requireType(String actual, String expected, String program) throws IOException {
        if (!actual.equals(expected)) throw failure(program, "Expected " + expected + " buffer declaration");
    }
    private static IOException failure(String program, String text) { return new IOException(program + ": " + text); }
}
