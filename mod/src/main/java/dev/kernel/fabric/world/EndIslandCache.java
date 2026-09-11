package dev.kernel.fabric.world;

import java.lang.ref.WeakReference;

/** Thread-confined exact-result cache for immutable native End-island noise sources. */
public final class EndIslandCache {
    private static final int MASK = 1023;
    private final long[] keys = new long[MASK + 1];
    private final float[] values = new float[MASK + 1];
    private final byte[] owners = new byte[MASK + 1];
    private final WeakReference<?>[] sources = new WeakReference<?>[4];
    private int recent, next;

    public int find(Object source, int x, int z) {
        int owner = owner(source);
        if (owner < 0) return -1;
        long key = key(x, z);
        int slot = slot(key, owner);
        return owners[slot] == owner + 1 && keys[slot] == key ? slot : -1;
    }
    public float value(int slot) { return values[slot]; }
    public void store(Object source, int x, int z, float value) {
        int owner = owner(source);
        if (owner < 0) {
            owner = next++ & 3;
            for (int i = 0; i < sources.length; i++) {
                if (sources[i] == null || sources[i].get() == null) { owner = i; break; }
            }
            for (int i = 0; i < owners.length; i++) if (owners[i] == owner + 1) owners[i] = 0;
            sources[owner] = new WeakReference<>(source);
        }
        recent = owner;
        long key = key(x, z);
        int slot = slot(key, owner);
        keys[slot] = key; values[slot] = value; owners[slot] = (byte) (owner + 1);
    }
    private int owner(Object source) {
        var current = sources[recent];
        if (current != null && current.get() == source) return recent;
        for (int i = 0; i < sources.length; i++) {
            if (sources[i] != null && sources[i].get() == source) { recent = i; return i; }
        }
        return -1;
    }
    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
    private static int slot(long key, int owner) {
        long mixed = key ^ (key >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        return ((int) mixed ^ (owner * 0x9e3779b9)) & MASK;
    }
}
