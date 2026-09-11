package dev.kernel.fabric.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class WorldConfigTest {
    @TempDir Path directory;
    @Test void preservesUnknownKeysAndDefaultsWithoutWriting() throws Exception {
        Path path = directory.resolve("world.properties");
        assertTrue(WorldConfig.load(path).biomeOffsets()); assertFalse(Files.exists(path));
        Files.writeString(path, "future_key=value\nbiome_offsets=maybe\n");
        assertFalse(WorldConfig.load(path).biomeOffsets());
        new WorldConfig(true).save(path);
        assertTrue(WorldConfig.load(path).biomeOffsets());
        assertTrue(Files.readString(path).contains("future_key=value"));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void malformedPropertiesArePreservedForRecovery() throws Exception {
        Path path = directory.resolve("world.properties");
        String invalid = "future_key=\\uBAD!\n";
        Files.writeString(path, invalid);
        assertThrows(java.io.IOException.class, () -> WorldConfig.load(path));
        assertThrows(java.io.IOException.class, () -> new WorldConfig(false).save(path));
        assertEquals(invalid, Files.readString(path));
    }
}
