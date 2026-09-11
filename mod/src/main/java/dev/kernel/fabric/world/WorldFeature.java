package dev.kernel.fabric.world;

/** Independently persisted, restart-only world optimizations. */
public enum WorldFeature {
    BIOME_OFFSETS("biome_offsets"),
    NOISE_SLICES("noise_slices"),
    END_ISLAND_HEIGHTS("end_island_heights"),
    SHAPE_TRAVERSAL("shape_traversal"),
    PACKED_STORAGE("packed_storage");

    private final String key;
    WorldFeature(String key) { this.key = key; }
    public String key() { return key; }
}
