package dev.kernel.fabric.world;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Restart-only world optimization preferences; unknown keys survive saves. */
public record WorldConfig(boolean biomeOffsets) {
    public static WorldConfig load(Path path) throws IOException {
        return new WorldConfig(Boolean.parseBoolean(read(path).getProperty("biome_offsets", "true")));
    }
    public void save(Path path) throws IOException {
        Path target = path.toAbsolutePath();
        Properties properties = read(target);
        properties.setProperty("biome_offsets", Boolean.toString(biomeOffsets));
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "kernel-world-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) { properties.store(writer, "Kernel world settings. Restart Minecraft after changing."); }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static Properties read(Path path) throws IOException {
        var properties = new Properties();
        if (Files.notExists(path)) return properties;
        try (var reader = Files.newBufferedReader(path)) { properties.load(reader); }
        catch (IllegalArgumentException exception) { throw new IOException("Invalid Kernel world property escapes", exception); }
        return properties;
    }
}
