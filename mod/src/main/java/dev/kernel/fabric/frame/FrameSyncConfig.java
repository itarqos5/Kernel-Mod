package dev.kernel.fabric.frame;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Runtime display preference, separate from restart-only renderer Mixin switches. */
public record FrameSyncConfig(boolean enabled) {
    public static FrameSyncConfig load(Path path) throws IOException {
        return new FrameSyncConfig(Boolean.parseBoolean(read(path).getProperty("frame_sync", "true")));
    }

    public void save(Path path) throws IOException {
        Path target = path.toAbsolutePath();
        Properties properties = read(target);
        properties.setProperty("frame_sync", Boolean.toString(enabled));
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "kernel-display-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) {
                properties.store(writer, "Kernel display settings. Frame Sync applies immediately.");
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static Properties read(Path path) throws IOException {
        var properties = new Properties();
        if (Files.notExists(path)) return properties;
        try (var reader = Files.newBufferedReader(path)) { properties.load(reader); }
        catch (IllegalArgumentException exception) { throw new IOException("Invalid Kernel display property escapes", exception); }
        return properties;
    }
}
