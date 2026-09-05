package dev.kernel.fabric.render;

import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FastVertexMathTest {
    @Test
    void scalarTransformMatchesJoml() {
        Matrix4f matrix = new Matrix4f()
            .translate(12.5F, -3.25F, 8.75F)
            .rotateXYZ(0.31F, -0.72F, 1.13F)
            .scale(2.0F, 0.5F, 1.25F);
        Vector3f expected = matrix.transformPosition(0.125F, -2.5F, 7.75F, new Vector3f());

        assertEquals(expected.x(), FastVertexMath.transformX(matrix, 0.125F, -2.5F, 7.75F));
        assertEquals(expected.y(), FastVertexMath.transformY(matrix, 0.125F, -2.5F, 7.75F));
        assertEquals(expected.z(), FastVertexMath.transformZ(matrix, 0.125F, -2.5F, 7.75F));
    }

    @Test
    void scalar2DTransformMatchesJoml() {
        Matrix3x2f matrix = new Matrix3x2f()
            .translate(13.5F, -4.25F)
            .rotate(0.78F)
            .scale(1.75F, 0.625F);
        Vector2f expected = matrix.transformPosition(2.25F, -7.5F, new Vector2f());

        assertEquals(expected.x(), FastVertexMath.transform2DX(matrix, 2.25F, -7.5F));
        assertEquals(expected.y(), FastVertexMath.transform2DY(matrix, 2.25F, -7.5F));
    }

    @Test
    void legacyColorDecodesLittleEndianVertexChannels() {
        int packedAbgr = 0x7F3366CC;

        assertEquals(
            0xBF66190C,
            FastVertexMath.legacyColor(packedAbgr, 0.5F, 1.0F, 0.5F, 0.5F, 0.75F, true)
        );
    }

    @Test
    void legacyColorCanIgnorePackedVertexColor() {
        assertEquals(
            0xFF7F3F1F,
            FastVertexMath.legacyColor(0, 0.5F, 1.0F, 0.5F, 0.25F, 1.0F, false)
        );
    }
}
