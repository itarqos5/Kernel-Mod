package dev.kernel.fabric.resource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Resource-reload preferences, separate from the pre-Fabric bootstrap layer. */
public record ResourceConfig(boolean compactReaders) {
    public static ResourceConfig load(Path path) throws IOException {
        return new ResourceConfig(Boolean.parseBoolean(read(path).getProperty("compact_readers", "true")));
    }
    public void save(Path path) throws IOException {
        Path target = path.toAbsolutePath();
        Properties properties = read(target);
        properties.setProperty("compact_readers", Boolean.toString(compactReaders));
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "kernel-resources-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) { properties.store(writer, "Kernel resource settings. Restart Minecraft after changing."); }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static Properties read(Path path) throws IOException {
        Properties properties = new Properties();
        if (Files.notExists(path)) return properties;
        try (var reader = Files.newBufferedReader(path)) { properties.load(reader); }
        catch (IllegalArgumentException exception) { throw new IOException("Invalid Kernel resource property escapes", exception); }
        return properties;
    }
}
