package dev.kernel.fabric.world;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Optional actual-getter benchmark with independent escaping and scalar-consumption call sites. */
final class ShapeCoordinatesBenchmark {
    private static final ThreadMXBean ALLOCATION = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static volatile Object escape;
    private static volatile double result;
    private static final Axis[] AXES = Axis.values();
    static void run() throws Throwable {
        for (int base : new int[]{0, 64}) {
            VoxelShape[][] shapes = new VoxelShape[2][64];
            for (int i = 0; i < 64; i++) {
                int size = base + (i * 37 & 63) + 1;
                var grid = new ShapeCoordinatesSmokeChecks.Grid(size, size, size);
                shapes[0][i] = ShapeCoordinatesTestSupport.create(grid, true);
                shapes[1][i] = ShapeCoordinatesTestSupport.create(grid, false);
            }
            for (boolean escaping : new boolean[]{true, false}) {
                long[][] time = new long[2][15], bytes = new long[2][15];
                for (int round = -12; round < 15; round++) for (int offset = 0; offset < 2; offset++) {
                    int variant = Math.floorMod(round + offset, 2);
                    long allocated = ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().threadId()), start = System.nanoTime();
                    if (escaping) escaping(shapes[variant]); else scalar(shapes[variant]);
                    long elapsed = System.nanoTime() - start;
                    long used = ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
                    if (round >= 0) { time[variant][round] = elapsed; bytes[variant][round] = used; }
                }
                for (int variant = 0; variant < 2; variant++) {
                    Arrays.sort(time[variant]); Arrays.sort(bytes[variant]);
                    System.out.printf(Locale.ROOT, "Kernel coordinate benchmark: sizes=%d-%d %s %s enabled=%s ns/query=%.4f B/query=%.4f%n",
                        base+1, base+64, escaping ? "escaping" : "scalar", variant == 0 ? "reference" : "live",
                        WorldSettings.active(WorldFeature.SHAPE_COORDINATES), (double)time[variant][7]/65536, (double)bytes[variant][7]/65536);
                }
            }
        }
    }
    private static void escaping(VoxelShape[] shapes) {
        for (int i = 0; i < 65536; i++) escape = shapes[i & 63].getCoords(AXES[i % 3]);
    }
    private static void scalar(VoxelShape[] shapes) {
        double sum = 0;
        for (int i = 0; i < 65536; i++) { var list = shapes[i & 63].getCoords(AXES[i % 3]); sum += list.getDouble(1) + list.size(); }
        result = sum;
    }
}
