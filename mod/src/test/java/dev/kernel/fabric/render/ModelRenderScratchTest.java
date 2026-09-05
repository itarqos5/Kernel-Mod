package dev.kernel.fabric.render;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class ModelRenderScratchTest {
    @Test
    void rotationMatchesFreshJomlQuaternion() {
        Quaternionf expected = new Quaternionf().rotationZYX(0.72F, -1.14F, 0.38F);
        Quaternionf actual = ModelRenderScratch.get().rotationZYX(0.72F, -1.14F, 0.38F);

        assertEquals(expected.x(), actual.x());
        assertEquals(expected.y(), actual.y());
        assertEquals(expected.z(), actual.z());
        assertEquals(expected.w(), actual.w());
    }

    @Test
    void mutableValuesAreReusedWithinAThread() {
        ModelRenderScratch scratch = ModelRenderScratch.get();

        assertSame(scratch, ModelRenderScratch.get());
        assertSame(scratch.normal(), ModelRenderScratch.get().normal());
        assertSame(scratch.rotationZYX(0.1F, 0.2F, 0.3F), scratch.rotationZYX(0.4F, 0.5F, 0.6F));
    }

    @Test
    void normalMatrixCopiesTheLinearTransformAndReusesStorage() {
        ModelRenderScratch scratch = ModelRenderScratch.get();
        Matrix4f source = new Matrix4f().rotateXYZ(0.2F, -0.4F, 0.7F);
        Matrix3f expected = new Matrix3f(source);
        Matrix3f first = scratch.normalMatrix(source);

        assertEquals(expected, first);
        assertSame(first, scratch.normalMatrix(new Matrix4f().scale(2.0F)));
    }
}
