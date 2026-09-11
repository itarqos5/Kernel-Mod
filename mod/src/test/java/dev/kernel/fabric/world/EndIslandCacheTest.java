package dev.kernel.fabric.world;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.junit.jupiter.api.Test;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

final class EndIslandCacheTest {
    static MethodHandle nativeHeight() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var method = Class.forName("net.minecraft.world.level.levelgen.DensityFunctions$EndIslandDensityFunction")
            .getDeclaredMethod("getHeightValue", SimplexNoise.class, int.class, int.class);
        method.setAccessible(true); return MethodHandles.lookup().unreflect(method);
    }
    static float height(MethodHandle nativeHeight, SimplexNoise source, int x, int z) throws Throwable {
        return (float) nativeHeight.invokeExact(source, x, z);
    }
    static float cached(EndIslandCache cache, MethodHandle nativeHeight, SimplexNoise source, int x, int z) throws Throwable {
        int slot = cache.find(source, x, z);
        if (slot >= 0) return cache.value(slot);
        float value = height(nativeHeight, source, x, z); cache.store(source, x, z, value); return value;
    }
    @Test void cachesExactNativeResultsAcrossWorldsSignedCoordinatesAndEvictions() throws Throwable {
        var method = nativeHeight(); var cache = new EndIslandCache(); var random = new Random(1702951);
        var sources = new SimplexNoise[7];
        for (int i = 0; i < sources.length; i++) sources[i] = new SimplexNoise(new LegacyRandomSource(random.nextLong()));
        for (int i = 0; i < 24_000; i++) {
            var source = sources[(i / 257) % sources.length];
            int x = i < 12_000 ? (i / 16) % 96 - 48 : random.nextInt();
            int z = i < 12_000 ? (i % 16) - 8 : random.nextInt();
            float expected = height(method, source, x, z);
            assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(cached(cache, method, source, x, z)));
            assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(cached(cache, method, source, x, z)));
        }
    }
    @Test void keepsOwnerIdentityFullCoordinateKeysAndRawFloatBits() {
        var cache = new EndIslandCache();
        Object first = new String("same"), second = new String("same");
        int[] bits = {0, 0x80000000, 0x7fc12345, 0xff800000, 0x7f800000, 0x3f800000};
        for (int i = 0; i < bits.length; i++) {
            cache.store(first, Integer.MIN_VALUE, i, Float.intBitsToFloat(bits[i]));
            assertEquals(bits[i], Float.floatToRawIntBits(cache.value(cache.find(first, Integer.MIN_VALUE, i))));
            assertEquals(-1, cache.find(second, Integer.MIN_VALUE, i));
            assertEquals(-1, cache.find(first, 0, i));
        }
        // Replacing an owner slot must invalidate its entries, including coordinates not touched by the replacement.
        Object[] replacements = new Object[16];
        for (int i = 0; i < replacements.length; i++) cache.store(replacements[i] = new Object(), i, 11, i);
        for (int i = 0; i < bits.length; i++) assertEquals(-1, cache.find(first, Integer.MIN_VALUE, i));
        java.lang.ref.Reference.reachabilityFence(replacements);
    }
    @Test void weakSourceReplacementCannotReuseOldValues() throws ReflectiveOperationException {
        var cache = new EndIslandCache(); var source = new Object(); cache.store(source, 91, -131, 5);
        var field = EndIslandCache.class.getDeclaredField("sources"); field.setAccessible(true);
        for (var reference : (WeakReference<?>[]) field.get(cache)) if (reference != null) reference.clear();
        var replacement = new Object(); assertEquals(-1, cache.find(replacement, 91, -131));
        cache.store(replacement, 91, -131, 8);
        assertEquals(8, cache.value(cache.find(replacement, 91, -131)));
        assertEquals(-1, cache.find(source, 91, -131));
    }
}
