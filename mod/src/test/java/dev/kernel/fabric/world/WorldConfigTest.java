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
        assertTrue(WorldConfig.load(path).noiseSlices());
        assertTrue(WorldConfig.load(path).endIslandHeights());
        assertTrue(WorldConfig.load(path).shapeTraversal());
        new WorldConfig(true, true, true, true).save(path);
        assertTrue(WorldConfig.load(path).biomeOffsets());
        assertTrue(Files.readString(path).contains("future_key=value"));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void malformedPropertiesArePreservedForRecovery() throws Exception {
        Path path = directory.resolve("world.properties");
        String invalid = "future_key=\\uBAD!\n";
        Files.writeString(path, invalid);
        assertThrows(java.io.IOException.class, () -> WorldConfig.load(path));
        assertThrows(java.io.IOException.class, () -> new WorldConfig(false, false, true, true).save(path));
        assertEquals(invalid, Files.readString(path));
    }
    @Test void worldFeaturesPersistIndependently() throws Exception {
        Path path = directory.resolve("world.properties");
        new WorldConfig(true, false, true, true).save(path);
        var loaded = WorldConfig.load(path);
        assertTrue(loaded.biomeOffsets()); assertFalse(loaded.noiseSlices());
        loaded.with(WorldFeature.BIOME_OFFSETS, false).save(path);
        assertEquals(new WorldConfig(false, false, true, true), WorldConfig.load(path));
        WorldConfig.load(path).with(WorldFeature.NOISE_SLICES, true).save(path);
        assertEquals(new WorldConfig(false, true, true, true), WorldConfig.load(path));
        WorldConfig.load(path).with(WorldFeature.END_ISLAND_HEIGHTS, false).save(path);
        assertEquals(new WorldConfig(false, true, false, true), WorldConfig.load(path));
        WorldConfig.load(path).with(WorldFeature.BIOME_OFFSETS, true).save(path);
        assertEquals(new WorldConfig(true, true, false, true), WorldConfig.load(path));
        WorldConfig.load(path).with(WorldFeature.SHAPE_TRAVERSAL, false).save(path);
        assertEquals(new WorldConfig(true, true, false, false), WorldConfig.load(path));
    }
}
