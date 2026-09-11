package dev.kernel.fabric.render;
import java.util.Arrays;
import java.util.Random;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

public final class FrustumBenchmark {
    private static volatile int sink;
    private static int run(FrustumIntersection frustum, float[] boxes, boolean direct, int rounds) {
        int hits = 0;
        for (int round = 0; round < rounds; round++) for (int i = 0; i < boxes.length; i += 6) {
            if (direct) {
                if (frustum.testAab(boxes[i], boxes[i+1], boxes[i+2], boxes[i+3], boxes[i+4], boxes[i+5])) hits++;
            } else {
                int type = frustum.intersectAab(boxes[i], boxes[i+1], boxes[i+2], boxes[i+3], boxes[i+4], boxes[i+5]);
                if (type == FrustumIntersection.INSIDE || type == FrustumIntersection.INTERSECT) hits++;
            }
        }
        return hits;
    }
    public static void main(String[] args) {
        Random random = new Random(314159);
        var frustum = new FrustumIntersection(new Matrix4f().perspective(1.5f, 1.5f, .05f, 512));
        for (int pattern = 0; pattern < 3; pattern++) {
            float[] boxes = new float[4096 * 6];
            for (int i = 0; i < boxes.length; i += 6) {
                boxes[i] = pattern == 0 ? random.nextFloat() * 4 - 2 : pattern == 1 ? random.nextFloat() * 4 + 100 : random.nextFloat() * 128 - 64;
                boxes[i+1] = pattern == 2 ? random.nextFloat() * 128 - 64 : random.nextFloat() * 4 - 2;
                boxes[i+2] = pattern == 2 ? random.nextFloat() * 128 - 64 : -16 - random.nextFloat() * 4;
                for (int axis = 0; axis < 3; axis++) boxes[i+3+axis] = boxes[i+axis] + .5f;
            }
            int nativeHits = run(frustum, boxes, false, 1), directHits = run(frustum, boxes, true, 1);
            if (nativeHits != directHits) throw new AssertionError("Visibility differs");
            sink = run(frustum, boxes, false, 2000); sink = run(frustum, boxes, true, 2000);
            long[][] elapsed = new long[2][9];
            for (int round = 0; round < 9; round++) for (int order = 0; order < 2; order++) {
                int method = (round + order) & 1;
                long started = System.nanoTime(); sink = run(frustum, boxes, method == 1, 1000);
                elapsed[method][round] = System.nanoTime() - started;
            }
            Arrays.sort(elapsed[0]); Arrays.sort(elapsed[1]);
            System.out.printf(java.util.Locale.ROOT, "%s: %d/4096 visible; classify %.2f ns, boolean %.2f ns%n",
                new String[]{"inside", "outside", "mixed"}[pattern], nativeHits, elapsed[0][4] / 4096000.0, elapsed[1][4] / 4096000.0);
        }
    }
}
