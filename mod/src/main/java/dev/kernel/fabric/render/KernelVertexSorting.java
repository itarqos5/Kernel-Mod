package dev.kernel.fabric.render;

import com.mojang.blaze3d.vertex.VertexSorting;
//? if >=1.21.9 {
import com.mojang.blaze3d.vertex.CompactVectorArray;
//? }
import org.joml.Vector3f;

/** Preserves vanilla distance evaluation and input representation while replacing only the index sort. */
public final class KernelVertexSorting implements VertexSorting {
    private static final int RADIX_SORT_MINIMUM = 512;
    private final DistanceFunction function;
    private final VertexSorting fallback;

    public KernelVertexSorting(DistanceFunction function, VertexSorting fallback) {
        this.function = function;
        this.fallback = fallback;
    }

    @Override
    //? if >=1.21.9 {
    public int[] sort(CompactVectorArray points) {
        // Subclasses can override size/get with observable behavior. Keep their original sorting path.
        if (points.getClass() != CompactVectorArray.class) return fallback.sort(points);
        if (points.size() == 0) return new int[0];
        if (points.size() < RADIX_SORT_MINIMUM) return fallback.sort(points);
        // A custom distance function can retain this object. Match vanilla's new object for each sort.
        Vector3f point = new Vector3f();
        return StableFloatRadixSort.sort(points.size(), index -> function.apply(points.get(index, point)));
    }
    //? } else {
    /*public int[] sort(Vector3f[] points) {
        if (points.length == 0) return new int[0];
        if (points.length < RADIX_SORT_MINIMUM) return fallback.sort(points);
        return StableFloatRadixSort.sort(points.length, index -> function.apply(points[index]));
    }
    *///? }
}
