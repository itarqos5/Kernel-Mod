package dev.kernel.fabric.shader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Per-pack option values, stored beside the shader selection.
 *
 * <p>Each pack keeps its own file so that reinstalling or switching packs cannot silently reinterpret
 * another pack values. Values are stored as the pack declared them, as plain text; Kernel validates
 * them against the pack declarations when it compiles, not when it reads the file, so a file written by
 * an older version of a pack stays readable and simply loses the entries that pack no longer declares.
 */
public record ShaderOptionConfig(Map<String, String> values) {
    private static final int MAX_ENTRIES = 1024;
    private static final int MAX_BYTES = 256 * 1024;
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern VALUE = Pattern.compile("[A-Za-z0-9_.+-]{1,32}");

    public ShaderOptionConfig { values = Map.copyOf(new TreeMap<>(values)); }

    public static ShaderOptionConfig empty() { return new ShaderOptionConfig(Map.of()); }

    /** Returns the file holding one pack options, or null when the pack name cannot own a file. */
    public static Path file(Path directory, String pack) {
        String name = pack.toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) name = name.substring(0, name.length() - 4);
        var sanitized = new StringBuilder();
        for (int index = 0; index < name.length() && sanitized.length() < 96; index++) {
            char value = name.charAt(index);
            sanitized.append(value >= 'a' && value <= 'z' || value >= '0' && value <= '9' || value == '-' || value == '_' ? value : '.');
        }
        String result = sanitized.toString();
        return result.isBlank() || result.chars().allMatch(value -> value == '.') ? null : directory.resolve(result + ".txt");
    }

    /** Reads stored values, treating an unreadable or malformed file as no stored values. */
    public static ShaderOptionConfig load(Path file) {
        if (file == null || Files.notExists(file)) return empty();
        var values = new LinkedHashMap<String, String>();
        try {
            if (Files.size(file) > MAX_BYTES) return empty();
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || values.size() >= MAX_ENTRIES) continue;
                int separator = trimmed.indexOf('=');
                if (separator <= 0) continue;
                String name = trimmed.substring(0, separator).strip();
                String value = trimmed.substring(separator + 1).strip();
                if (NAME.matcher(name).matches() && VALUE.matcher(value).matches()) values.putIfAbsent(name, value);
            }
        } catch (IOException | RuntimeException unreadable) { return empty(); }
        return new ShaderOptionConfig(values);
    }

    public void save(Path file) throws IOException {
        if (file == null) throw new IOException("This shader pack name cannot own an options file");
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "kernel-options-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) {
                writer.write("# Kernel shader pack options. Values Kernel does not recognise are ignored.\n");
                for (var entry : new TreeMap<>(values).entrySet()) {
                    if (!NAME.matcher(entry.getKey()).matches() || !VALUE.matcher(entry.getValue()).matches()) continue;
                    writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
                }
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    public ShaderOptionConfig with(String name, String value) {
        var updated = new LinkedHashMap<>(values);
        updated.put(name, value);
        return new ShaderOptionConfig(updated);
    }

    public ShaderOptionConfig without(String name) {
        if (!values.containsKey(name)) return this;
        var updated = new LinkedHashMap<>(values);
        updated.remove(name);
        return new ShaderOptionConfig(updated);
    }
}
