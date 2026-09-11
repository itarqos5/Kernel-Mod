package dev.kernel.fabric.world;

import net.minecraft.world.phys.shapes.CubePointRange;

/** Shares bounded native immutable coordinate lists, never world-dependent geometry. */
public final class CubeCoordinateCache {
    private static final CubePointRange[] RANGES = ranges();
    private CubeCoordinateCache() {}
    private static CubePointRange[] ranges() {
        var values = new CubePointRange[65];
        for (int parts = 1; parts < values.length; parts++) values[parts] = new CubePointRange(parts);
        return values;
    }
    public static CubePointRange create(int parts) {
        return parts > 0 && parts < RANGES.length ? RANGES[parts] : new CubePointRange(parts);
    }
}
