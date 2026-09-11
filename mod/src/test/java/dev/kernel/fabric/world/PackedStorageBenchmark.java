package dev.kernel.fabric.world;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import net.minecraft.util.SimpleBitStorage;

/** Isolated decoder cost; this task does not start Fabric or claim a world-loading speedup. */
public final class PackedStorageBenchmark {
    private static volatile int sink;
    public static void main(String[] args) {
        Random random = new Random(20260911);
        for (int bits : new int[]{4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}) {
            SimpleBitStorage[] stores = new SimpleBitStorage[8];
            int[] output = new int[4096];
            for (int i = 0; i < stores.length; i++) {
                int[] input = new int[output.length];
                for (int j = 0; j < input.length; j++) input[j] = random.nextInt(1 << bits);
                stores[i] = new SimpleBitStorage(bits, input.length, input);
            }
            for (int i = 0; i < 20000; i++) {
                SimpleBitStorage storage = stores[i & 7];
                storage.unpack(output);
                PackedStorageDecoder.unpack(bits, output.length, storage.getRaw(), output);
                sink = output[i & 4095];
            }
            double[] nativeNs = new double[9], kernelNs = new double[9];
            for (int round = 0; round < 9; round++) for (int order = 0; order < 2; order++) {
                boolean kernel = ((round + order) & 1) != 0;
                long started = System.nanoTime(); int sum = 0;
                for (int i = 0; i < 20000; i++) {
                    SimpleBitStorage storage = stores[i & 7];
                    if (kernel) PackedStorageDecoder.unpack(bits, output.length, storage.getRaw(), output);
                    else storage.unpack(output);
                    sum += output[(i * 17) & 4095];
                }
                (kernel ? kernelNs : nativeNs)[round] = (System.nanoTime() - started) / 20000.0;
                sink = sum;
            }
            Arrays.sort(nativeNs); Arrays.sort(kernelNs);
            System.out.printf(Locale.ROOT, "bits=%d native=%.1f ns Kernel=%.1f ns per 4096 values%n", bits, nativeNs[4], kernelNs[4]);
        }
    }
}
