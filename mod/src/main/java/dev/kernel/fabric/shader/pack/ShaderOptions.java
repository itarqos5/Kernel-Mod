package dev.kernel.fabric.shader.pack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pack options declared in shader source, in the Iris/OptiFine syntax.
 *
 * <p>Options are applied by rewriting the declaring line rather than by prepending defines, because a
 * pack ships some options already enabled: prepending cannot undo an existing {@code #define}. Every
 * rewrite replaces one physical line with exactly one physical line, so the {@code #line} directives
 * produced by include expansion keep pointing at the original source.
 *
 * <p>Nothing here evaluates preprocessor conditions. A declaration inside an inactive {@code #if} branch
 * is still listed and still rewritten; the GLSL preprocessor decides whether it matters.
 */
public final class ShaderOptions {
    public static final int MAX_OPTIONS = 1024;
    private static final int MAX_VALUES = 64;
    private static final int MAX_NAME = 64;
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern VALUE = Pattern.compile("[A-Za-z0-9_.+-]{1,32}");
    // A bare define the pack ships enabled, optionally followed by its own description comment.
    private static final Pattern BOOLEAN_ON = Pattern.compile("^([ \\t]*)#[ \\t]*define[ \\t]+([A-Za-z_][A-Za-z0-9_]*)[ \\t]*((?://.*)?)$");
    // The same declaration shipped disabled.
    private static final Pattern BOOLEAN_OFF = Pattern.compile("^([ \\t]*)//[ \\t]*#[ \\t]*define[ \\t]+([A-Za-z_][A-Za-z0-9_]*)[ \\t]*((?://.*)?)$");
    private static final Pattern VALUE_DEFINE = Pattern.compile("^([ \\t]*)#[ \\t]*define[ \\t]+([A-Za-z_][A-Za-z0-9_]*)[ \\t]+([^ \\t/]+)[ \\t]*(//[ \\t]*\\[([^\\]]*)\\].*)$");
    private static final Pattern VALUE_CONST = Pattern.compile("^([ \\t]*)const[ \\t]+(int|float)[ \\t]+([A-Za-z_][A-Za-z0-9_]*)[ \\t]*=[ \\t]*([^;/]+?)[ \\t]*;[ \\t]*(//[ \\t]*\\[([^\\]]*)\\].*)$");
    // Names Kernel or Minecraft own, and the buffer configuration read separately by ShaderBufferDirectives.
    private static final Pattern RESERVED = Pattern.compile("KERNEL|MC_[A-Z0-9_]*|gl_.*|(?:colortex[0-9]+|gcolor|gdepth|gnormal|composite|gaux[0-9]+)(?:Format|Clear|ClearColor|MipmapEnabled)");

    private ShaderOptions() {}

    /** One declaration site: the option it declares and how to rewrite its line for a chosen value. */
    private record Declaration(ShaderOption option, String prefix, String suffix) {
        String rewrite(String value) {
            if (option.kind() == ShaderOption.Kind.BOOLEAN)
                return prefix + (value.equals("true") ? "" : "//") + "#define " + option.name() + suffix;
            return prefix + value + suffix;
        }
    }

    /**
     * Lists the options declared in one expanded program source, in declaration order.
     *
     * <p>A name declared more than once keeps its first declaration. Conflicting later declarations are
     * ignored rather than rejected, because packs routinely redeclare the same option behind guards.
     */
    public static List<ShaderOption> discover(String source) {
        var found = new LinkedHashMap<String, ShaderOption>();
        scan(source, (line, declaration) -> found.putIfAbsent(declaration.option().name(), declaration.option()));
        return List.copyOf(found.values());
    }

    /**
     * Rewrites every declaration whose name has a configured value.
     *
     * <p>A stored value the pack does not declare is ignored and the pack default is kept, so an edited
     * or stale configuration file cannot produce source the pack never described.
     */
    public static String apply(String source, Map<String, String> values) {
        if (values.isEmpty()) return source;
        var rewrites = new LinkedHashMap<Integer, String>();
        scan(source, (line, declaration) -> {
            String value = values.get(declaration.option().name());
            if (value != null && declaration.option().accepts(value)) rewrites.putIfAbsent(line, declaration.rewrite(value));
        });
        if (rewrites.isEmpty()) return source;
        String[] lines = source.split("\n", -1);
        var edited = new StringBuilder(source.length());
        for (int line = 0; line < lines.length; line++) {
            if (line > 0) edited.append('\n');
            String replacement = rewrites.get(line);
            // Rewrites are built from the line without its carriage return, so restore it here.
            if (replacement != null && lines[line].endsWith("\r")) replacement += "\r";
            edited.append(replacement == null ? lines[line] : replacement);
        }
        return edited.toString();
    }

    @FunctionalInterface private interface Sink { void accept(int line, Declaration declaration); }

    private static void scan(String source, Sink sink) {
        String[] lines = source.split("\n", -1);
        boolean blockComment = false;
        int count = 0;
        for (int index = 0; index < lines.length && count < MAX_OPTIONS; index++) {
            String line = stripCarriageReturn(lines[index]);
            boolean startedInComment = blockComment;
            blockComment = advanceBlockComment(line, blockComment);
            // A declaration that begins inside a block comment is inert, so it is neither listed nor rewritten.
            if (startedInComment) continue;
            Declaration declaration = parse(line);
            if (declaration == null) continue;
            count++;
            sink.accept(index, declaration);
        }
    }

    private static String stripCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    /** Tracks only block comments; a line comment cannot continue onto the next line. */
    private static boolean advanceBlockComment(String line, boolean inComment) {
        for (int offset = 0; offset + 1 < line.length(); offset++) {
            if (inComment) {
                if (line.charAt(offset) == '*' && line.charAt(offset + 1) == '/') { inComment = false; offset++; }
            } else if (line.charAt(offset) == '/' && line.charAt(offset + 1) == '*') { inComment = true; offset++; }
            else if (line.charAt(offset) == '/' && line.charAt(offset + 1) == '/') return false;
        }
        return inComment;
    }

    private static Declaration parse(String line) {
        if (line.length() > 1024) return null;
        Matcher matcher = VALUE_DEFINE.matcher(line);
        if (matcher.matches()) {
            var values = values(matcher.group(5), matcher.group(3));
            return values == null || !usable(matcher.group(2)) ? null : new Declaration(
                option(matcher.group(2), ShaderOption.Kind.VALUE, matcher.group(3), values, comment(matcher.group(4))),
                matcher.group(1) + "#define " + matcher.group(2) + " ", " " + matcher.group(4));
        }
        matcher = VALUE_CONST.matcher(line);
        if (matcher.matches()) {
            var values = values(matcher.group(6), matcher.group(4));
            return values == null || !usable(matcher.group(3)) ? null : new Declaration(
                option(matcher.group(3), ShaderOption.Kind.VALUE, matcher.group(4), values, comment(matcher.group(5))),
                matcher.group(1) + "const " + matcher.group(2) + " " + matcher.group(3) + " = ", "; " + matcher.group(5));
        }
        matcher = BOOLEAN_OFF.matcher(line);
        boolean enabled = false;
        if (!matcher.matches()) { matcher = BOOLEAN_ON.matcher(line); enabled = true; }
        if (!matcher.matches() || !usable(matcher.group(2))) return null;
        String suffix = matcher.group(3).isEmpty() ? "" : " " + matcher.group(3);
        return new Declaration(option(matcher.group(2), ShaderOption.Kind.BOOLEAN, Boolean.toString(enabled),
            List.of("false", "true"), comment(matcher.group(3))), matcher.group(1), suffix);
    }

    private static ShaderOption option(String name, ShaderOption.Kind kind, String value, List<String> values, String comment) {
        return new ShaderOption(name, kind, value, values, comment, false);
    }

    private static boolean usable(String name) {
        return name.length() <= MAX_NAME && NAME.matcher(name).matches() && !RESERVED.matcher(name).matches();
    }

    /** Returns the declared values including the pack default, or null when the list is unusable. */
    private static List<String> values(String declared, String current) {
        var values = new ArrayList<String>();
        if (VALUE.matcher(current).matches()) values.add(current);
        for (String value : declared.trim().split("[ \\t]+")) {
            if (value.isEmpty()) continue;
            if (!VALUE.matcher(value).matches() || values.size() >= MAX_VALUES) return null;
            if (!values.contains(value)) values.add(value);
        }
        return values.size() < 2 || !values.contains(current) ? null : values;
    }

    private static String comment(String trailing) {
        String text = trailing.startsWith("//") ? trailing.substring(2) : trailing;
        int list = text.indexOf(']');
        if (text.trim().startsWith("[") && list >= 0) text = text.substring(list + 1);
        text = text.trim();
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
