package dev.kernel.fabric.world;

import java.util.Arrays;
import java.util.Random;
import net.minecraft.util.SimpleBitStorage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackedStorageDecoderTest {
    @Test void matchesNativeDecodingAndIndexedValuesWithoutTouchingInputsOrUnusedOutput() {
        Random random = new Random(20260911);
        for (int bits = 1; bits <= 32; bits++) {
            int lanes = 64 / bits;
            for (int size : new int[]{0, 1, 63, 64, 65, 255, 256, 257, 511, 512, 4095, 4096, 4097, 8193}) {
                for (int repeat = 0; repeat < 16; repeat++) {
                    long[] words = new long[(size + lanes - 1) / lanes];
                    for (int w = 0; w < words.length; w++) words[w] = random.nextLong();
                    var storage = new SimpleBitStorage(bits, size, words);
                    int[] expected = new int[size + 7], actual = new int[size + 7];
                    Arrays.fill(expected, -703); Arrays.fill(actual, -703);
                    storage.unpack(expected);
                    long[] before = storage.getRaw().clone();
                    boolean handled = PackedStorageDecoder.unpack(bits, size, storage.getRaw(), actual);
                    assertEquals(bits >= 4 && bits <= 16 && size >= 256, handled);
                    assertArrayEquals(before, storage.getRaw());
                    if (!handled) { for (int value : actual) assertEquals(-703, value); storage.unpack(actual); }
                    assertArrayEquals(expected, actual);
                    for (int i = 0; i < size; i++) assertEquals(storage.get(i), actual[i]);
                    if (handled) {
                        storage.getRaw()[0] ^= -1L;
                        storage.unpack(expected);
                        assertTrue(PackedStorageDecoder.unpack(bits, size, storage.getRaw(), actual));
                        assertArrayEquals(expected, actual); // No cached or retained source words.
                    }
                }
            }
        }
    }

    @Test void unsuitableDimensionsAndShortOutputsFallBackBeforeAnyWrite() {
        int[] output = new int[4096]; Arrays.fill(output, -703);
        long[] words = new long[256];
        for (int size : new int[]{Integer.MIN_VALUE, -1, 0, 64, 255, 4097, Integer.MAX_VALUE})
            assertFalse(PackedStorageDecoder.unpack(4, size, words, output));
        for (int bits : new int[]{Integer.MIN_VALUE, -1, 0, 1, 2, 3, 17, 32, 64, Integer.MAX_VALUE})
            assertFalse(PackedStorageDecoder.unpack(bits, 4096, words, output));
        assertFalse(PackedStorageDecoder.unpack(4, 4096, null, output));
        assertFalse(PackedStorageDecoder.unpack(4, 4096, words, null));
        assertFalse(PackedStorageDecoder.unpack(4, 4096, new long[255], output));
        assertFalse(PackedStorageDecoder.unpack(4, 4096, new long[257], output));
        for (int value : output) assertEquals(-703, value);
        for (int size : new int[]{0, 1, 255, 256, 4095}) {
            int[] shortOutput = new int[size]; Arrays.fill(shortOutput, -703);
            assertFalse(PackedStorageDecoder.unpack(4, 4096, words, shortOutput));
            for (int value : shortOutput) assertEquals(-703, value);
        }
    }
}
