package dev.kernel.fabric.world;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public final class EndIslandSmokeChecks {
    public static void run(boolean expected) throws Throwable {
        Class<?> type = Class.forName("net.minecraft.world.level.levelgen.DensityFunctions$EndIslandDensityFunction");
        Field cacheField = Arrays.stream(type.getDeclaredFields()).filter(field -> field.getName().equals("kernel$islandHeights")).findFirst().orElse(null);
        if ((cacheField != null) != expected || WorldSettings.endIslandHeightsActive() != expected) throw new AssertionError("End island Mixin activation mismatch");
        if (cacheField != null) cacheField.setAccessible(true);
        var method = type.getDeclaredMethod("getHeightValue", SimplexNoise.class, int.class, int.class);
        method.setAccessible(true); var height = MethodHandles.lookup().unreflect(method);
        var normal = new SimplexNoise(new LegacyRandomSource(932471));
        var failure = new AtomicReference<Throwable>(); var threads = new Thread[4];
        for (int worker = 0; worker < threads.length; worker++) {
            int seed = worker;
            threads[worker] = new Thread(() -> {
                try { verify(height, normal, cacheField, seed); }
                catch (Throwable exception) { failure.compareAndSet(null, exception); }
            }, "kernel-end-noise-" + worker);
            threads[worker].start();
        }
        for (var thread : threads) thread.join();
        if (failure.get() != null) throw new AssertionError("End island cache/thread isolation", failure.get());
        System.out.println("Kernel End island heights: enabled=" + expected + "; native float parity, cache population, subclass fallback and four-thread isolation passed.");
    }
    private static void verify(MethodHandle height, SimplexNoise normal, Field cacheField, int worker) throws Throwable {
        var reference = new CountingNoise(932471); var random = new Random(782311 + worker);
        for (int i = 0; i < 1024; i++) {
            int x = i < 512 ? 2048 + (i / 16) : random.nextInt();
            int z = i < 512 ? -2048 + (i % 16) : random.nextInt();
            float expected = (float) height.invokeExact((SimplexNoise) reference, x, z);
            float actual = (float) height.invokeExact(normal, x, z);
            float repeat = (float) height.invokeExact(normal, x, z);
            if (Float.floatToRawIntBits(expected) != Float.floatToRawIntBits(actual)
                || Float.floatToRawIntBits(actual) != Float.floatToRawIntBits(repeat)) throw new AssertionError("Native island height changed");
            if (cacheField != null) {
                var cache = (EndIslandCache) ((ThreadLocal<?>) cacheField.get(null)).get();
                if (cache.find(normal, x, z) < 0 || cache.find(reference, x, z) >= 0) throw new AssertionError("Native result missing or custom source cached");
            }
        }
        int calls = reference.calls;
        float first = (float) height.invokeExact((SimplexNoise) reference, 2048, -2048);
        if (reference.calls == calls) throw new AssertionError("Custom source did not run");
        calls = reference.calls;
        float second = (float) height.invokeExact((SimplexNoise) reference, 2048, -2048);
        if (reference.calls == calls || Float.floatToRawIntBits(first) != Float.floatToRawIntBits(second)) throw new AssertionError("Custom source was memoized");
    }
    private static final class CountingNoise extends SimplexNoise {
        int calls;
        CountingNoise(long seed) { super(new LegacyRandomSource(seed)); }
        @Override public double getValue(double x, double z) { calls++; return super.getValue(x, z); }
    }
}
