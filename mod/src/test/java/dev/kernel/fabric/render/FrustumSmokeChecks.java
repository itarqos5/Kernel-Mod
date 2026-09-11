package dev.kernel.fabric.render;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

/** Differential checks against the untouched native classification method in a real Mixin process. */
public final class FrustumSmokeChecks {
    private static volatile int sink;
    private FrustumSmokeChecks() {}
    public static void run(boolean enabled) {
        try {
            boolean injected = Arrays.stream(Frustum.class.getDeclaredMethods()).anyMatch(method -> method.getName().contains("kernel$visibleBounds"));
            if (injected != enabled) throw new AssertionError("Frustum control did not own its Mixin");
            var classify = Frustum.class.getDeclaredMethod("cubeInFrustum", double.class, double.class, double.class, double.class, double.class, double.class);
            classify.setAccessible(true);
            Random random = new Random(20260911);
            int cases = 0;
            for (int frame = 0; frame < 128; frame++) {
                Matrix4f projection = new Matrix4f().perspective(.6f + random.nextFloat(), .75f + random.nextFloat(), .05f, 1024);
                Matrix4f view = new Matrix4f().rotateXYZ(random.nextFloat() * 6, random.nextFloat() * 6, random.nextFloat() * 6);
                if (frame % 16 == 0) projection.identity();
                if (frame % 31 == 0) projection.zero();
                if (frame % 47 == 0) projection.m00(Float.NaN);
                var frustum = new Frustum(view, projection);
                double x = random.nextDouble() * 60_000_000 - 30_000_000, y = random.nextDouble() * 8192 - 4096;
                double z = random.nextDouble() * 60_000_000 - 30_000_000;
                frustum.prepare(x, y, z);
                if (frame % 3 == 0) frustum = new Frustum(frustum);
                if (frame % 8 == 1) frustum.prepare(x + .125, y - .75, z + .5);
                for (int sample = 0; sample < 256; sample++) {
                    double bx = x + random.nextDouble() * 256 - 128, by = y + random.nextDouble() * 256 - 128;
                    double bz = z + random.nextDouble() * 256 - 128;
                    double size = sample % 5 == 0 ? 0 : random.nextDouble() * 24;
                    if (sample % 31 == 0) bx = Double.NaN;
                    if (sample % 37 == 0) by = Double.NEGATIVE_INFINITY;
                    if (sample % 41 == 0) bz = Double.MAX_VALUE;
                    var box = new AABB(bx, by, bz, bx + size, by + size, bz + size);
                    int expected = (int) classify.invoke(frustum, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
                    if (frustum.isVisible(box) != visible(expected)) throw new AssertionError("Native frustum visibility differs at " + frame + "/" + sample);
                    var integerBox = new BoundingBox((int) x - sample, (int) y - sample, (int) z - sample,
                        (int) x + sample, (int) y + sample, (int) z + sample);
                    int category = (int) classify.invoke(frustum, (double) integerBox.minX(), (double) integerBox.minY(), (double) integerBox.minZ(),
                        (double) (integerBox.maxX() + 1), (double) (integerBox.maxY() + 1), (double) (integerBox.maxZ() + 1));
                    if (frustum.cubeInFrustum(integerBox) != category) throw new AssertionError("Full native classification changed");
                    cases++;
                }
            }
            customOwnership();
            allocation();
            if (Boolean.getBoolean("kernel.frustumBenchmark")) FrustumNativeBenchmarkChecks.run();
            System.out.println("Kernel frustum visibility: enabled=" + enabled + "; " + cases + " native boxes, copy/camera changes, classification and custom ownership passed");
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Native frustum probe failed", failure); }
    }
    private static boolean visible(int category) { return category == FrustumIntersection.INSIDE || category == FrustumIntersection.INTERSECT; }
    private static void customOwnership() throws ReflectiveOperationException {
        var field = Frustum.class.getDeclaredField("intersection"); field.setAccessible(true);
        for (boolean subclass : new boolean[]{false, true}) {
            Frustum frustum = subclass ? new Frustum(new Matrix4f(), new Matrix4f()) {
                @Override public boolean isVisible(AABB box) { return !super.isVisible(box); }
            } : new Frustum(new Matrix4f(), new Matrix4f());
            var custom = new CustomIntersection(); field.set(frustum, custom);
            frustum.prepare(3, 5, 7);
            for (int value : new int[]{-3, -2, -1, 0, 1, 2, 3, 4, 5}) {
                custom.category = value; custom.calls = 0;
                boolean result = frustum.isVisible(new AABB(4, 7, 10, 7, 10, 13));
                if (custom.calls != 1 || result != (subclass != visible(value))) throw new AssertionError("Custom intersector/class ownership changed");
            }
        }
        try { new Frustum(new Matrix4f(), new Matrix4f()).isVisible(null); throw new AssertionError("Null box accepted"); }
        catch (NullPointerException expected) {}
    }
    private static final class CustomIntersection extends FrustumIntersection {
        int calls, category;
        @Override public int intersectAab(float a, float b, float c, float d, float e, float f) {
            calls++;
            if (a != 1 || b != 2 || c != 3 || d != 4 || e != 5 || f != 6) throw new AssertionError("Native relative coordinates changed");
            return category;
        }
        @Override public boolean testAab(float a, float b, float c, float d, float e, float f) { throw new AssertionError("Custom test ownership bypassed"); }
    }
    private static void allocation() {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        var frustum = new Frustum(new Matrix4f(), new Matrix4f().perspective(1.3f, 1.5f, .05f, 512));
        AABB[] boxes = new AABB[64];
        for (int i = 0; i < boxes.length; i++) boxes[i] = new AABB(i - 32, -2, -30, i - 31, 2, -28);
        for (int i = 0; i < 100_000; i++) if (frustum.isVisible(boxes[i & 63])) sink++;
        long thread = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(thread);
        int hits = 0;
        for (int i = 0; i < 100_000; i++) if (frustum.isVisible(boxes[i & 63])) hits++;
        long allocated = bean.getThreadAllocatedBytes(thread) - before; sink = hits;
        if (allocated > 100_000) throw new AssertionError("Frustum visibility allocates " + (allocated / 100_000.0) + " bytes/query");
    }
}
