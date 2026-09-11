package dev.kernel.fabric.shader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public record ShaderConfig(String selected) {
    public ShaderConfig {
        if (selected == null || selected.contains("/") || selected.contains("\\") || selected.contains(":" )
            || selected.startsWith(".") || selected.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid shader pack filename");
    }
    public static ShaderConfig load(Path file) throws IOException {
        try { return new ShaderConfig(read(file).getProperty("selected", "")); }
        catch (IllegalArgumentException failure) { throw new IOException("Invalid shader configuration", failure); }
    }
    public void save(Path file) throws IOException {
        var properties = read(file); properties.setProperty("selected", selected);
        file = file.toAbsolutePath(); Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "kernel-shaders-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) { properties.store(writer, "Kernel shader selection; an empty selection disables shaders."); }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static Properties read(Path path) throws IOException {
        var result = new Properties();
        if (Files.notExists(path)) return result;
        try (var reader = Files.newBufferedReader(path)) { result.load(reader); }
        catch (IllegalArgumentException exception) { throw new IOException("Invalid shader configuration escapes", exception); }
        return result;
    }
}
