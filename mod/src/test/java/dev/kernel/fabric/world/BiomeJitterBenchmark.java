package dev.kernel.fabric.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.util.LinearCongruentialGenerator;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/** Isolated biome selection comparison, not a world-load or FPS benchmark. */
public final class BiomeJitterBenchmark {
    private static final MethodHandle FIDDLE;
    private static volatile int sink;
    static {
        try { FIDDLE = BiomeJitterCacheTest.vanilla("getFiddle", long.class); }
        catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    private static double fiddle(long state) { return BiomeJitterCacheTest.fiddle(FIDDLE, state); }
    public static void main(String[] arguments) {
        System.out.println("Biome selection microbenchmark; Java " + Runtime.version() + "; warmed JVM, median of seven samples.");
        System.out.println("scenario,native_ns_per_lookup,cache_ns_per_lookup");
        for (String scenario : new String[]{"neighboring_blocks", "random_cells"}) {
            var fixture = new Fixture(scenario);
            var cache = new BiomeJitterCache(LinearCongruentialGenerator::next, BiomeJitterBenchmark::fiddle);
            int[] result = new int[3];
            var vanilla = new BiomeManager((x, y, z) -> { result[0] = x; result[1] = y; result[2] = z; return null; }, 782354930284L);
            for (var pos : fixture.positions) {
                vanilla.getBiome(pos); int corner = cache.nearest(782354930284L, pos.getX(), pos.getY(), pos.getZ());
                if (result[0] != ((pos.getX() - 2) >> 2) + ((corner & 4) == 0 ? 0 : 1)
                    || result[1] != ((pos.getY() - 2) >> 2) + ((corner & 2) == 0 ? 0 : 1)
                    || result[2] != ((pos.getZ() - 2) >> 2) + ((corner & 1) == 0 ? 0 : 1)) throw new AssertionError(scenario);
            }
            for (int warmup = 0; warmup < 256; warmup++) { batch(fixture, vanilla, result, null); batch(fixture, vanilla, result, cache); }
            double[] original = new double[7], optimized = new double[7];
            for (int sample = 0; sample < 7; sample++) {
                if ((sample & 1) == 0) { original[sample] = measure(fixture, vanilla, result, null); optimized[sample] = measure(fixture, vanilla, result, cache); }
                else { optimized[sample] = measure(fixture, vanilla, result, cache); original[sample] = measure(fixture, vanilla, result, null); }
            }
            Arrays.sort(original); Arrays.sort(optimized);
            System.out.printf(Locale.ROOT, "%s,%.2f,%.2f%n", scenario, original[3], optimized[3]);
        }
    }
    private static double measure(Fixture fixture, BiomeManager vanilla, int[] result, BiomeJitterCache cache) {
        long started = System.nanoTime();
        for (int i = 0; i < 64; i++) batch(fixture, vanilla, result, cache);
        return (double) (System.nanoTime() - started) / (64 * fixture.positions.length);
    }
    private static void batch(Fixture fixture, BiomeManager vanilla, int[] result, BiomeJitterCache cache) {
        int total = 0;
        for (var pos : fixture.positions) {
            if (cache == null) vanilla.getBiome(pos);
            else {
                int corner = cache.nearest(782354930284L, pos.getX(), pos.getY(), pos.getZ());
                result[0] = ((pos.getX() - 2) >> 2) + ((corner & 4) == 0 ? 0 : 1);
                result[1] = ((pos.getY() - 2) >> 2) + ((corner & 2) == 0 ? 0 : 1);
                result[2] = ((pos.getZ() - 2) >> 2) + ((corner & 1) == 0 ? 0 : 1);
            }
            total += result[0] ^ result[1] ^ result[2];
        }
        sink = total;
    }
    private static final class Fixture {
        final BlockPos[] positions = new BlockPos[4096];
        Fixture(String scenario) {
            var random = new Random(78420);
            for (int i = 0; i < positions.length; i++) {
                positions[i] = scenario.equals("random_cells") ? new BlockPos(random.nextInt(), random.nextInt(), random.nextInt())
                    : new BlockPos(i / 256 - 8, i % 16 - 8, i / 16 % 16 - 8);
            }
        }
    }
}
