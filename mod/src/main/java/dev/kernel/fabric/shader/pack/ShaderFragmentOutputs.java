package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Restricts output declarations to names the GL 3.3 linker can query without guessing bindings. */
public final class ShaderFragmentOutputs {
    private static final Pattern TOKENS = Pattern.compile("#[^\\n]*|[A-Za-z_]\\w*|[{}();]");
    private static final Pattern MACRO = Pattern.compile("#\\s*define\\s+(\\w+)\\b(.*)");
    private static final Pattern CONDITIONAL = Pattern.compile("#\\s*(if|ifdef|ifndef|elif|else|endif)\\b");
    private static final Pattern MACRO_OUTPUT = Pattern.compile("\\bout\\b|##");
    private static final Pattern DECLARATION = Pattern.compile("\\s*(?:(?:lowp|mediump|highp)\\s+)?vec4\\s+(\\w+)\\s*;");

    private ShaderFragmentOutputs() {}

    public static List<String> read(String source) throws IOException {
        String code = ShaderLexical.maskComments(source, null);
        var names = new ArrayList<String>();
        var macros = new HashSet<String>();
        var conditions = new java.util.ArrayDeque<Long>();
        var tokens = TOKENS.matcher(code);
        int braces = 0, parentheses = 0;
        while (tokens.find()) {
            String token = tokens.group();
            if (token.startsWith("#")) {
                var condition = CONDITIONAL.matcher(token);
                long scope = ((long) braces << 32) | (parentheses & 0xffffffffL);
                if (condition.find()) {
                    String kind = condition.group(1);
                    if (kind.equals("if") || kind.equals("ifdef") || kind.equals("ifndef")) conditions.push(scope);
                    else if (!conditions.isEmpty()) {
                        if (conditions.peek() != scope) throw new IOException("Conditional shader branches must balance their braces and parentheses");
                        if (kind.equals("endif")) conditions.pop();
                    }
                }
                var macro = MACRO.matcher(token);
                if (macro.matches()) {
                    macros.add(macro.group(1));
                    int macroBraces = 0, macroParentheses = 0;
                    for (char value : macro.group(2).toCharArray()) {
                        if (value == '{') macroBraces++; else if (value == '}') macroBraces--;
                        if (value == '(') macroParentheses++; else if (value == ')') macroParentheses--;
                    }
                    if (macroBraces != 0 || macroParentheses != 0) throw new IOException("Shader macros must balance their braces and parentheses");
                    if (MACRO_OUTPUT.matcher(macro.group(2)).find())
                        throw new IOException("Macro-generated fragment outputs are not supported yet");
                }
                continue;
            }
            switch (token) {
                case "{" -> braces++;
                case "}" -> braces--;
                case "(" -> parentheses++;
                case ")" -> parentheses--;
                case "out" -> {
                    if (braces != 0 || parentheses != 0) continue; // Function parameters are not framebuffer outputs.
                    int end = code.indexOf(';', tokens.end());
                    var declaration = DECLARATION.matcher(code.substring(tokens.end(), end < 0 ? code.length() : end + 1));
                    if (!declaration.matches()) throw new IOException("Fragment outputs require separate vec4 declarations; arrays and interface blocks are not supported yet");
                    String name = declaration.group(1);
                    if (!names.contains(name)) names.add(name);
                }
                default -> { }
            }
        }
        if (names.stream().anyMatch(macros::contains)) throw new IOException("Macro-generated fragment output names are not supported yet");
        if (names.isEmpty()) throw new IOException("A fragment shader must declare vec4 color outputs");
        return List.copyOf(names);
    }
}
