package dev.kernel.fabric.world;

import java.util.function.LongBinaryOperator;
import java.util.function.LongToDoubleFunction;

/** Thread-confined reuse of eight corner offsets for a quart cell. Contains no world/biome references. */
public final class BiomeJitterCache {
    private static final int CAPACITY = 128;
    private final long[] seeds = new long[CAPACITY];
    private final int[] xs = new int[CAPACITY], ys = new int[CAPACITY], zs = new int[CAPACITY];
    private final double[] offsets = new double[CAPACITY * 24];
    private final boolean[] valid = new boolean[CAPACITY];
    private final LongBinaryOperator next;
    private final LongToDoubleFunction fiddle;

    public BiomeJitterCache(LongBinaryOperator next, LongToDoubleFunction fiddle) {
        this.next = next; this.fiddle = fiddle;
    }

    public int nearest(long seed, int blockX, int blockY, int blockZ) {
        int bx = blockX - 2, by = blockY - 2, bz = blockZ - 2;
        int x = bx >> 2, y = by >> 2, z = bz >> 2;
        long hash = seed ^ ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) y * 0xC2B2AE3D27D4EB4FL) ^ ((long) z * 0x165667B19E3779F9L);
        int slot = (int) (hash ^ (hash >>> 32)) & (CAPACITY - 1);
        int start = slot * 24;
        if (!valid[slot] || seeds[slot] != seed || xs[slot] != x || ys[slot] != y || zs[slot] != z) {
            valid[slot] = false;
            for (int corner = 0; corner < 8; corner++) {
                int cx = x + ((corner & 4) == 0 ? 0 : 1);
                int cy = y + ((corner & 2) == 0 ? 0 : 1);
                int cz = z + ((corner & 1) == 0 ? 0 : 1);
                long state = seed;
                for (int round = 0; round < 2; round++) {
                    state = next.applyAsLong(state, cx);
                    state = next.applyAsLong(state, cy);
                    state = next.applyAsLong(state, cz);
                }
                int index = start + corner * 3;
                offsets[index] = fiddle.applyAsDouble(state);
                state = next.applyAsLong(state, seed);
                offsets[index + 1] = fiddle.applyAsDouble(state);
                state = next.applyAsLong(state, seed);
                offsets[index + 2] = fiddle.applyAsDouble(state);
            }
            seeds[slot] = seed; xs[slot] = x; ys[slot] = y; zs[slot] = z; valid[slot] = true;
        }
        double fx = (bx & 3) / 4.0, fy = (by & 3) / 4.0, fz = (bz & 3) / 4.0;
        double bestDistance = Double.POSITIVE_INFINITY;
        int best = 0;
        for (int corner = 0; corner < 8; corner++) {
            int index = start + corner * 3;
            double dx = (fx - ((corner & 4) == 0 ? 0 : 1)) + offsets[index];
            double dy = (fy - ((corner & 2) == 0 ? 0 : 1)) + offsets[index + 1];
            double dz = (fz - ((corner & 1) == 0 ? 0 : 1)) + offsets[index + 2];
            // Preserve vanilla's floating-point order and strict comparison for ties.
            double distance = dz * dz + dy * dy + dx * dx;
            if (bestDistance > distance) { bestDistance = distance; best = corner; }
        }
        return best;
    }
}
