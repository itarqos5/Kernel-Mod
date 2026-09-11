package dev.kernel.fabric.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.util.LinearCongruentialGenerator;
import org.junit.jupiter.api.Test;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class BiomeJitterCacheTest {
    static MethodHandle vanilla(String name, Class<?>... parameters) throws ReflectiveOperationException {
        var method = BiomeManager.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return MethodHandles.lookup().unreflect(method);
    }
    static double fiddle(MethodHandle method, long state) {
        try { return (double) method.invokeExact(state); }
        catch (Throwable exception) { throw new AssertionError(exception); }
    }

    @Test void matchesActualVanillaSelectionAcrossSeedsCoordinatesEvictionsAndBoundaries() throws Exception {
        MethodHandle fiddle = vanilla("getFiddle", long.class);
        var cache = new BiomeJitterCache(LinearCongruentialGenerator::next, state -> fiddle(fiddle, state));
        var random = new Random(780953);
        int[] selected = new int[3];
        for (int world = 0; world < 20; world++) {
            long seed = random.nextLong();
            var vanilla = new BiomeManager((x, y, z) -> { selected[0] = x; selected[1] = y; selected[2] = z; return null; }, seed);
            for (int i = 0; i < 8_000; i++) {
                int x = i < 4096 ? i / 256 - 8 : random.nextInt();
                int y = i < 4096 ? i % 16 - 8 : random.nextInt();
                int z = i < 4096 ? i / 16 % 16 - 8 : random.nextInt();
                vanilla.getBiome(new BlockPos(x, y, z));
                int corner = cache.nearest(seed, x, y, z);
                assertEquals(selected[0], ((x - 2) >> 2) + ((corner & 4) == 0 ? 0 : 1));
                assertEquals(selected[1], ((y - 2) >> 2) + ((corner & 2) == 0 ? 0 : 1));
                assertEquals(selected[2], ((z - 2) >> 2) + ((corner & 1) == 0 ? 0 : 1));
            }
        }
    }

    @Test void aCellHitSkipsSeedMathAndChangingWorldSeedRecomputes() throws Exception {
        var calls = new AtomicInteger(); MethodHandle fiddle = vanilla("getFiddle", long.class);
        var cache = new BiomeJitterCache((state, salt) -> { calls.incrementAndGet(); return LinearCongruentialGenerator.next(state, salt); }, state -> fiddle(fiddle, state));
        cache.nearest(1, 2, 2, 2); assertEquals(64, calls.get());
        cache.nearest(1, 3, 5, 4); assertEquals(64, calls.get());
        cache.nearest(2, 3, 5, 4); assertEquals(128, calls.get());
    }
}
