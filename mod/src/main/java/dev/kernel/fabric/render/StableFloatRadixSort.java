package dev.kernel.fabric.render;

import java.util.Arrays;

/** Stable descending float-key sort. The returned indices belong exclusively to the caller. */
public final class StableFloatRadixSort {
    static final int MAX_RETAINED_QUADS = 16_384;
    private static final ReentrantThreadLocalPool<Scratch> SCRATCH =
        new ReentrantThreadLocalPool<>(Scratch::new, scratch -> {});

    private StableFloatRadixSort() {
    }

    @FunctionalInterface
    public interface KeyFunction {
        float key(int index);
    }

    public static int[] sort(int size, KeyFunction function) {
        int[] result = new int[size];
        if (size == 0) return result;
        if (size == 1) {
            function.key(0);
            return result;
        }

        Scratch pooled = SCRATCH.acquire();
        try {
            // Large modded meshes are supported without pinning their temporary arrays to a worker thread.
            Scratch scratch = size <= MAX_RETAINED_QUADS ? pooled : new Scratch();
            scratch.ensureCapacity(size);
            int[] keys = scratch.keys;
            int variation = 0;
            boolean ordered = true;
            for (int index = 0; index < size; index++) {
                int key = descendingKey(function.key(index));
                keys[index] = key;
                result[index] = index;
                variation |= key ^ keys[0];
                if (index != 0 && keys[index - 1] > key) ordered = false;
            }
            if (ordered) return result;

            int[] histogram = scratch.histogram;
            Arrays.fill(histogram, 0);
            for (int index = 0; index < size; index++) {
                int key = keys[index];
                histogram[key & 255]++;
                histogram[256 + ((key >>> 8) & 255)]++;
                histogram[512 + ((key >>> 16) & 255)]++;
                histogram[768 + ((key >>> 24) ^ 128)]++;
            }

            int[] source = result;
            int[] destination = scratch.order;
            for (int shift = 0; shift < 32; shift += 8) {
                // A constant digit cannot change the order. Skipping it also helps quantized geometry.
                if (((variation >>> shift) & 255) == 0) continue;
                int base = (shift >>> 3) * 256;
                int offset = 0;
                for (int bucket = base; bucket < base + 256; bucket++) {
                    int count = histogram[bucket];
                    histogram[bucket] = offset;
                    offset += count;
                }
                for (int index = 0; index < size; index++) {
                    int quad = source[index];
                    int digit = (keys[quad] >>> shift) & 255;
                    int bucket = base + (shift == 24 ? digit ^ 128 : digit);
                    destination[histogram[bucket]++] = quad;
                }
                int[] swap = source;
                source = destination;
                destination = swap;
            }
            if (source != result) System.arraycopy(source, 0, result, 0, size);
            return result;
        } finally {
            SCRATCH.release();
        }
    }

    /** Signed ascending integer order equals descending Float.compare order, including NaNs and signed zero. */
    static int descendingKey(float value) {
        int bits = Float.floatToIntBits(value);
        return bits ^ ((~bits >> 31) & Integer.MAX_VALUE) ^ Integer.MIN_VALUE;
    }

    static final class Scratch {
        int[] keys = new int[0];
        int[] order = new int[0];
        final int[] histogram = new int[1024];

        void ensureCapacity(int size) {
            if (keys.length >= size) return;
            int capacity = size > MAX_RETAINED_QUADS ? size : Integer.highestOneBit(size - 1) << 1;
            keys = new int[capacity];
            order = new int[capacity];
        }
    }
}
