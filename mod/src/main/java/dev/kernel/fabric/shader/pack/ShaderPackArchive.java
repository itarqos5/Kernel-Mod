package dev.kernel.fabric.shader.pack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Bounded archive access. Shader sources stay inside their pack; archives are never extracted. */
public final class ShaderPackArchive implements AutoCloseable {
    public static final long MAX_ARCHIVE_BYTES = 256L * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024;
    private static final int MAX_SOURCE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_EXPANDED_CHARS = 16 * 1024 * 1024;
    private static final Pattern PROGRAM = Pattern.compile("(?:world-?[0-9]+/)?[A-Za-z_][A-Za-z_0-9]*\\.(?:vsh|fsh|gsh|csh|tcs|tes)");
    private static final Pattern INCLUDE = Pattern.compile("^[ \\t]*#[ \\t]*include[ \\t]+[\"<]([^\">]+)[\">][ \\t]*(?://.*)?$");
    private final ZipFile zip;
    private final Map<String, ZipEntry> files;
    private final String root;

    public ShaderPackArchive(Path archive) throws IOException {
        if (!Files.isRegularFile(archive) || Files.size(archive) > MAX_ARCHIVE_BYTES) throw new IOException("Shader ZIP is missing or exceeds 256 MiB");
        zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8);
        try {
            var entries = new LinkedHashMap<String, ZipEntry>();
            var roots = new LinkedHashSet<String>();
            long total = 0; int count = 0;
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++count > 16384) throw new IOException("Shader ZIP contains too many entries");
                String name = normalize(entry.getName());
                if (entry.isDirectory()) continue;
                if (entries.putIfAbsent(name, entry) != null) throw new IOException("Duplicate shader ZIP path: " + name);
                if (entry.getSize() > MAX_UNCOMPRESSED_BYTES || entry.getSize() > MAX_UNCOMPRESSED_BYTES - total) throw new IOException("Shader ZIP expands beyond 512 MiB");
                if (entry.getSize() > 0) total += entry.getSize();
                int marker = name.startsWith("shaders/") ? 0 : name.indexOf("/shaders/") + 1;
                if (marker != 0 || name.startsWith("shaders/")) {
                    String candidate = name.substring(0, marker) + "shaders/";
                    if (PROGRAM.matcher(name.substring(candidate.length())).matches()) roots.add(candidate);
                }
            }
            if (roots.size() != 1) throw new IOException(roots.isEmpty() ? "No shaders directory containing shader programs was found" : "Shader ZIP contains several ambiguous shaders directories");
            files = Map.copyOf(entries); root = roots.iterator().next();
        } catch (Throwable failure) {
            try { zip.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    public Set<String> files() {
        var result = new TreeSet<String>();
        for (String name : files.keySet()) if (name.startsWith(root)) result.add(name.substring(root.length()));
        return java.util.Collections.unmodifiableSortedSet(result);
    }
    public boolean contains(String relativePath) throws IOException { return files.containsKey(root + normalize(relativePath)); }
    public String source(String relativePath) throws IOException {
        String name = root + normalize(relativePath);
        ZipEntry entry = files.get(name);
        if (entry == null) throw new IOException("Missing shader source: " + relativePath);
        if (entry.getSize() > MAX_SOURCE_BYTES) throw new IOException("Shader source exceeds 4 MiB: " + relativePath);
        byte[] bytes;
        try (var input = zip.getInputStream(entry)) { bytes = input.readNBytes(MAX_SOURCE_BYTES + 1); }
        if (bytes.length > MAX_SOURCE_BYTES) throw new IOException("Shader source exceeds 4 MiB: " + relativePath);
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
    public record Expanded(String source, Map<Integer, String> sourceFiles) {}
    public Expanded expand(String relativePath) throws IOException {
        var output = new StringBuilder(); var ids = new LinkedHashMap<String, Integer>();
        expand(normalize(relativePath), output, ids, new HashMap<>(), new ArrayDeque<>());
        var names = new LinkedHashMap<Integer, String>(); ids.forEach((name, id) -> names.put(id, name));
        return new Expanded(output.toString(), Map.copyOf(names));
    }
    private void expand(String path, StringBuilder output, Map<String, Integer> ids, Map<String, String> sources, ArrayDeque<String> stack) throws IOException {
        if (stack.size() >= 32 || stack.contains(path)) throw new IOException("Cyclic or excessively deep shader include: " + path);
        stack.addLast(path); int id = ids.computeIfAbsent(path, ignored -> ids.size());
        String text = sources.get(path);
        if (text == null) { text = source(path); sources.put(path, text); }
        String[] lines = text.split("\\R", -1); boolean blockComment = false;
        for (int line = 0; line < lines.length; line++) {
            String raw = lines[line];
            boolean beganInComment = blockComment;
            boolean quoted = false;
            var active = new StringBuilder();
            // Ignore directives inside comments while keeping the original shader text untouched.
            for (int offset = 0; offset < raw.length(); offset++) {
                if (blockComment) {
                    if (offset + 1 < raw.length() && raw.charAt(offset) == '*' && raw.charAt(offset + 1) == '/') { blockComment = false; offset++; }
                } else if (!quoted && offset + 1 < raw.length() && raw.charAt(offset) == '/' && raw.charAt(offset + 1) == '*') { blockComment = true; active.append(' '); offset++; }
                else if (!quoted && offset + 1 < raw.length() && raw.charAt(offset) == '/' && raw.charAt(offset + 1) == '/') break;
                else {
                    char character = raw.charAt(offset);
                    if (character == '"' && (offset == 0 || raw.charAt(offset - 1) != '\\')) quoted = !quoted;
                    active.append(character);
                }
            }
            var include = INCLUDE.matcher(active);
            if (include.matches()) {
                String target = include.group(1);
                int slash = path.lastIndexOf('/');
                String resolved = normalize(target.startsWith("/") ? target.substring(1) : (slash < 0 ? "" : path.substring(0, slash + 1)) + target);
                int includedId = ids.computeIfAbsent(resolved, ignored -> ids.size());
                if (beganInComment) output.append("*/\n");
                output.append("#line 1 ").append(includedId).append('\n');
                expand(resolved, output, ids, sources, stack);
                output.append("#line ").append(line + 2).append(' ').append(id);
                if (blockComment) output.append(" /*");
                output.append('\n');
            } else output.append(raw).append('\n');
            if (output.length() > MAX_EXPANDED_CHARS) throw new IOException("Expanded shader source exceeds 16 MiB");
        }
        stack.removeLast();
    }
    private static String normalize(String path) throws IOException {
        if (path.isEmpty() || path.startsWith("/") || path.indexOf('\\') >= 0 || path.indexOf(':') >= 0 || path.indexOf('\0') >= 0) throw new IOException("Invalid shader path: " + path);
        var parts = new ArrayDeque<String>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (parts.isEmpty()) throw new IOException("Shader path escapes its root: " + path);
                parts.removeLast();
            } else parts.addLast(part);
        }
        if (parts.isEmpty()) throw new IOException("Empty shader path");
        return String.join("/", parts);
    }
    @Override public void close() throws IOException { zip.close(); }
}
