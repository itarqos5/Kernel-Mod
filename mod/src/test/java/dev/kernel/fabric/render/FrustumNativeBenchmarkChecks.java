package dev.kernel.fabric.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

/** Measures transformed public queries against the untouched native classification helper. */
public final class FrustumNativeBenchmarkChecks {
    private static final MethodHandle CLASSIFY = classifier();
    private static volatile int sink;
    private FrustumNativeBenchmarkChecks() {}
    private static MethodHandle classifier() {
        try {
            return MethodHandles.privateLookupIn(Frustum.class, MethodHandles.lookup()).findVirtual(Frustum.class, "cubeInFrustum",
                MethodType.methodType(int.class, double.class, double.class, double.class, double.class, double.class, double.class));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    public static void run() {
        try { measure(); } catch (Throwable failure) { throw new AssertionError("Native frustum benchmark failed", failure); }
    }
    private static int query(Frustum frustum, AABB[] boxes, boolean direct, int rounds) throws Throwable {
        int hits = 0;
        for (int round = 0; round < rounds; round++) for (var box : boxes) {
            if (direct) { if (frustum.isVisible(box)) hits++; }
            else {
                int type = (int) CLASSIFY.invokeExact(frustum, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
                if (type == FrustumIntersection.INSIDE || type == FrustumIntersection.INTERSECT) hits++;
            }
        }
        return hits;
    }
    private static void measure() throws Throwable {
        Random random = new Random(314159);
        var frustum = new Frustum(new Matrix4f(), new Matrix4f().perspective(1.5f, 1.5f, .05f, 512));
        double cx = 1234.25, cy = -12.75, cz = -9876.125;
        frustum.prepare(cx, cy, cz);
        for (int pattern = 0; pattern < 3; pattern++) {
            AABB[] boxes = new AABB[4096];
            for (int i = 0; i < boxes.length; i++) {
                double x = cx + (pattern == 0 ? random.nextFloat() * 4 - 2 : pattern == 1 ? random.nextFloat() * 4 + 100 : random.nextFloat() * 128 - 64);
                double y = cy + (pattern == 2 ? random.nextFloat() * 128 - 64 : random.nextFloat() * 4 - 2);
                double z = cz + (pattern == 2 ? random.nextFloat() * 128 - 64 : -16 - random.nextFloat() * 4);
                boxes[i] = new AABB(x, y, z, x + .5, y + .5, z + .5);
            }
            int nativeHits = query(frustum, boxes, false, 1), directHits = query(frustum, boxes, true, 1);
            if (nativeHits != directHits) throw new AssertionError("Native visibility differs");
            sink = query(frustum, boxes, false, 2000); sink = query(frustum, boxes, true, 2000);
            long[][] elapsed = new long[2][9];
            for (int round = 0; round < 9; round++) for (int order = 0; order < 2; order++) {
                int method = (round + order) & 1;
                long start = System.nanoTime(); sink = query(frustum, boxes, method == 1, 1000);
                elapsed[method][round] = System.nanoTime() - start;
            }
            Arrays.sort(elapsed[0]); Arrays.sort(elapsed[1]);
            System.out.printf(java.util.Locale.ROOT, "Native frustum %s: %d/4096 visible; classification %.2f ns, public query %.2f ns%n",
                new String[]{"inside", "outside", "mixed"}[pattern], nativeHits, elapsed[0][4] / 4096000.0, elapsed[1][4] / 4096000.0);
        }
    }
}
