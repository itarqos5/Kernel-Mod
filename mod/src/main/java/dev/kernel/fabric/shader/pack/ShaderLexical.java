package dev.kernel.fabric.shader.pack;

import java.util.List;

/** Linear comment masking with preserved offsets; it never backtracks over malformed source. */
final class ShaderLexical {
    private ShaderLexical() {}
    record Comment(int offset, String body) {}

    static String maskComments(String source, List<Comment> comments) {
        char[] code = source.toCharArray();
        for (int offset = 0; offset < source.length();) {
            char current = source.charAt(offset);
            if (current == '/' && offset + 1 < source.length()) {
                char next = source.charAt(offset + 1);
                if (next == '/') {
                    int end = source.indexOf('\n', offset + 2);
                    blank(code, offset, end < 0 ? source.length() : end);
                    offset = end < 0 ? source.length() : end;
                    continue;
                }
                if (next == '*') {
                    int end = source.indexOf("*/", offset + 2);
                    if (end < 0) { blank(code, offset, source.length()); break; } // The driver reports the malformed comment.
                    if (comments != null) comments.add(new Comment(offset, source.substring(offset + 2, end)));
                    blank(code, offset, end + 2);
                    offset = end + 2;
                    continue;
                }
            }
            if (current == '"' || current == '\'') {
                char quote = current;
                int start = offset;
                offset++;
                while (offset < source.length()) {
                    char value = source.charAt(offset++);
                    if (value == '\\' && offset < source.length()) offset++;
                    else if (value == quote || value == '\n') break;
                }
                blank(code, start, offset);
                continue;
            }
            offset++;
        }
        return new String(code);
    }

    private static void blank(char[] code, int from, int to) {
        for (int index = from; index < to; index++) if (code[index] != '\n') code[index] = ' ';
    }

}
