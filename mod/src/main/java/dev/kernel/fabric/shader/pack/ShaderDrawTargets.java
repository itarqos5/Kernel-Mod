package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Maps fragment-output locations to logical color buffers without evaluating shader conditions. */
public final class ShaderDrawTargets {
    public static final int BUFFER_COUNT = 16;
    public static final int OUTPUT_COUNT = 8;
    private static final List<Integer> DEFAULT = List.of(0, 1, 2, 3, 4, 5, 6, 7);
    private static final Pattern DIRECTIVE = Pattern.compile("(?s)^\\s*(DRAWBUFFERS|RENDERTARGETS)\\s*:(.*?)\\s*$");
    private static final Pattern CONDITIONAL = Pattern.compile("^\\s*#\\s*(if|ifdef|ifndef|endif)\\b");

    private ShaderDrawTargets() {}

    public static List<Integer> defaults(boolean finalPass) { return finalPass ? List.of(0) : DEFAULT; }

    public static List<Integer> read(String source, boolean finalPass) throws IOException {
        if (finalPass) return defaults(true);
        List<ShaderLexical.Comment> comments = new ArrayList<>();
        String code = ShaderLexical.maskComments(source, comments);
        List<Integer> result = null;
        int conditionalDepth = 0;
        int commentIndex = 0;
        for (int offset = 0; offset < source.length();) {
            int end = source.indexOf('\n', offset);
            if (end < 0) end = source.length();
            var conditional = CONDITIONAL.matcher(code.substring(offset, end));
            if (conditional.find()) {
                if (conditional.group(1).equals("endif")) conditionalDepth = Math.max(0, conditionalDepth - 1);
                else conditionalDepth++;
            }
            while (commentIndex < comments.size() && comments.get(commentIndex).offset() < end) {
                ShaderLexical.Comment comment = comments.get(commentIndex++);
                var directive = DIRECTIVE.matcher(comment.body());
                if (directive.matches()) {
                    if (!code.substring(offset, end).isBlank()) throw new IOException("Draw-buffer comments must appear on their own line");
                    if (conditionalDepth != 0) throw new IOException("Conditional draw-buffer declarations are not supported yet");
                    List<Integer> parsed = parse(directive.group(1), directive.group(2));
                    if (result != null && !result.equals(parsed)) throw new IOException("Conflicting shader draw-buffer declarations");
                    result = parsed;
                }
            }
            offset = end + 1;
        }
        return result == null ? defaults(false) : result;
    }

    private static List<Integer> parse(String type, String text) throws IOException {
        String contents = text.trim();
        var targets = new ArrayList<Integer>();
        if (type.equals("DRAWBUFFERS")) {
            if (!contents.matches("[0-9]{1,8}")) throw new IOException("DRAWBUFFERS must contain one to eight buffer digits");
            for (int index = 0; index < contents.length(); index++) targets.add(contents.charAt(index) - '0');
        } else {
            if (!contents.matches("[0-9]+(?:\\s*,\\s*[0-9]+){0,7}")) throw new IOException("RENDERTARGETS must list one to eight buffer indices");
            for (String value : contents.split(",")) {
                try { targets.add(Integer.parseInt(value.trim())); }
                catch (NumberFormatException failure) { throw new IOException("Shader buffer index is out of range", failure); }
            }
        }
        int mask = 0;
        for (int target : targets) {
            if (target < 0 || target >= BUFFER_COUNT) throw new IOException("Shader buffer index must be between 0 and 15");
            if ((mask & (1 << target)) != 0) throw new IOException("A shader pass cannot write the same color buffer twice");
            mask |= 1 << target;
        }
        return List.copyOf(targets);
    }
}
