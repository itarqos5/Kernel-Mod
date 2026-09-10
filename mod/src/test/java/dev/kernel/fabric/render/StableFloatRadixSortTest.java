package dev.kernel.fabric.render;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class StableFloatRadixSortTest {
    @Test
    void keysFollowFloatCompareIncludingNonCanonicalNaNs() {
        float[] special = {Float.NEGATIVE_INFINITY, -Float.MAX_VALUE, -1, -Float.MIN_VALUE, -0.0f,
            0.0f, Float.MIN_VALUE, 1, Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NaN,
            Float.intBitsToFloat(0xff800001), Float.intBitsToFloat(0x7f800001)};
        for (float first : special) for (float second : special) assertKeyOrder(first, second);
        Random random = new Random(8462);
        for (int iteration = 0; iteration < 1_000_000; iteration++) {
            assertKeyOrder(Float.intBitsToFloat(random.nextInt()), Float.intBitsToFloat(random.nextInt()));
        }
    }

    @Test
    void stablePermutationMatchesReferenceAtAlgorithmAndCapacityBoundaries() {
        Random random = new Random(256);
        for (int size : new int[]{0, 1, 2, 31, 32, 33, 63, 64, 65, 255, 256, 257, 511, 512, 513, 4096, 16_384, 16_385, 65_537}) {
            float[] keys = new float[size];
            for (int kind = 0; kind < 5; kind++) {
                for (int index = 0; index < size; index++) {
                    keys[index] = switch (kind) {
                        case 0 -> Float.intBitsToFloat(random.nextInt());
                        case 1 -> random.nextInt(8);
                        case 2 -> index;
                        case 3 -> size - index;
                        default -> Float.NaN;
                    };
                }
                int[] actual = StableFloatRadixSort.sort(size, index -> keys[index]);
                assertArrayEquals(reference(keys), actual, "size=" + size + ", kind=" + kind);
            }
        }
    }

    @Test
    void constantDigitsCanBeSkippedWithoutChangingTieOrder() {
        Random random = new Random(42);
        float[] keys = new float[2048];
        for (int byteIndex = 0; byteIndex < 4; byteIndex++) {
            for (int index = 0; index < keys.length; index++) {
                keys[index] = Float.intBitsToFloat(0x3f800000 ^ (random.nextInt(256) << (byteIndex * 8)));
            }
            assertArrayEquals(reference(keys), StableFloatRadixSort.sort(keys.length, index -> keys[index]));
        }
    }

    @Test
    void evaluatesKeysOnceInInputOrderAndDoesNotReuseReturnedStorage() {
        int[] calls = {0};
        int[] first = StableFloatRadixSort.sort(100, index -> {
            assertEquals(calls[0]++, index);
            return index % 3;
        });
        assertEquals(100, calls[0]);
        int[] saved = first.clone();
        int[] second = StableFloatRadixSort.sort(100, index -> -index);
        Arrays.fill(second, -1);
        assertArrayEquals(saved, first);
        assertNotSame(StableFloatRadixSort.sort(0, null), StableFloatRadixSort.sort(0, null));
        assertThrows(NullPointerException.class, () -> StableFloatRadixSort.sort(1, null));
    }

    @Test
    void nestedEvaluationAndExceptionsReleaseIndependentScratch() throws Exception {
        StableFloatRadixSort.Scratch primary = primaryScratch();
        int[] result = StableFloatRadixSort.sort(500, index -> {
            if (index == 211) {
                assertArrayEquals(new int[]{2, 1, 0}, StableFloatRadixSort.sort(3, nested -> nested));
                RuntimeException failure = new RuntimeException("distance callback");
                assertSame(failure, assertThrows(RuntimeException.class, () -> StableFloatRadixSort.sort(400, nested -> {
                    if (nested == 200) throw failure;
                    return nested;
                })));
            }
            return index;
        });
        assertArrayEquals(IntStream.range(0, 500).map(index -> 499 - index).toArray(), result);
        assertSame(primary, primaryScratch());
        assertThrows(IllegalStateException.class, () -> StableFloatRadixSort.sort(500, index -> {
            if (index == 100) throw new IllegalStateException("outer failure");
            return index;
        }));
        assertSame(primary, primaryScratch());
    }

    @Test
    void largeMeshesDoNotGrowRetainedWorkerScratch() throws Exception {
        StableFloatRadixSort.sort(1024, index -> index);
        StableFloatRadixSort.Scratch primary = primaryScratch();
        int[] retainedKeys = primary.keys;
        int[] retainedOrder = primary.order;
        int size = StableFloatRadixSort.MAX_RETAINED_QUADS + 1;
        int[] result = StableFloatRadixSort.sort(size, index -> index);
        assertEquals(size - 1, result[0]);
        assertSame(retainedKeys, primary.keys);
        assertSame(retainedOrder, primary.order);
        assertTrue(primary.keys.length <= StableFloatRadixSort.MAX_RETAINED_QUADS);
    }

    @Test
    void simultaneousWorkersRemainIndependent() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = IntStream.range(0, 4).mapToObj(worker -> (Callable<Void>) () -> {
                Random random = new Random(worker);
                for (int iteration = 0; iteration < 100; iteration++) {
                    float[] keys = new float[1024];
                    for (int index = 0; index < keys.length; index++) keys[index] = Float.intBitsToFloat(random.nextInt());
                    assertArrayEquals(reference(keys), StableFloatRadixSort.sort(keys.length, index -> keys[index]));
                }
                return null;
            }).toList();
            for (var result : executor.invokeAll(jobs)) result.get();
        }
    }

    private static void assertKeyOrder(float first, float second) {
        assertEquals(Integer.signum(Float.compare(second, first)), Integer.signum(Integer.compare(
            StableFloatRadixSort.descendingKey(first), StableFloatRadixSort.descendingKey(second))));
    }

    private static int[] reference(float[] keys) {
        Integer[] order = IntStream.range(0, keys.length).boxed().toArray(Integer[]::new);
        Arrays.sort(order, (first, second) -> Float.compare(keys[second], keys[first]));
        return Arrays.stream(order).mapToInt(Integer::intValue).toArray();
    }

    @SuppressWarnings("unchecked")
    private static StableFloatRadixSort.Scratch primaryScratch() throws Exception {
        var field = StableFloatRadixSort.class.getDeclaredField("SCRATCH");
        field.setAccessible(true);
        var pool = (ReentrantThreadLocalPool<StableFloatRadixSort.Scratch>) field.get(null);
        try {
            return pool.acquire();
        } finally {
            pool.release();
        }
    }
}
