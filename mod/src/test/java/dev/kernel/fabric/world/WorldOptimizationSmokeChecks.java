package dev.kernel.fabric.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.BiomeManager;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldOptimizationSmokeChecks {
    public static void run() throws Throwable {
        boolean expected = Boolean.getBoolean("kernel.worldProbe.expectedEnabled");
        if (WorldSettings.noiseSlicesActive() != expected) throw new AssertionError("Noise slice activation mismatch");
        NoiseSliceSmokeChecks.run(expected);
        EndIslandSmokeChecks.run(expected);
        ShapeJoinSmokeChecks.run(expected);
        boolean applied = Arrays.stream(BiomeManager.class.getDeclaredFields()).anyMatch(field -> field.getName().equals("kernel$offsets"));
        if (expected != applied || expected != WorldSettings.biomeOffsetsActive()) throw new AssertionError("Biome Mixin activation mismatch");
        var method = BiomeManager.class.getDeclaredMethod("getFiddledDistance", long.class, int.class, int.class, int.class, double.class, double.class, double.class);
        method.setAccessible(true);
        MethodHandle reference = MethodHandles.lookup().unreflect(method);
        var failure = new AtomicReference<Throwable>();
        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            int worker = i;
            workers[i] = new Thread(() -> {
                try { verify(reference, worker); }
                catch (Throwable exception) { failure.compareAndSet(null, exception); }
            }, "kernel-biome-test-" + i);
            workers[i].start();
        }
        for (var worker : workers) worker.join();
        if (failure.get() != null) throw new AssertionError("Concurrent biome selection", failure.get());
        System.out.println("Kernel biome selection: real Mixin=" + applied + ", exact native choice/source calls/thread isolation passed.");
    }
    private static void verify(MethodHandle reference, int worker) throws Throwable {
        var random = new Random(705331 + worker);
        int[] result = new int[4];
        for (int world = 0; world < 8; world++) {
            long seed = random.nextLong();
            var manager = new BiomeManager((x, y, z) -> { result[0] = x; result[1] = y; result[2] = z; result[3]++; return null; }, seed);
            for (int index = 0; index < 2048; index++) {
                int bx = index < 1024 ? index / 128 - 4 : random.nextInt();
                int by = index < 1024 ? index % 16 - 8 : random.nextInt();
                int bz = index < 1024 ? index / 16 % 8 - 4 : random.nextInt();
                int x = (bx - 2) >> 2, y = (by - 2) >> 2, z = (bz - 2) >> 2;
                double fx = ((bx - 2) & 3) / 4.0, fy = ((by - 2) & 3) / 4.0, fz = ((bz - 2) & 3) / 4.0;
                int best = 0; double bestDistance = Double.POSITIVE_INFINITY;
                for (int corner = 0; corner < 8; corner++) {
                    int ox = (corner & 4) == 0 ? 0 : 1, oy = (corner & 2) == 0 ? 0 : 1, oz = (corner & 1) == 0 ? 0 : 1;
                    double distance = (double) reference.invokeExact(seed, x + ox, y + oy, z + oz, fx - ox, fy - oy, fz - oz);
                    if (bestDistance > distance) { bestDistance = distance; best = corner; }
                }
                result[3] = 0; manager.getBiome(new BlockPos(bx, by, bz));
                if (result[0] != x + ((best & 4) == 0 ? 0 : 1) || result[1] != y + ((best & 2) == 0 ? 0 : 1)
                    || result[2] != z + ((best & 1) == 0 ? 0 : 1) || result[3] != 1) throw new AssertionError("Native biome choice/source invocation changed");
            }
        }
    }
}
