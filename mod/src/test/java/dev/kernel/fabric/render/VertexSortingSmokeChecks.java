package dev.kernel.fabric.render;

import com.mojang.blaze3d.vertex.VertexSorting;
//? if >=1.21.9 {
import com.mojang.blaze3d.vertex.CompactVectorArray;
//? }
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.stream.IntStream;

/** Loaded only through Knot's transforming game classloader. No JUnit launcher is involved. */
public final class VertexSortingSmokeChecks {
    public static void run() {
        if (!(VertexSorting.DISTANCE_TO_ORIGIN instanceof KernelVertexSorting)
            || !(VertexSorting.ORTHOGRAPHIC_Z instanceof KernelVertexSorting)
            || !(VertexSorting.byDistance(1, 2, 3) instanceof KernelVertexSorting)) {
            throw new AssertionError("Kernel's sorting factory was not applied before static initialization");
        }
        float[] keys = new float[1025];
        for (int index = 0; index < keys.length; index++) keys[index] = (index * 17) % 13;
        keys[1] = Float.NaN;
        keys[17] = -0.0f;
        keys[201] = Float.intBitsToFloat(0xff800001);
        keys[202] = Float.NEGATIVE_INFINITY;
        //? if >=1.21.9 {
        CompactVectorArray points = new CompactVectorArray(keys.length);
        for (int index = 0; index < keys.length; index++) points.set(index, keys[index], 0, 0);
        //? } else {
        /*Vector3f[] points = new Vector3f[keys.length];
        for (int index = 0; index < keys.length; index++) points[index] = new Vector3f(keys[index], 0, 0);
        *///? }
        int[] calls = {0};
        VertexSorting sorting = VertexSorting.byDistance(point -> {
            calls[0]++;
            return point.x();
        });
        if (!(sorting instanceof KernelVertexSorting)) throw new AssertionError("Custom distance factory did not use Kernel");
        Integer[] reference = IntStream.range(0, keys.length).boxed().toArray(Integer[]::new);
        Arrays.sort(reference, (first, second) -> Float.compare(keys[second], keys[first]));
        int[] expected = Arrays.stream(reference).mapToInt(Integer::intValue).toArray();
        if (!Arrays.equals(expected, sorting.sort(points)) || calls[0] != keys.length) {
            throw new AssertionError("Transformed factory produced incorrect ordering or callback count");
        }
        VertexSorting custom = ignored -> new int[]{42};
        if (!Arrays.equals(new int[]{42}, custom.sort(points))) throw new AssertionError("Custom sorting implementation changed");
        System.out.println("Kernel vertex sorting: real Fabric/Mixin factory smoke test passed.");
    }
}
