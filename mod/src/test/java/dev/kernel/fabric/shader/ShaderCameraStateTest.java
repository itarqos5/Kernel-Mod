package dev.kernel.fabric.shader;

import java.io.IOException;
import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaderCameraStateTest {
    @Test void splitCoordinatesPreserveNegativeAndWorldBorderPrecision() throws Exception {
        Random random = new Random(271828);
        var state = new ShaderCameraState();
        for (int sample = 0; sample < 16384; sample++) {
            double[] values = {random.nextDouble() * 60_000_000 - 30_000_000,
                random.nextDouble() * 8192 - 4096, random.nextDouble() * 60_000_000 - 30_000_000};
            state.resetHistory(); assertTrue(state.capture(values[0], values[1], values[2]));
            assertTrue(state.prepare(320, 240));
            int[] integral = state.integer("cameraPositionInt");
            float[] fraction = state.vector("cameraPositionFract");
            for (int axis = 0; axis < 3; axis++) {
                assertEquals((int) Math.floor(values[axis]), integral[axis]);
                assertTrue(fraction[axis] >= 0 && fraction[axis] < 1);
                assertEquals(values[axis], integral[axis] + (double) fraction[axis], 6e-8);
            }
            assertArrayEquals(integral, state.integer("previousCameraPositionInt"));
            assertArrayEquals(fraction, state.vector("previousCameraPositionFract"));
            assertArrayEquals(state.vector("cameraPosition"), state.vector("previousCameraPosition"));
            assertEquals((float) values[1], state.altitude());
            assertTrue(Math.abs(state.vector("cameraPosition")[0]) <= 30000);
            assertTrue(Math.abs(state.vector("cameraPosition")[2]) <= 30000);
        }
    }
    @Test void rebasingKeepsMotionAndOnlyCompletedFramesAdvanceHistory() throws Exception {
        for (int direction : new int[]{-1, 1}) {
            var state = new ShaderCameraState();
            assertThrows(IOException.class, () -> state.prepare(100, 50));
            state.capture(direction * 29999.5, -12.75, direction * 29999.75); state.prepare(100, 50); state.complete();
            state.capture(direction * 30000.5, -12.25, direction * 30000.25);
            assertFalse(state.prepare(100, 50));
            float[] now = state.vector("cameraPosition"), before = state.vector("previousCameraPosition");
            assertEquals(direction, now[0] - before[0]); assertEquals(.5f, now[1] - before[1]); assertEquals(direction * .5f, now[2] - before[2]);
            state.beginWorld(); // This frame was never completed.
            state.capture(direction * 30001.5, -12, direction * 30001.25); assertFalse(state.prepare(100, 50));
            assertEquals(direction * 2, now[0] - before[0]); state.complete();
            state.capture(direction * 40002, 120, 0); assertTrue(state.prepare(100, 50)); // Teleport.
            assertArrayEquals(now, before); state.complete();
            state.capture(direction * 40003, 120, 0); assertTrue(state.prepare(200, 50)); assertArrayEquals(now, before); state.complete();
            state.resetHistory(); state.capture(1, 2, 3); assertTrue(state.prepare(200, 50)); assertArrayEquals(now, before);
            for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, (double) Integer.MAX_VALUE + 1, (double) Integer.MIN_VALUE - 1}) {
                assertFalse(state.capture(invalid, 0, 0)); assertThrows(IOException.class, () -> state.prepare(200, 50));
                assertFalse(state.capture(0, invalid, 0)); assertFalse(state.capture(0, 0, invalid));
            }
            state.capture(-1e-12, -0.0, Integer.MAX_VALUE); state.prepare(200, 50);
            assertEquals(-1, state.integer("cameraPositionInt")[0]);
            assertTrue(state.vector("cameraPositionFract")[0] < 1);
        }
    }
    @Test void modelViewIsOwnedAndReconstructsWorldDirectionsWithProjection() throws Exception {
        Random random = new Random(314159);
        for (int mask = 1; mask < 8; mask++) {
            var state = new ShaderMatrixState(ShaderMatrixState.Kind.MODEL_VIEW, mask);
            Matrix4f last = null;
            for (int frame = 0; frame < 256; frame++) {
                Matrix4f view = new Matrix4f().rotateXYZ(random.nextFloat() * 6, random.nextFloat() * 6, random.nextFloat() * 6);
                Matrix4f copy = new Matrix4f(view);
                assertTrue(state.capture(view, false, false)); view.zero(); state.prepare(320, 240);
                if ((mask & 1) != 0) assertArrayEquals(copy.get(new float[16]), state.values("gbufferModelView"));
                if ((mask & 2) != 0) assertArrayEquals(copy.invert(new Matrix4f()).get(new float[16]), state.values("gbufferModelViewInverse"));
                if ((mask & 4) != 0) assertArrayEquals((last == null ? copy : last).get(new float[16]), state.values("gbufferPreviousModelView"));
                assertThrows(IllegalArgumentException.class, () -> state.values("gbufferProjection"));
                Matrix4f projection = new Matrix4f().perspective(1.2f, 1.5f, .05f, 512).translate(.02f, -.06f, 0).rotateZ(.03f);
                Matrix4f combined = projection.mul(copy, new Matrix4f());
                Matrix4f inverse = copy.invert(new Matrix4f()).mul(projection.invert(new Matrix4f()));
                for (int i = 0; i < 16; i++) {
                    var point = new Vector4f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, 1);
                    var clip = combined.transform(point, new Vector4f());
                    var restored = inverse.transform(clip, new Vector4f()); restored.div(restored.w);
                    assertEquals(point.x, restored.x, 1e-5); assertEquals(point.y, restored.y, 1e-5); assertEquals(point.z, restored.z, 1e-5);
                }
                state.complete(); last = copy;
                assertThrows(IOException.class, () -> state.prepare(320, 240));
                if (frame % 17 == 0) { state.discardHistory(); last = null; }
            }
        }
    }
}
