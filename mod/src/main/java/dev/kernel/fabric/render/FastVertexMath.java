package dev.kernel.fabric.render;

import org.joml.Matrix4fc;
import org.joml.Matrix3x2fc;

/**
 * Allocation-free scalar operations used by Kernel's vertex transformation and baked-quad upload paths.
 */
public final class FastVertexMath {
    private FastVertexMath() {
    }

    public static float transformX(Matrix4fc matrix, float x, float y, float z) {
        return matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30();
    }

    public static float transformY(Matrix4fc matrix, float x, float y, float z) {
        return matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31();
    }

    public static float transformZ(Matrix4fc matrix, float x, float y, float z) {
        return matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + matrix.m32();
    }

    public static float transform2DX(Matrix3x2fc matrix, float x, float y) {
        return matrix.m00() * x + matrix.m10() * y + matrix.m20();
    }

    public static float transform2DY(Matrix3x2fc matrix, float x, float y) {
        return matrix.m01() * x + matrix.m11() * y + matrix.m21();
    }

    public static int legacyColor(
        int packedVertexColor,
        float brightness,
        float red,
        float green,
        float blue,
        float alpha,
        boolean useVertexColor
    ) {
        float sourceRed = useVertexColor ? packedVertexColor & 0xFF : 255.0F;
        float sourceGreen = useVertexColor ? packedVertexColor >>> 8 & 0xFF : 255.0F;
        float sourceBlue = useVertexColor ? packedVertexColor >>> 16 & 0xFF : 255.0F;

        int outputAlpha = (int)(alpha * 255.0F);
        int outputRed = (int)(sourceRed * brightness * red);
        int outputGreen = (int)(sourceGreen * brightness * green);
        int outputBlue = (int)(sourceBlue * brightness * blue);
        return outputAlpha << 24 | outputRed << 16 | outputGreen << 8 | outputBlue;
    }
}
