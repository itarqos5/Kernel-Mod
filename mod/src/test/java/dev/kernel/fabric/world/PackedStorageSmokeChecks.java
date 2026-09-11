package dev.kernel.fabric.world;

import java.lang.management.ManagementFactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.PalettedContainer;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.util.SimpleBitStorage;

public final class PackedStorageSmokeChecks {
    private static volatile int sink;
    private static MethodHandle unpack;
    public static void run(boolean expected) throws Throwable {
        boolean applied = Arrays.stream(PalettedContainer.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().contains("kernel$unpackBlocks"));
        if (applied != expected || WorldSettings.active(WorldFeature.PACKED_STORAGE) != expected)
            throw new AssertionError("Packed storage activation mismatch");
        unpack = MethodHandles.lookup().findVirtual(BitStorage.class, "unpack", MethodType.methodType(void.class, int[].class));
        if (expected) {
            var handler = Arrays.stream(PalettedContainer.class.getDeclaredMethods())
                .filter(method -> method.getName().contains("kernel$unpackBlocks")).findFirst().orElseThrow();
            handler.setAccessible(true); unpack = MethodHandles.lookup().unreflect(handler);
            if (!Modifier.isStatic(handler.getModifiers())) {
                // The legacy call-site handler reads no container state; avoid creating unrelated registries.
                var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
                var unsafe = (sun.misc.Unsafe) field.get(null);
                unpack = unpack.bindTo(unsafe.allocateInstance(PalettedContainer.class));
            }
        }
        Random random = new Random(440913);
        for (int bits = 1; bits <= 32; bits++) for (int size : new int[]{0, 1, 64, 255, 256, 257, 4095, 4096, 4097}) {
            int lanes = 64 / bits; long[] words = new long[(size + lanes - 1) / lanes];
            for (int i = 0; i < words.length; i++) words[i] = random.nextLong();
            SimpleBitStorage storage = new SimpleBitStorage(bits, size, words);
            verify(storage);
            if (words.length > 0) { words[0] ^= -1L; verify(storage); }
            verify(new SimpleBitStorage(bits, size, words) {});
            if (size == 0) unpack.invokeExact((BitStorage) storage, (int[]) null);
            else {
                try { unpack.invokeExact((BitStorage) storage, (int[]) null); throw new AssertionError("Null output accepted"); }
                catch (NullPointerException correct) { }
                int[] shortOutput = new int[size - 1];
                try { unpack.invokeExact((BitStorage) storage, shortOutput); throw new AssertionError("Short output accepted"); }
                catch (ArrayIndexOutOfBoundsException correct) { }
                for (int i = 0; i < shortOutput.length; i++) if (shortOutput[i] != storage.get(i))
                    throw new AssertionError("Short output partial writes changed");
            }
        }
        BitStorage custom = new SimpleBitStorage(4, 4096) {
            @Override public int getBits() { throw new AssertionError("Custom bit width queried"); }
            @Override public int getSize() { throw new AssertionError("Custom size queried"); }
            @Override public long[] getRaw() { throw new AssertionError("Custom packed array queried"); }
        };
        int[] customOutput = new int[4096]; Arrays.fill(customOutput, -703);
        unpack.invokeExact(custom, customOutput);
        for (int value : customOutput) if (value != 0) throw new AssertionError("Custom native decoding changed");
        BitStorage zero = new net.minecraft.util.ZeroBitStorage(4096);
        Arrays.fill(customOutput, -703); unpack.invokeExact(zero, customOutput);
        for (int value : customOutput) if (value != 0) throw new AssertionError("Zero-bit storage changed");
        if (expected) {
            var loadHandler = Arrays.stream(PalettedContainer.class.getDeclaredMethods())
                .filter(method -> method.getName().contains("kernel$unpackLoadedBlocks")).findFirst();
            if (loadHandler.isPresent()) {
                var method = loadHandler.get(); method.setAccessible(true);
                MethodHandle saved = unpack;
                unpack = MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(void.class, BitStorage.class, int[].class));
                long[] words = new long[342]; for (int i = 0; i < words.length; i++) words[i] = random.nextLong();
                verify(new SimpleBitStorage(5, 4097, words));
                unpack = saved;
            }
        }
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        for (int bits : new int[]{3, 4, 5, 8, 15, 17}) for (int size : new int[]{64, 4096}) {
            SimpleBitStorage storage = new SimpleBitStorage(bits, size); int[] output = new int[size];
            for (int i = 0; i < 20000; i++) { unpack.invokeExact((BitStorage) storage, output); sink = output[i % size]; }
            long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
            for (int i = 0; i < 10000; i++) { unpack.invokeExact((BitStorage) storage, output); sink = output[i % size]; }
            long bytes = (bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before) / 10000;
            if (bytes > 1) throw new AssertionError("Packed unpack adds allocation: " + bits + "/" + size + "=" + bytes);
        }
        System.out.println("Kernel packed storage: enabled=" + expected + "; native indexed values, raw mutations, custom subclasses, partial writes and allocation checks passed.");
    }
    private static void verify(SimpleBitStorage storage) throws Throwable {
        int[] output = new int[storage.getSize() + 3]; Arrays.fill(output, -703);
        long[] before = storage.getRaw().clone(); unpack.invokeExact((BitStorage) storage, output);
        if (!Arrays.equals(before, storage.getRaw())) throw new AssertionError("Unpack changed packed words");
        for (int i = 0; i < storage.getSize(); i++) if (output[i] != storage.get(i)) throw new AssertionError("Native indexed value differs");
        for (int i = storage.getSize(); i < output.length; i++) if (output[i] != -703) throw new AssertionError("Unpack wrote past the logical size");
    }
}
