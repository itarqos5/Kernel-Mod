package dev.kernel.fabric.world;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Restart-only world optimization preferences; unknown keys survive saves. */
public record WorldConfig(boolean biomeOffsets, boolean noiseSlices, boolean endIslandHeights) {
    public static WorldConfig load(Path path) throws IOException {
        Properties properties = read(path);
        return new WorldConfig(Boolean.parseBoolean(properties.getProperty("biome_offsets", "true")),
            Boolean.parseBoolean(properties.getProperty("noise_slices", "true")),
            Boolean.parseBoolean(properties.getProperty("end_island_heights", "true")));
    }
    public boolean enabled(WorldFeature feature) {
        return switch (feature) {
            case BIOME_OFFSETS -> biomeOffsets;
            case NOISE_SLICES -> noiseSlices;
            case END_ISLAND_HEIGHTS -> endIslandHeights;
        };
    }
    public WorldConfig with(WorldFeature feature, boolean enabled) {
        return switch (feature) {
            case BIOME_OFFSETS -> new WorldConfig(enabled, noiseSlices, endIslandHeights);
            case NOISE_SLICES -> new WorldConfig(biomeOffsets, enabled, endIslandHeights);
            case END_ISLAND_HEIGHTS -> new WorldConfig(biomeOffsets, noiseSlices, enabled);
        };
    }
    public void save(Path path) throws IOException {
        Path target = path.toAbsolutePath();
        Properties properties = read(target);
        for (var feature : WorldFeature.values()) properties.setProperty(feature.key(), Boolean.toString(enabled(feature)));
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
