package dev.kernel.fabric.render;
import java.util.Random;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

class FrustumVisibilityTest {
    @org.junit.jupiter.api.Test void booleanTestsMatchEveryNativeClassification() {
        Random random = new Random(20260911);
        int cases = 0;
        float[] box = new float[6];
        for (int frame = 0; frame < 256; frame++) {
            Matrix4f matrix = new Matrix4f().perspective(.5f + random.nextFloat(), .5f + random.nextFloat() * 2, .05f, 1024)
                .rotateXYZ(random.nextFloat() * 6, random.nextFloat() * 6, random.nextFloat() * 6);
            if (frame % 16 == 0) matrix.identity();
            if (frame % 31 == 0) matrix.zero();
            if (frame % 47 == 0) matrix.m00(Float.NaN);
            var frustum = new FrustumIntersection(matrix, (frame & 1) == 0);
            for (int sample = 0; sample < 4096; sample++) {
                for (int axis = 0; axis < 3; axis++) {
                    float lo = random.nextFloat() * 2048 - 1024;
                    float hi = lo + random.nextFloat() * 256;
                    if (sample % 7 == 0) lo = hi = 0;
                    if (sample % 17 == 0) lo = -Float.MAX_VALUE;
                    if (sample % 19 == 0) hi = Float.POSITIVE_INFINITY;
                    if (sample % 23 == 0) lo = Float.NaN;
                    if (sample % 29 == 0) lo = Float.intBitsToFloat(random.nextInt());
                    if (sample % 37 == 0) hi = -hi;
                    box[axis] = lo; box[axis + 3] = hi;
                }
                int category = frustum.intersectAab(box[0], box[1], box[2], box[3], box[4], box[5]);
                boolean expected = category == FrustumIntersection.INSIDE || category == FrustumIntersection.INTERSECT;
                boolean actual = frustum.testAab(box[0], box[1], box[2], box[3], box[4], box[5]);
                if (expected != actual) throw new AssertionError("Difference at frame " + frame + " sample " + sample);
                cases++;
            }
        }
        System.out.println(cases + " native boolean/classification cases matched");
    }
}
