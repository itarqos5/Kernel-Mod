package dev.kernel.fabric.world;

/** Constant-shift decoding of native packed block arrays; no shared state or retained buffers. */
public final class PackedStorageDecoder {
    private PackedStorageDecoder() {}

    public static void unpack(net.minecraft.util.BitStorage storage, int[] output) {
        if (storage.getClass() != net.minecraft.util.SimpleBitStorage.class
            || !unpack(storage.getBits(), storage.getSize(), storage.getRaw(), output)) storage.unpack(output);
    }

    /** Returns false without writing when the native implementation should handle this request. */
    public static boolean unpack(int bits, int size, long[] words, int[] output) {
        if (bits < 4 || bits > 16 || size < 256 || words == null || output == null || output.length < size) return false;
        int perWord = 64 / bits;
        if (words.length != (size - 1) / perWord + 1) return false;
        int fullWords = size / perWord;
        // Constant offsets expose independent shifts to the JIT. Each native word is read only once.
        switch (bits) {
            case 4 -> {
                for (int w = 0; w < fullWords; w++) {
                    long word = words[w]; int i = w * 16;
                    output[i] = (int) word & 15; output[i + 1] = (int) (word >>> 4) & 15; output[i + 2] = (int) (word >>> 8) & 15;
                    output[i + 3] = (int) (word >>> 12) & 15; output[i + 4] = (int) (word >>> 16) & 15; output[i + 5] = (int) (word >>> 20) & 15;
                    output[i + 6] = (int) (word >>> 24) & 15; output[i + 7] = (int) (word >>> 28) & 15; output[i + 8] = (int) (word >>> 32) & 15;
                    output[i + 9] = (int) (word >>> 36) & 15; output[i + 10] = (int) (word >>> 40) & 15; output[i + 11] = (int) (word >>> 44) & 15;
                    output[i + 12] = (int) (word >>> 48) & 15; output[i + 13] = (int) (word >>> 52) & 15; output[i + 14] = (int) (word >>> 56) & 15;
                    output[i + 15] = (int) (word >>> 60) & 15;
                }
            }
            case 5 -> {
                for (int w = 0; w < fullWords; w++) {
                    long word = words[w]; int i = w * 12;
                    output[i] = (int) word & 31; output[i + 1] = (int) (word >>> 5) & 31; output[i + 2] = (int) (word >>> 10) & 31;
                    output[i + 3] = (int) (word >>> 15) & 31; output[i + 4] = (int) (word >>> 20) & 31; output[i + 5] = (int) (word >>> 25) & 31;
                    output[i + 6] = (int) (word >>> 30) & 31; output[i + 7] = (int) (word >>> 35) & 31; output[i + 8] = (int) (word >>> 40) & 31;
                    output[i + 9] = (int) (word >>> 45) & 31; output[i + 10] = (int) (word >>> 50) & 31; output[i + 11] = (int) (word >>> 55) & 31;
                }
            }
            case 6 -> {
                for (int w = 0; w < fullWords; w++) {
                    long word = words[w]; int i = w * 10;
                    output[i] = (int) word & 63; output[i + 1] = (int) (word >>> 6) & 63; output[i + 2] = (int) (word >>> 12) & 63;
                    output[i + 3] = (int) (word >>> 18) & 63; output[i + 4] = (int) (word >>> 24) & 63; output[i + 5] = (int) (word >>> 30) & 63;
                    output[i + 6] = (int) (word >>> 36) & 63; output[i + 7] = (int) (word >>> 42) & 63; output[i + 8] = (int) (word >>> 48) & 63;
                    output[i + 9] = (int) (word >>> 54) & 63;
                }
            }
            case 7 -> {
                for (int w = 0; w < fullWords; w++) {
                    long word = words[w]; int i = w * 9;
                    output[i] = (int) word & 127; output[i + 1] = (int) (word >>> 7) & 127; output[i + 2] = (int) (word >>> 14) & 127;
                    output[i + 3] = (int) (word >>> 21) & 127; output[i + 4] = (int) (word >>> 28) & 127; output[i + 5] = (int) (word >>> 35) & 127;
                    output[i + 6] = (int) (word >>> 42) & 127; output[i + 7] = (int) (word >>> 49) & 127; output[i + 8] = (int) (word >>> 56) & 127;
                }
            }
            case 8 -> {
                for (int w = 0; w < fullWords; w++) {
                    long word = words[w]; int i = w * 8;
                    output[i] = (int) word & 255; output[i + 1] = (int) (word >>> 8) & 255; output[i + 2] = (int) (word >>> 16) & 255;
                    output[i + 3] = (int) (word >>> 24) & 255; output[i + 4] = (int) (word >>> 32) & 255; output[i + 5] = (int) (word >>> 40) & 255;
                    output[i + 6] = (int) (word >>> 48) & 255; output[i + 7] = (int) (word >>> 56) & 255;
                }
            }
            case 9 -> {
                for (int w = 0; w < fullWords; w++) {
                    long word = words[w]; int i = w * 7;
                    output[i] = (int) word & 511; output[i + 1] = (int) (word >>> 9) & 511; output[i + 2] = (int) (word >>> 18) & 511;
                    output[i + 3] = (int) (word >>> 27) & 511; output[i + 4] = (int) (word >>> 36) & 511; output[i + 5] = (int) (word >>> 45) & 511;
                    output[i + 6] = (int) (word >>> 54) & 511;
                }
            }
            case 10 -> {
                for (int w = 0; w < fullWords; w++) {
                    long word = words[w]; int i = w * 6;
                    output[i] = (int) word & 1023; output[i + 1] = (int) (word >>> 10) & 1023; output[i + 2] = (int) (word >>> 20) & 1023;
                    output[i + 3] = (int) (word >>> 30) & 1023; output[i + 4] = (int) (word >>> 40) & 1023; output[i + 5] = (int) (word >>> 50) & 1023;
                }
            }
            case 11 -> {
                for (int w = 0; w < fullWords; w++) {
                    long word = words[w]; int i = w * 5;
                    output[i] = (int) word & 2047; output[i + 1] = (int) (word >>> 11) & 2047; output[i + 2] = (int) (word >>> 22) & 2047;
                    output[i + 3] = (int) (word >>> 33) & 2047; output[i + 4] = (int) (word >>> 44) & 2047;
                }
            }
            case 12 -> {
                for (int w = 0; w < fullWords; w++) {
                    long word = words[w]; int i = w * 5;
                    output[i] = (int) word & 4095; output[i + 1] = (int) (word >>> 12) & 4095; output[i + 2] = (int) (word >>> 24) & 4095;
                    output[i + 3] = (int) (word >>> 36) & 4095; output[i + 4] = (int) (word >>> 48) & 4095;
                }
            }
            case 13 -> {
                for (int w = 0; w < fullWords; w++) {
                    long word = words[w]; int i = w * 4;
                    output[i] = (int) word & 8191; output[i + 1] = (int) (word >>> 13) & 8191; output[i + 2] = (int) (word >>> 26) & 8191;
                    output[i + 3] = (int) (word >>> 39) & 8191;
                }
            }
            case 14 -> {
                for (int w = 0; w < fullWords; w++) {
                    long word = words[w]; int i = w * 4;
                    output[i] = (int) word & 16383; output[i + 1] = (int) (word >>> 14) & 16383; output[i + 2] = (int) (word >>> 28) & 16383;
                    output[i + 3] = (int) (word >>> 42) & 16383;
                }
            }
            case 15 -> {
                for (int w = 0; w < fullWords; w++) {
                    long word = words[w]; int i = w * 4;
                    output[i] = (int) word & 32767; output[i + 1] = (int) (word >>> 15) & 32767; output[i + 2] = (int) (word >>> 30) & 32767;
                    output[i + 3] = (int) (word >>> 45) & 32767;
                }
            }
            case 16 -> {
                for (int w = 0; w < fullWords; w++) {
                    long word = words[w]; int i = w * 4;
                    output[i] = (int) word & 65535; output[i + 1] = (int) (word >>> 16) & 65535; output[i + 2] = (int) (word >>> 32) & 65535;
                    output[i + 3] = (int) (word >>> 48) & 65535;
                }
            }
        }
        int index = fullWords * perWord;
        if (index < size) {
            long word = words[fullWords]; int mask = (1 << bits) - 1;
            for (; index < size; index++) { output[index] = (int) word & mask; word >>>= bits; }
        }
        return true;
    }
}
