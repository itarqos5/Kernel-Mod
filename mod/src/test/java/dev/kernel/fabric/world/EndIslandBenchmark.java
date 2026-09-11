package dev.kernel.fabric.world;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;

/** Isolated native height evaluation; this excludes generation scheduling and Mixin dispatch. */
public final class EndIslandBenchmark {
    private static volatile float sink;
    public static void main(String[] args) throws Throwable {
        MethodHandle method = EndIslandCacheTest.nativeHeight();
        var source = new SimplexNoise(new LegacyRandomSource(2718281828L));
        for (boolean scattered : new boolean[]{false, true}) {
            for (int i = 0; i < 4; i++) { sample(method, source, false, scattered); sample(method, source, true, scattered); }
            long[] nativeTimes = new long[7], cachedTimes = new long[7];
            for (int i = 0; i < 7; i++) {
                if ((i & 1) == 0) {
                    nativeTimes[i] = sample(method, source, false, scattered); cachedTimes[i] = sample(method, source, true, scattered);
                } else { cachedTimes[i] = sample(method, source, true, scattered); nativeTimes[i] = sample(method, source, false, scattered); }
            }
            Arrays.sort(nativeTimes); Arrays.sort(cachedTimes);
            System.out.printf("End height %s: native %.2f ns/query, Kernel %.2f ns/query%n", scattered ? "scattered" : "local vertical reuse",
                nativeTimes[3] / 32_768.0, cachedTimes[3] / 32_768.0);
        }
    }
    private static long sample(MethodHandle method, SimplexNoise source, boolean enabled, boolean scattered) throws Throwable {
        var cache = new EndIslandCache(); float result = 0;
        long started = System.nanoTime();
        for (int i = 0; i < 32_768; i++) {
            int x = scattered ? (i * 7919) % 8_000_000 : 2048 + ((i / 1024) % 32);
            int z = scattered ? (i * 104729) % 8_000_000 : -2048 + ((i / 32) % 32);
            float value = enabled ? EndIslandCacheTest.cached(cache, method, source, x, z) : EndIslandCacheTest.height(method, source, x, z);
            result += value;
        }
        long elapsed = System.nanoTime() - started; sink = result; return elapsed;
    }
}
