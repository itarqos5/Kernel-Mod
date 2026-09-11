package dev.kernel.fabric.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Recognizes unchanged native matrices without ever mutating a retained uniform snapshot. */
public final class ChunkMatrixSnapshot {
    private ChunkMatrixSnapshot() {}

    public static boolean matches(Matrix4f copy, Matrix4fc source) {
        return copy != null && source != null && source.getClass() == Matrix4f.class && copy.properties() == source.properties()
            && raw(copy.m00(),source.m00()) && raw(copy.m01(),source.m01()) && raw(copy.m02(),source.m02()) && raw(copy.m03(),source.m03())
            && raw(copy.m10(),source.m10()) && raw(copy.m11(),source.m11()) && raw(copy.m12(),source.m12()) && raw(copy.m13(),source.m13())
            && raw(copy.m20(),source.m20()) && raw(copy.m21(),source.m21()) && raw(copy.m22(),source.m22()) && raw(copy.m23(),source.m23())
            && raw(copy.m30(),source.m30()) && raw(copy.m31(),source.m31()) && raw(copy.m32(),source.m32()) && raw(copy.m33(),source.m33());
    }

    public static boolean canRetain(Matrix4fc source, Matrix4f copy) {
        return source != null && copy != null && source.getClass() == Matrix4f.class && copy.getClass() == Matrix4f.class && source != copy;
    }

    private static boolean raw(float first, float second) { return Float.floatToRawIntBits(first) == Float.floatToRawIntBits(second); }
}
