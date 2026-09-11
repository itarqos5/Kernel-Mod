package dev.kernel.fabric.world;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.world.phys.shapes.*;

/** Measures original target-bytecode joins and the actual native entry point in one idle test process. */
public final class ShapeConstructionBenchmark {
    private static volatile DiscreteVoxelShape sink;
    private ShapeConstructionBenchmark() {}
    static void run(boolean enabled) throws Throwable {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        for (int fixture = 0; fixture < 5; fixture++) {
            int size = fixture == 0 ? 1 : fixture == 1 ? 4 : 16;
            var a = new BitSetDiscreteVoxelShape(size, size, size); var b = new BitSetDiscreteVoxelShape(size, size, size);
            if (fixture != 2) for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) for (int z = 0; z < size; z++) {
                if (fixture == 4 || (x + y * 3 + z * 7) % 5 < 2) a.fill(x, y, z);
                if (fixture == 4 || (x * 7 + y + z * 3) % 7 < 3) b.fill(x, y, z);
            }
            var merger = ShapeJoinTestSupport.identity(size);
            BooleanOp operation = fixture == 2 ? BooleanOp.FALSE : fixture == 4 ? BooleanOp.AND : BooleanOp.OR;
            long[] nativeTimes = new long[9], liveTimes = new long[9], nativeBytes = new long[9], liveBytes = new long[9];
            for (int round = -4; round < 9; round++) for (int order = 0; order < 2; order++) {
                boolean reference = (round + order) % 2 == 0;
                long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()), start = System.nanoTime();
                for (int i = 0; i < 512; i++) sink = ShapeConstructionTestSupport.join(reference, a, b, merger, merger, merger, operation);
                long nanos = System.nanoTime() - start, bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
                if (round >= 0) { (reference ? nativeTimes : liveTimes)[round] = nanos; (reference ? nativeBytes : liveBytes)[round] = bytes; }
            }
            Arrays.sort(nativeTimes); Arrays.sort(liveTimes); Arrays.sort(nativeBytes); Arrays.sort(liveBytes);
            System.out.printf(Locale.ROOT, "Kernel shape join benchmark: enabled=%s fixture=%d size=%d, native/live %.2f/%.2f ns and %.1f/%.1f bytes%n",
                enabled, fixture, size, nativeTimes[4] / 512.0, liveTimes[4] / 512.0, nativeBytes[4] / 512.0, liveBytes[4] / 512.0);
        }
    }
}
