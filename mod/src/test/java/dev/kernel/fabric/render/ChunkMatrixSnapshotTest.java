package dev.kernel.fabric.render;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ChunkMatrixSnapshotTest {
    @Test void comparesAllRawComponentsAndPropertiesWithoutChangingEitherMatrix() {
        var random = new Random(934762L);
        for (int sample = 0; sample < 512; sample++) {
            float[] values = new float[16];
            for (int index = 0; index < values.length; index++) values[index] = Float.intBitsToFloat(random.nextInt());
            var source = new Matrix4f().set(values);
            var copy = new Matrix4f(source);
            assertTrue(ChunkMatrixSnapshot.matches(copy, source));
            for (int index = 0; index < 16; index++) {
                float saved = values[index];
                values[index] = Float.intBitsToFloat(Float.floatToRawIntBits(saved) ^ 1);
                source.set(values);
                assertFalse(ChunkMatrixSnapshot.matches(copy, source), "Changed component " + index);
                values[index] = saved; source.set(values);
            }
            assertTrue(ChunkMatrixSnapshot.matches(copy, source));
            source.assume(source.properties() ^ Matrix4f.PROPERTY_AFFINE);
            assertFalse(ChunkMatrixSnapshot.matches(copy, source));
        }
    }

    @Test void distinguishesSignedZeroNanPayloadsAndCustomMatrixOwnership() {
        var source = new Matrix4f().m01(-0.0f);
        var copy = new Matrix4f(source);
        source.m01(0.0f);
        assertFalse(ChunkMatrixSnapshot.matches(copy, source));
        source.m00(Float.intBitsToFloat(0x7fc00001)); copy.set(source);
        source.m00(Float.intBitsToFloat(0x7fc00002));
        assertFalse(ChunkMatrixSnapshot.matches(copy, source));
        assertFalse(ChunkMatrixSnapshot.matches(copy, new Matrix4f(source) {}));
        assertFalse(ChunkMatrixSnapshot.matches(null, source));
        assertFalse(ChunkMatrixSnapshot.matches(copy, null));
        assertTrue(ChunkMatrixSnapshot.canRetain(source, copy));
        assertFalse(ChunkMatrixSnapshot.canRetain(source, source));
        assertFalse(ChunkMatrixSnapshot.canRetain(source, new Matrix4f(source) {}));
    }

    @Test void changedSourcesUseNewSnapshotsAndLeavePreviouslyRetainedUniformsIntact() {
        var source = new Matrix4f().rotateXYZ(.2f,.3f,.4f);
        var first = new Matrix4f(source);
        var saved = new Matrix4f(first);
        source.translate(3,4,5);
        assertFalse(ChunkMatrixSnapshot.matches(first, source));
        var second = new Matrix4f(source);
        assertTrue(ChunkMatrixSnapshot.matches(first, saved));
        assertTrue(ChunkMatrixSnapshot.matches(second, source));
        assertNotSame(first, second);
    }

    //? if >=1.21.11 {
    @Test void sharedSnapshotsProduceTheExactNativeUniformBytesAndRemainStableAfterLaterBatches() {
        var source = new Matrix4f().rotateXYZ(.2f,.3f,.4f);
        Matrix4f snapshot = null;
        net.minecraft.client.renderer.DynamicUniforms.ChunkSectionInfo retained = null;
        byte[] saved = null;
        var expectedBuffer = java.nio.ByteBuffer.allocateDirect(128).order(java.nio.ByteOrder.nativeOrder());
        var actualBuffer = java.nio.ByteBuffer.allocateDirect(128).order(java.nio.ByteOrder.nativeOrder());
        var retainedBuffer = java.nio.ByteBuffer.allocateDirect(128).order(java.nio.ByteOrder.nativeOrder());
        byte[] expectedBytes = new byte[128], actualBytes = new byte[128], retainedBytes = new byte[128];
        for (int index = 0; index < 4096; index++) {
            if (index % 31 == 0) source.translate(1,-2,3);
            if (!ChunkMatrixSnapshot.matches(snapshot, source)) snapshot = new Matrix4f(source);
            var expected = new net.minecraft.client.renderer.DynamicUniforms.ChunkSectionInfo(new Matrix4f(source), index, -index, index * 16, index / 4096f, 2048, 1024);
            var actual = new net.minecraft.client.renderer.DynamicUniforms.ChunkSectionInfo(snapshot, index, -index, index * 16, index / 4096f, 2048, 1024);
            expectedBuffer.clear(); actualBuffer.clear();
            expected.write(expectedBuffer); actual.write(actualBuffer);
            expectedBuffer.get(0, expectedBytes); actualBuffer.get(0, actualBytes);
            assertArrayEquals(expectedBytes, actualBytes);
            if (retained == null) { retained = actual; saved = actualBytes.clone(); }
            retainedBuffer.clear(); retained.write(retainedBuffer); retainedBuffer.get(0, retainedBytes);
            assertArrayEquals(saved, retainedBytes);
        }
    }
    //? }
}
