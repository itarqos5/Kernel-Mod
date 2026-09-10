package dev.kernel.fabric.render;

import com.mojang.blaze3d.vertex.VertexSorting;
//? if >=1.21.9 {
import com.mojang.blaze3d.vertex.CompactVectorArray;
//? }
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against this target's actual unmodified vanilla implementation; JUnit does not apply the Mixins. */
class KernelVertexSortingTest {
    @Test
    void matchesVanillaForCameraDistancesAndOrthographicDepths() {
        Random random = new Random(125);
        for (int iteration = 0; iteration < 180; iteration++) {
            int size = iteration < 40 ? iteration : random.nextInt(8192);
            Vector3f[] positions = new Vector3f[size];
            for (int index = 0; index < size; index++) {
                positions[index] = new Vector3f(random.nextInt(32), random.nextInt(32), random.nextInt(32));
            }
            var points = input(positions);
            Vector3f camera = new Vector3f(random.nextFloat() * 64 - 32, random.nextFloat() * 64 - 32, random.nextFloat() * 64 - 32);
            VertexSorting.DistanceFunction function = camera::distanceSquared;
            assertArrayEquals(VertexSorting.byDistance(camera).sort(points), kernel(function).sort(points));
            assertArrayEquals(VertexSorting.ORTHOGRAPHIC_Z.sort(points), kernel(point -> -point.z()).sort(points));
            // The factory captures the origin object, so later camera mutations must still be visible.
            VertexSorting vanilla = VertexSorting.byDistance(camera);
            VertexSorting kernel = kernel(function);
            camera.negate();
            assertArrayEquals(vanilla.sort(points), kernel.sort(points));
        }
    }

    @Test
    void matchesVanillaForCustomKeysWithTiesNaNsInfinitiesAndSignedZero() {
        Random random = new Random(527);
        float[] special = {0f, -0f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
            Float.intBitsToFloat(0x7f800001), Float.intBitsToFloat(0xff800001), Float.NaN};
        for (int size : new int[]{0, 1, 2, 32, 33, 256, 511, 512, 513, 2048, 16_384, 16_385, 65_537}) {
            Vector3f[] positions = new Vector3f[size];
            for (int index = 0; index < size; index++) {
                float key = (index & 3) == 0 ? Float.intBitsToFloat(random.nextInt()) : special[index % special.length];
                positions[index] = new Vector3f(key, index, 0);
            }
            var points = input(positions);
            assertArrayEquals(VertexSorting.byDistance(Vector3f::x).sort(points), kernel(Vector3f::x).sort(points));
        }
    }

    @Test
    void callbacksObserveVanillaInputOrderMutationsAndObjectLifetime() {
        Vector3f[] original = new Vector3f[600];
        for (int index = 0; index < original.length; index++) original[index] = new Vector3f(index, 0, 0);
        var vanillaPoints = input(original);
        var kernelPoints = input(original);
        List<Vector3f> vanillaSeen = new ArrayList<>();
        List<Vector3f> kernelSeen = new ArrayList<>();
        List<Float> vanillaValues = new ArrayList<>();
        List<Float> kernelValues = new ArrayList<>();
        VertexSorting vanilla = VertexSorting.byDistance(point -> capture(point, vanillaSeen, vanillaValues));
        VertexSorting kernel = kernel(point -> capture(point, kernelSeen, kernelValues));
        assertArrayEquals(vanilla.sort(vanillaPoints), kernel.sort(kernelPoints));
        assertArrayEquals(vanilla.sort(vanillaPoints), kernel.sort(kernelPoints));
        assertEquals(vanillaValues, kernelValues);
        assertEquals(1200, kernelSeen.size());
        for (int index = 1; index < kernelSeen.size(); index++) {
            assertEquals(vanillaSeen.get(index - 1) == vanillaSeen.get(index), kernelSeen.get(index - 1) == kernelSeen.get(index));
        }
        assertEquals(vanillaSeen.get(0) == vanillaSeen.get(600), kernelSeen.get(0) == kernelSeen.get(600));
        //? if <1.21.9 {
        /*for (int index = 0; index < kernelPoints.length; index++) {
            assertSame(kernelPoints[index], kernelSeen.get(index));
        }
        *///? }
    }

    private static float capture(Vector3f point, List<Vector3f> seen, List<Float> values) {
        seen.add(point);
        values.add(point.x());
        float key = point.x() % 7;
        point.x += 1;
        return key;
    }

    private static VertexSorting kernel(VertexSorting.DistanceFunction function) {
        return new KernelVertexSorting(function, VertexSorting.byDistance(function));
    }

    //? if >=1.21.9 {
    @Test
    void subclassesOfCompactInputRetainTheOriginalAccessPattern() {
        List<String> vanillaAccess = new ArrayList<>();
        List<String> kernelAccess = new ArrayList<>();
        VertexSorting.DistanceFunction function = Vector3f::x;
        int[] expected = VertexSorting.byDistance(function).sort(tracedInput(vanillaAccess));
        int[] actual = kernel(function).sort(tracedInput(kernelAccess));
        assertArrayEquals(expected, actual);
        assertEquals(vanillaAccess, kernelAccess);
    }

    private static CompactVectorArray tracedInput(List<String> access) {
        return new CompactVectorArray(0) {
            @Override
            public int size() {
                access.add("size");
                return 600;
            }

            @Override
            public Vector3f get(int index, Vector3f output) {
                access.add("get:" + index);
                return output.set(index, 0, 0);
            }
        };
    }
    //? }

    //? if >=1.21.9 {
    static CompactVectorArray input(Vector3f[] positions) {
        CompactVectorArray result = new CompactVectorArray(positions.length);
        for (int index = 0; index < positions.length; index++) {
            Vector3f point = positions[index];
            result.set(index, point.x(), point.y(), point.z());
        }
        return result;
    }
    //? } else {
    /*static Vector3f[] input(Vector3f[] positions) {
        Vector3f[] result = new Vector3f[positions.length];
        for (int index = 0; index < positions.length; index++) result[index] = new Vector3f(positions[index]);
        return result;
    }
    *///? }
}
